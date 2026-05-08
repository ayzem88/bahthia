package com.bahthia.app.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.bahthia.importer.LibraryImporter
import com.bahthia.importer.pdf.PdfOcrRunner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.io.PrintWriter
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Collections
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * إدارة عمليّة الاستيراد بكلّ تفاصيلها:
 *   - اختيار ملفّات/مجلّدات (TXT/DOCX/PDF)
 *   - تشغيل OCR ثمّ الفهرسة (مع توازي قابل للضبط على PDFs)
 *   - تَتبّع الحالة بدقّة (عداد، حجم، ETA، أخطاء)
 *   - إلغاء/إيقاف مؤقّت/استئناف
 *   - حفظ التقدّم لاستئنافه بعد الانهيار
 *
 * كلّ الحالة Compose-State فالواجهة تَتجاوب فوراً.
 */
class ImportViewModel(
    private val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val progressStore: ImportProgressStore? = null,
    /** عدد عمليّات OCR المتوازية. يُقرأ من التفضيلات افتراضياً، قابل للحقن في الاختبارات. */
    private val parallelWorkersProvider: () -> Int = { 2 },
    /** عدد خُيوط استيراد TXT/DOCX المُتَوازية. يُقرأ من التَّفضيلات. */
    private val parallelTextWorkersProvider: () -> Int = {
        Runtime.getRuntime().availableProcessors().coerceIn(2, 8)
    },
) {
    private val logger = LoggerFactory.getLogger(ImportViewModel::class.java)

    /** الملفّات الفعليّة التي تُعالَج (للحفظ في progressStore). */
    private var currentFiles: List<Path> = emptyList()
    private var currentKind: String = ""

    // ─── حالة المهمّة ─────────────────────────────────────────
    /**
     * مَراحل المهمّة:
     *   - `IDLE`        — لا شيء جارٍ.
     *   - `PREPARING`   — فَحص أَوّليّ + تَحضير قائمة المَلفّات (على Dispatchers.IO).
     *   - `RUNNING`     — جارٍ القِراءة والفَهرسة الفِعليّة.
     *   - `PAUSED`      — مُتوقِّف مُؤقّتاً بَين المَلفّات.
     *   - `FINALIZING`  — انتهت الفَهرسة، يَجري الآن تَحسين الفهرس + احتساب الإحصاءات
     *                     لِيَكون كلّ شيء جاهزاً عند فَتح الواجِهة الرّئيسيّة.
     *   - `FINISHED`    — جاهِز تَماماً — يُمكن إِغلاق الحِوار وعَرض المَكتبة فَوراً.
     */
    enum class State { IDLE, PREPARING, RUNNING, PAUSED, FINALIZING, CANCELLED, FINISHED, ERROR }

    enum class Kind { TEXT, PDF }

    var state by mutableStateOf(State.IDLE)
        private set

    var kind by mutableStateOf<Kind?>(null)
        private set

    /**
     * الطابور — يَحتوي **عيِّنة** فَقط من المَلفّات (أَوّل ٥٠) لِأَغراض العَرض.
     * لا نُخزِّن ٤٥ ألف عُنصر في `mutableStateListOf` لِأنّ ذلك يُفجِّر الواجِهة.
     * الإِجمالي الفِعليّ في [totalFiles].
     */
    val queue = mutableStateListOf<QueueItem>()

    /** عَدد الملفّات الإِجماليّ — يَستعمله الواجِهة لِحساب التّقدُّم والـETA. */
    var totalFiles by mutableStateOf(0)
        private set

    /** اسم الملفّ الجاري معالجته (في حال التوازي: آخر ما بدأ). */
    var currentFile by mutableStateOf<String?>(null)
        private set

    /** المرحلة الحاليّة (للـ PDF: OCR/Indexing). */
    var stageMessage by mutableStateOf("")
        private set

    /** التقدّم 0..1 خلال الملفّ الحاليّ. */
    var inFileProgress by mutableStateOf(0f)
        private set

    /** عدد الملفّات المُنجَزة بنجاح. */
    var doneCount by mutableStateOf(0)
        private set

    /** عدد الملفّات الفاشلة. */
    var errorCount by mutableStateOf(0)
        private set

    /** صفحات مُفهرَسة منذ بداية المهمّة. */
    var pagesIndexed by mutableStateOf(0)
        private set

    /** زمن البداية (millis). */
    private var startMs: Long = 0

    /** الزمن المنقضي بالثواني. */
    var elapsedSec by mutableStateOf(0L)
        private set

    /** الزمن المتوقَّع لنهاية المهمّة بالثواني (-1 = غير معلوم). */
    var etaSec by mutableStateOf<Long>(-1)
        private set

    /** السرعة (ملف/دقيقة). */
    var filesPerMin by mutableStateOf(0.0)
        private set

    /**
     * السّجلّ التّفصيليّ — مَحدود في الذّاكِرة بِـ [LOG_VISIBLE_CAP] عُنصراً.
     * كلّ ما يَتجاوز ذلك يُكتب إلى ملفّ `import-log-<ts>.txt` في مُجلَّد بَيانات التّطبيق
     * (يُمكن لِلمُستخدِم فَتحه لِمُراجَعة المَلفّات الفاشِلة).
     */
    val log = mutableStateListOf<LogEntry>()

    /**
     * Aggregate المَكتبة المَحسوب فَور انتهاء الاستيراد (في طَور FINALIZING).
     * تَستعمله الواجِهة الرّئيسيّة بَعد إِغلاق حِوار الاستيراد لِعَرض الكتب فَوراً
     * بِلا قِراءة جَديدة من الفهرس.
     */
    var precomputedAggregate by mutableStateOf<com.bahthia.search.Aggregate?>(null)
        private set

    // ─── إشارات تَحكّم (cross-thread safe) ───
    private val cancelFlag = AtomicBoolean(false)
    private val pauseFlag = AtomicBoolean(false)

    // ─── عَدّادات ذَرّيّة لِتَجميع التَّحديثات بِدون قَفل ───
    /** يَزيدها أيّ خَيط بَعد كلّ ملفّ ناجح؛ الـticker يَنسخها إلى Compose state. */
    private val atDoneCount = AtomicInteger(0)
    private val atErrorCount = AtomicInteger(0)
    private val atPagesIndexed = AtomicLong(0)
    private val atCurrentFile = AtomicReference<String?>(null)
    private val atStageMessage = AtomicReference<String>("")
    /** نِسبة التَّقدُّم في الملفّ الحاليّ (٠..١) — تَستعمله أَحداث OCR. */
    private val atInFileProgress = AtomicReference<Float>(0f)

    /** Buffer ذَرّيّ لِسجلّ يَنتظر التَّمرير إلى Compose state — يُفرَّغ كلّ ١٠٠ms. */
    private val pendingLogBuffer = ConcurrentLinkedQueue<LogEntry>()

    /** كَاتب ملفّ السّجلّ الكامل (كلّ المَلفّات حتّى لو > ٢٠٠). */
    @Volatile private var logFileWriter: PrintWriter? = null
    @Volatile private var logFilePath: Path? = null

    /** زَمن آخر `saveProgress` لِتَطبيق ثَروتل ٥ ثَوان. */
    @Volatile private var lastSaveProgressMs: Long = 0L

    /** زَمن آخر تَحديث UI من الـticker. */
    @Volatile private var lastUiSyncMs: Long = 0L

    private var currentJob: Job? = null

    /**
     * كلّ runners النشطة في عمليّة OCR متوازية. عند الإلغاء نَستدعي
     * cancel() على كلّ واحد منها فيُقتل عمليّة Python خاصّته.
     */
    private val activeRunners: MutableSet<PdfOcrRunner> = Collections.synchronizedSet(mutableSetOf())

    /**
     * mutex لحماية تحديثات Compose-State من مُتعدّد threads (workers OCR).
     * Compose State آمن للقراءة لكنّ التَجميع (++) يَحتاج تَزامناً.
     */
    private val stateMutex = Mutex()

    // ─── واجهة عامّة ──────────────────────────────────────────

    /**
     * يَبدأ مَهمّة استيراد TXT/DOCX من قائمة مَلفّات.
     *
     * كلّ العَمل الثَّقيل (`preflightCheck` + `prepareQueue` + الفَهرسة + `aggregateAll`)
     * يَجري على `Dispatchers.IO`. الواجِهة لا تَتجمَّد أَبداً — تَحديثاتها تُجمَّع
     * في عَدّادات ذَرّيّة وتُنسَخ إلى Compose state عَبر ticker بِـ ١٠٠ms.
     */
    fun startTextImport(files: List<Path>, indexDir: Path) {
        if (state == State.RUNNING || state == State.PAUSED || state == State.PREPARING) return
        kind = Kind.TEXT
        currentKind = "TEXT"
        // تَهيئة فَوريّة لِلواجِهة (بِلا I/O)
        beginPrepareUiOnly(files)

        // كلّ الباقي على IO
        runJob {
            // ١) فَحص أَوّليّ + تَحضير قائمة المَلفّات (٤٥ ألف stat على القُرص)
            val pre = withContext(Dispatchers.IO) {
                preflightCheck(files, requiresApiKey = false, indexDir = indexDir)
            }
            if (pre.fatal) {
                pre.messages.forEach { enqueueLog(it) }
                state = State.ERROR
                return@runJob
            }
            withContext(Dispatchers.IO) { prepareQueueIo(files) }
            pre.messages.forEach { enqueueLog(it) }

            // ٢) الفَهرسة الفِعليّة — مُتَوازية على عِدّة خُيوط
            state = State.RUNNING
            val workers = parallelTextWorkersProvider().coerceIn(1, 16)
            enqueueLog(LogEntry("🚀", "بدء الفَهرسة — $workers خُيوط مُتَوازية"))
            withContext(Dispatchers.IO) {
                LibraryImporter(indexDir).use { importer ->
                    importer.importFilesParallel(
                        files = files,
                        workerCount = workers,
                        shouldCancel = { cancelFlag.get() },
                        onProgress = { current, _, _ ->
                            atCurrentFile.set(current.fileName.toString())
                        },
                        onFileDone = { result, _, _ ->
                            if (result.ok) {
                                val newDone = atDoneCount.incrementAndGet()
                                atPagesIndexed.addAndGet(result.pagesAdded.toLong())
                                writeLogFile("OK\t${result.file}\t${result.pagesAdded}p\t${result.bookTitle ?: ""}")
                                if (newDone <= LOG_VISIBLE_CAP / 2) {
                                    enqueueLog(LogEntry("✅", "${result.bookTitle ?: result.file.fileName}: ${result.pagesAdded} ص"))
                                }
                            } else {
                                atErrorCount.incrementAndGet()
                                writeLogFile("ERR\t${result.file}\t${result.error ?: ""}")
                                enqueueLog(LogEntry("❌", "${result.file.fileName}: ${result.error}", LogColor.ERROR))
                            }
                        },
                    )
                    // ٣) تَحسين الفهرس لِلقِراءة (دَمج مَقاطع — مَرّة واحِدة في النّهاية)
                    if (!cancelFlag.get()) {
                        atStageMessage.set("جارٍ تَحسين الفهرس…")
                        enqueueLog(LogEntry("⚙", "تَحسين الفهرس"))
                        importer.optimizeForRead()
                    }
                }
            }

            // ٤) احتساب إِحصاءات المَكتبة (كَي تَكون الواجِهة جاهِزة فَور الإغلاق)
            if (!cancelFlag.get()) {
                state = State.FINALIZING
                atStageMessage.set("جارٍ تَحضير المَكتبة لِلعَرض…")
                enqueueLog(LogEntry("📊", "احتساب الفئات والكتب"))
                val agg = withContext(Dispatchers.IO) {
                    runCatching {
                        com.bahthia.search.LibraryStats(indexDir).use { it.aggregateAll() }
                    }.getOrNull()
                }
                if (agg != null) {
                    precomputedAggregate = agg
                    enqueueLog(LogEntry("✓", "جاهز: ${agg.totalBooks} كتاباً، ${agg.totalPages} صفحة"))
                } else {
                    enqueueLog(LogEntry("⚠️", "تَعذَّر احتساب الإحصاءات — ستُحسَب في الواجِهة الرّئيسيّة", LogColor.WARNING))
                }
            }
        }
    }

    /**
     * يَبدأ مهمّة استيراد PDF (OCR ثمّ فهرسة) — مع توازٍ بين الملفّات.
     *
     * كلّ worker يَأخذ ملفّاً تالياً من الطابور المشترك (`AtomicInteger` عدّاد)،
     * يُنشئ [PdfOcrRunner] مستقلّاً (= عمليّة Python منفصلة)، ينتظر النهاية،
     * ثمّ يَكتب TXT الناتج للـ Lucene تحت قفل ([stateMutex]) لتَجنّب تَنازع.
     *
     * عند `cancel()` نَقتل كلّ Python processes النشطة دفعة واحدة.
     * `pause()` يَوقف الـ workers بين الملفّات (كلّ worker يَفحص العَلَم قبل أخذ ملفّ تالٍ).
     */
    fun startPdfImport(files: List<Path>, indexDir: Path, apiKey: String) {
        if (state == State.RUNNING || state == State.PAUSED || state == State.PREPARING) return
        if (apiKey.isBlank()) {
            enqueueLog(LogEntry("⚠️", "مفتاح Mistral API فارغ", LogColor.ERROR))
            state = State.ERROR
            return
        }
        kind = Kind.PDF
        currentKind = "PDF"
        beginPrepareUiOnly(files)

        val workers = parallelWorkersProvider().coerceIn(1, 4)

        runJob {
            // ١) فَحص أَوّليّ + تَحضير على IO
            val pre = withContext(Dispatchers.IO) {
                preflightCheck(files, requiresApiKey = true, indexDir = indexDir)
            }
            if (pre.fatal) {
                pre.messages.forEach { enqueueLog(it) }
                state = State.ERROR
                return@runJob
            }
            withContext(Dispatchers.IO) { prepareQueueIo(files) }
            pre.messages.forEach { enqueueLog(it) }
            if (workers > 1) {
                enqueueLog(LogEntry("⚡", "تَوازٍ مُفعَّل: $workers عمليّات OCR متزامنة"))
            }

            // ٢) الفَهرسة الفِعليّة
            state = State.RUNNING
            val cursor = AtomicInteger(0)
            val sharedImporter = LibraryImporter(indexDir)
            try {
                coroutineScope {
                    val jobs = (0 until workers).map { workerId ->
                        async(Dispatchers.IO) {
                            ocrWorkerLoop(
                                workerId = workerId,
                                files = files,
                                cursor = cursor,
                                apiKey = apiKey,
                                sharedImporter = sharedImporter,
                            )
                        }
                    }
                    jobs.awaitAll()
                }
                // ٣) تَحسين الفهرس لِلقِراءة
                if (!cancelFlag.get()) {
                    atStageMessage.set("جارٍ تَحسين الفهرس…")
                    enqueueLog(LogEntry("⚙", "تَحسين الفهرس"))
                    sharedImporter.optimizeForRead()
                }
            } finally {
                runCatching { sharedImporter.close() }
            }

            // ٤) احتساب إِحصاءات المَكتبة
            if (!cancelFlag.get()) {
                state = State.FINALIZING
                atStageMessage.set("جارٍ تَحضير المَكتبة لِلعَرض…")
                enqueueLog(LogEntry("📊", "احتساب الفئات والكتب"))
                val agg = withContext(Dispatchers.IO) {
                    runCatching {
                        com.bahthia.search.LibraryStats(indexDir).use { it.aggregateAll() }
                    }.getOrNull()
                }
                if (agg != null) {
                    precomputedAggregate = agg
                    enqueueLog(LogEntry("✓", "جاهز: ${agg.totalBooks} كتاباً، ${agg.totalPages} صفحة"))
                } else {
                    enqueueLog(LogEntry("⚠️", "تَعذَّر احتساب الإحصاءات — ستُحسَب في الواجِهة الرّئيسيّة", LogColor.WARNING))
                }
            }
        }
    }

    /**
     * حلقة عَمل worker واحد:
     *   - يَلتقط ملفّاً تالياً (`cursor.getAndIncrement`) حتّى ينتهي الطابور أو يُلغى
     *   - يَحترم pause بين الملفّات
     *   - يُنشئ [PdfOcrRunner] خاصّاً به ويُسجِّله في [activeRunners]
     *   - بعد نهاية ملفّ، يَفهرسه تحت قفل [stateMutex] (Lucene IndexWriter آمن للـ thread
     *     لكن نَحرص على نظامِ الإحصاءات + saveProgress)
     */
    private suspend fun ocrWorkerLoop(
        workerId: Int,
        files: List<Path>,
        cursor: AtomicInteger,
        apiKey: String,
        sharedImporter: LibraryImporter,
    ) {
        val runner = PdfOcrRunner(apiKey)
        activeRunners.add(runner)
        try {
            while (true) {
                if (cancelFlag.get()) break
                waitWhilePaused()
                if (cancelFlag.get()) break

                val idx = cursor.getAndIncrement()
                if (idx >= files.size) break

                val pdf = files[idx]
                updateCurrent(pdf.fileName.toString(), idx)
                enqueueLog(LogEntry("📕", "[#$workerId] بدء OCR: ${pdf.fileName}"))

                val produced = try {
                    runner.convert(pdf) { event -> handleOcrEvent(event) }
                } catch (e: Exception) {
                    logger.warn("OCR runner threw on {}: {}", pdf.fileName, e.message)
                    enqueueLog(LogEntry("❌", "${pdf.fileName}: ${e.message}", LogColor.ERROR))
                    emptyList()
                }

                if (cancelFlag.get()) break

                if (produced.isNotEmpty()) {
                    // الفَهرسة تَتمّ تحت قفل لتَجنّب تَنازع IndexWriter وحَدائق العدّ
                    stateMutex.withLock {
                        stageMessage = "فَهرسة النصّ في المكتبة..."
                        val res = sharedImporter.importFiles(
                            files = produced,
                            shouldCancel = { cancelFlag.get() },
                        )
                        recordTextResultsLocked(res)
                    }
                }
            }
        } finally {
            activeRunners.remove(runner)
        }
    }

    /** إلغاء فوريّ (يَوقف Python و يُكمل commit للمُنجَز). */
    fun cancel() {
        if (state == State.IDLE || state == State.FINISHED) return
        cancelFlag.set(true)
        pauseFlag.set(false)
        synchronized(activeRunners) {
            for (r in activeRunners) {
                runCatching { r.cancel() }
            }
        }
        enqueueLog(LogEntry("⏹", "طُلب الإلغاء — جارٍ الحفظ والإيقاف"))
    }

    /** إيقاف مؤقّت (بين الملفّات فقط). */
    fun pause() {
        if (state != State.RUNNING) return
        pauseFlag.set(true)
        state = State.PAUSED
        enqueueLog(LogEntry("⏸", "متوقّف مؤقّتاً"))
    }

    /** استئناف بعد إيقاف مؤقّت. */
    fun resume() {
        if (state != State.PAUSED) return
        pauseFlag.set(false)
        state = State.RUNNING
        enqueueLog(LogEntry("▶", "تَمّ الاستئناف"))
    }

    /** إعادة الحالة إلى IDLE — يُستدعى عند إغلاق الحوار. */
    fun reset() {
        if (state == State.RUNNING || state == State.PAUSED || state == State.FINALIZING) cancel()
        currentJob?.cancel()
        currentJob = null
        closeLogFile()
        state = State.IDLE
        kind = null
        queue.clear()
        log.clear()
        pendingLogBuffer.clear()
        atDoneCount.set(0)
        atErrorCount.set(0)
        atPagesIndexed.set(0)
        atCurrentFile.set(null)
        atStageMessage.set("")
        atInFileProgress.set(0f)
        precomputedAggregate = null
        currentFile = null
        stageMessage = ""
        inFileProgress = 0f
        doneCount = 0
        errorCount = 0
        pagesIndexed = 0
        totalFiles = 0
        elapsedSec = 0
        etaSec = -1
        filesPerMin = 0.0
        cancelFlag.set(false)
        pauseFlag.set(false)
    }

    // ─── داخلي ────────────────────────────────────────────────

    /**
     * تَهيئة فَوريّة لِحالة الواجِهة (بِلا I/O) — تُستدعى من الـmain thread
     * لِتَظهر الواجِهة بِشريط تَقدُّم خِلال أَجزاء من الثّانية.
     */
    private fun beginPrepareUiOnly(files: List<Path>) {
        cancelFlag.set(false)
        pauseFlag.set(false)
        log.clear()
        queue.clear()
        pendingLogBuffer.clear()
        atDoneCount.set(0)
        atErrorCount.set(0)
        atPagesIndexed.set(0)
        atCurrentFile.set(null)
        atStageMessage.set("جارٍ التَّحضير…")
        atInFileProgress.set(0f)
        precomputedAggregate = null
        doneCount = 0
        errorCount = 0
        pagesIndexed = 0
        currentFile = null
        stageMessage = "جارٍ التَّحضير…"
        inFileProgress = 0f
        totalFiles = files.size
        startMs = System.currentTimeMillis()
        elapsedSec = 0
        etaSec = -1
        filesPerMin = 0.0
        lastUiSyncMs = 0L
        lastSaveProgressMs = 0L
        state = State.PREPARING
        currentFiles = files
        openLogFile()
        enqueueLog(LogEntry("📚", "وُجد ${files.size} ملفّاً"))
    }

    /**
     * تَحضير قائمة المَلفّات على Dispatchers.IO فَقط (لا Compose state).
     *
     * نَحتفظ بِـ **عيِّنة** بِحَجم [QUEUE_SAMPLE_SIZE] لِلعَرض في الحِوار. لا نُخزِّن
     * كلّ المَلفّات في mutableStateListOf لِأنّ ذلك يُجمِّد الواجِهة عند ٤٥ ألف.
     */
    private fun prepareQueueIo(files: List<Path>) {
        val sample = files.take(QUEUE_SAMPLE_SIZE).map { f ->
            val size = runCatching { Files.size(f) }.getOrDefault(0L)
            QueueItem(f, size)
        }
        // التَّحديث الذَّرّيّ — Compose state آمن لِلكتابة من خَيط آخر
        queue.clear()
        queue.addAll(sample)
    }

    /**
     * يَحفظ لقطة تَقدّم — يُستدعى دَوريّاً ليَحدث استئناف بَعد الانهيار.
     * يَقرأ من العَدّادات الذَّرّيّة (Compose state يَتأخّر ١٠٠ms عن الفِعليّ).
     */
    private fun saveProgress() {
        val store = progressStore ?: return
        val done = atDoneCount.get()
        val err = atErrorCount.get()
        val processed = done + err
        val pending = currentFiles.drop(processed)
        if (pending.isEmpty()) {
            store.clear()
            return
        }
        store.save(
            ImportProgressStore.Snapshot(
                kind = currentKind,
                total = currentFiles.size,
                completed = done,
                timestamp = System.currentTimeMillis(),
                pending = pending,
            )
        )
    }

    private fun runJob(block: suspend () -> Unit) {
        currentJob?.cancel()
        currentJob = coroutineScope.launch {
            startTickerJob()
            try {
                block()
                // فَلش UI أَخير قَبل تَسجيل الحالة النّهائيّة
                flushUiNow()
                state = if (cancelFlag.get()) State.CANCELLED else State.FINISHED
                enqueueLog(
                    LogEntry(
                        if (cancelFlag.get()) "⏹" else "✅",
                        if (cancelFlag.get()) "أُلغي — ${doneCount} ملفّاً مُستورَد"
                        else "اكتمل — ${doneCount} ناجح، ${errorCount} فاشل",
                    )
                )
                flushUiNow()
                if (state == State.FINISHED) progressStore?.clear()
            } catch (e: Exception) {
                logger.error("Import failed", e)
                state = State.ERROR
                enqueueLog(LogEntry("❌", "خطأ: ${e.message}", LogColor.ERROR))
                flushUiNow()
            } finally {
                closeLogFile()
            }
        }
    }

    /**
     * Ticker واحِد يَعمل كلّ ١٠٠ms ويَنسخ كلّ العَدّادات الذّرّيّة إلى Compose state
     * **دَفعة واحِدة**. هذا يَستبدل ٩٠ ألف launch(Main) (واحِد لِكلّ ملفّ × ٢) بِـ
     * ~١٠ تَحديثات في الثّانية فَقط.
     */
    private fun startTickerJob() {
        coroutineScope.launch(Dispatchers.Main) {
            while (state != State.IDLE && state != State.FINISHED
                    && state != State.CANCELLED && state != State.ERROR) {
                syncUiFromAtomics()
                // saveProgress + log flush مَرّة كلّ ٥ ثَوان — كانا بَعد كلّ ملفّ
                val now = System.currentTimeMillis()
                if (now - lastSaveProgressMs > SAVE_PROGRESS_INTERVAL_MS) {
                    lastSaveProgressMs = now
                    saveProgress()
                    flushLogFile()
                }
                kotlinx.coroutines.delay(UI_TICK_MS)
            }
            // مَسحة أَخيرة بَعد انتهاء الـloop
            syncUiFromAtomics()
        }
    }

    /** يَنسخ العَدّادات الذَّرّيّة إلى Compose state + يَفرغ buffer السّجلّ. */
    private fun syncUiFromAtomics() {
        val newDone = atDoneCount.get()
        val newErr  = atErrorCount.get()
        val newPages = atPagesIndexed.get()
        if (newDone != doneCount) doneCount = newDone
        if (newErr != errorCount) errorCount = newErr
        if (newPages.toInt() != pagesIndexed) pagesIndexed = newPages.toInt()
        atCurrentFile.get()?.let { if (it != currentFile) currentFile = it }
        val stage = atStageMessage.get()
        if (stage.isNotEmpty() && stage != stageMessage) stageMessage = stage
        val newProgress = atInFileProgress.get()
        if (newProgress != inFileProgress) inFileProgress = newProgress

        elapsedSec = (System.currentTimeMillis() - startMs) / 1000
        if (newDone > 0 && elapsedSec > 0) {
            filesPerMin = newDone.toDouble() * 60.0 / elapsedSec
            val remaining = (totalFiles - newDone - newErr).coerceAtLeast(0)
            if (filesPerMin > 0) etaSec = (remaining * 60.0 / filesPerMin).toLong()
        }

        // فَرّغ pending log إلى Compose state — مَع سَقف العَرض
        var drained = 0
        while (drained < LOG_DRAIN_PER_TICK) {
            val entry = pendingLogBuffer.poll() ?: break
            if (log.size >= LOG_VISIBLE_CAP) {
                log.removeAt(0)  // نَزع الأَقدم لِلمُحافظة على السّقف
            }
            log.add(entry)
            drained++
        }
        lastUiSyncMs = System.currentTimeMillis()
    }

    /** فَلش فَوريّ — يَستعمله الـrunJob في النّهاية لِضَمان وُصول كلّ شيء قَبل State.FINISHED. */
    private suspend fun flushUiNow() {
        withContext(Dispatchers.Main) { syncUiFromAtomics() }
    }

    private suspend fun waitWhilePaused() {
        while (pauseFlag.get() && !cancelFlag.get()) {
            kotlinx.coroutines.delay(200)
        }
    }

    /** يُحدِّث الملفّ الحاليّ — تَستعمله workers مُتعدِّدة لِلـ PDF. كلّه ذَرّيّ. */
    private fun updateCurrent(name: String, index: Int) {
        atCurrentFile.set(name)
        atStageMessage.set("[${index + 1}/$totalFiles] $name")
    }

    /** يُعالج أَحداث OCR من PdfOcrRunner — كلّ التَّحديثات على atomic counters. */
    private fun handleOcrEvent(event: PdfOcrRunner.Event) {
        when (event) {
            is PdfOcrRunner.Event.BatchStart -> enqueueLog(LogEntry("📦", "بدء معالجة ${event.total} ملفّ"))
            is PdfOcrRunner.Event.FileStart -> { /* مُسجَّل أصلاً */ }
            is PdfOcrRunner.Event.Progress -> {
                atStageMessage.set(event.message)
                atInFileProgress.set(event.fraction.coerceIn(0f, 1f))
            }
            is PdfOcrRunner.Event.FileDone -> enqueueLog(
                LogEntry("🎯", "تَمّ OCR: ${java.io.File(event.file).name} → ${event.pages} ص في ${event.elapsed}ث")
            )
            is PdfOcrRunner.Event.FileError -> {
                atErrorCount.incrementAndGet()
                enqueueLog(LogEntry("❌", "${java.io.File(event.file).name}: ${event.error}", LogColor.ERROR))
            }
            is PdfOcrRunner.Event.Warning -> enqueueLog(LogEntry("⚠️", event.message, LogColor.WARNING))
            is PdfOcrRunner.Event.Summary -> enqueueLog(
                LogEntry("📊", "${event.ok} ناجح، ${event.fail} فاشل في ${event.elapsed}ث")
            )
            is PdfOcrRunner.Event.Fatal -> {
                atErrorCount.incrementAndGet()
                enqueueLog(LogEntry("🔥", "خطأ فادح: ${event.error}", LogColor.ERROR))
            }
        }
    }

    /**
     * يُسجِّل نَتائج استيراد TXT/DOCX من workers مُتعدِّدة — على atomic counters.
     * **لا withContext(Main)** — الـticker يَنسخ كلّ شيء.
     */
    private fun recordTextResultsLocked(results: List<LibraryImporter.ImportResult>) {
        for (r in results) {
            if (r.ok) {
                atDoneCount.incrementAndGet()
                atPagesIndexed.addAndGet(r.pagesAdded.toLong())
                writeLogFile("OK\t${r.file}\t${r.pagesAdded}p\t${r.bookTitle ?: ""}")
                if (atDoneCount.get() <= LOG_VISIBLE_CAP / 2) {
                    enqueueLog(LogEntry("✅", "${r.bookTitle ?: r.file.fileName}: ${r.pagesAdded} ص"))
                }
            } else {
                atErrorCount.incrementAndGet()
                writeLogFile("ERR\t${r.file}\t${r.error ?: ""}")
                enqueueLog(LogEntry("❌", "${r.file.fileName}: ${r.error}", LogColor.ERROR))
            }
        }
    }

    // ─── إِدارة السّجلّ — Buffer + ملفّ على القُرص ─────────────────

    /** يُضيف عُنصراً إلى buffer السّجلّ — الـticker يَنقله إلى Compose state. */
    private fun enqueueLog(entry: LogEntry) {
        pendingLogBuffer.offer(entry)
        // كَتابة كلّ شيء إلى ملفّ — حتّى الّذي لا يَظهر في الواجِهة
        writeLogFile("${entry.icon}\t${entry.text}")
    }

    /** يَفتح ملفّ السّجلّ في مُجلَّد بَيانات التّطبيق. */
    private fun openLogFile() {
        runCatching {
            val ts = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now())
            val dir = AppRuntime.dataDir.resolve("import-logs")
            Files.createDirectories(dir)
            val path = dir.resolve("import-$ts.txt")
            logFilePath = path
            logFileWriter = PrintWriter(
                Files.newBufferedWriter(
                    path,
                    Charsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                ),
                /* autoFlush = */ false,
            )
            logFileWriter?.println("# Bahthia Import Log — ${LocalDateTime.now()}")
        }
    }

    /** يَكتب سَطراً إلى ملفّ السّجلّ — لا تَحديث UI. آمن لِلـthreads. */
    private fun writeLogFile(line: String) {
        val w = logFileWriter ?: return
        synchronized(w) { w.println(line) }
    }

    /** يُجبر buffered writes إلى القُرص — يُستدعى دَوريّاً (كلّ ٥ث) وعند الإغلاق. */
    private fun flushLogFile() {
        val w = logFileWriter ?: return
        runCatching { synchronized(w) { w.flush() } }
    }

    /** يُغلق ملفّ السّجلّ — يُستدعى في النّهاية من runJob. */
    private fun closeLogFile() {
        val w = logFileWriter ?: return
        runCatching {
            synchronized(w) {
                w.flush()
                w.close()
            }
        }
        logFileWriter = null
    }

    /** المسار الكامِل لِملفّ السّجلّ — الواجِهة تَستعمله لِزرّ "افتح السّجلّ". */
    val logFile: Path? get() = logFilePath

    fun close() {
        reset()
        closeLogFile()
        coroutineScope.cancel()
    }

    // ─── فحص أوّليّ (Pre-flight) ──────────────────────────────

    private data class PreflightResult(
        val fatal: Boolean,
        val messages: List<LogEntry>,
    )

    /**
     * فحص أوّليّ قبل بدء الاستيراد:
     *   - جميع الملفّات قابلة للقراءة + لها حجم
     *   - مساحة كافية على القرص (تقدير: حجم الملفّات × ٢)
     *   - اكتشاف المكرّرات بمقارنة عناوين الكتب الموجودة بالاسم
     *   - تَنبيه على أحجام شاذّة (فارغ أو > 100 ميغا)
     */
    private fun preflightCheck(files: List<Path>, requiresApiKey: Boolean, indexDir: Path): PreflightResult {
        val msgs = mutableListOf<LogEntry>()
        var fatal = false

        if (files.isEmpty()) {
            return PreflightResult(true, listOf(LogEntry("⚠️", "لا توجد ملفّات لاستيرادها", LogColor.ERROR)))
        }

        // ١) قراءة + حجم — نَستعمل BasicFileAttributes (مَكالمة واحِدة بَدل اثنتَين)
        var totalBytes = 0L
        var unreadableCount = 0
        var emptyCount = 0
        var hugeCount = 0
        for (f in files) {
            try {
                val attrs = Files.readAttributes(f, java.nio.file.attribute.BasicFileAttributes::class.java)
                if (!attrs.isRegularFile) {
                    unreadableCount++
                    continue
                }
                val size = attrs.size()
                totalBytes += size
                if (size == 0L) emptyCount++
                if (size > 100L * 1024 * 1024) hugeCount++
            } catch (_: Exception) {
                unreadableCount++
            }
            // كلّ ٥٠٠ ملفّ — تَحديث رِسالة لِيَرى المُستخدِم تَقدُّم الفَحص
            if (totalFiles > 5000 && (totalBytes and 0xFFF) == 0L) {
                atStageMessage.set("جارٍ الفَحص الأَوّليّ…")
            }
        }
        if (unreadableCount > 0) {
            msgs += LogEntry("⚠️", "$unreadableCount ملفّاً غير قابل للقراءة — سيُتجاهل", LogColor.WARNING)
        }
        if (emptyCount > 0) {
            msgs += LogEntry("⚠️", "$emptyCount ملفّاً فارغاً", LogColor.WARNING)
        }
        if (hugeCount > 0) {
            msgs += LogEntry("⚠️", "$hugeCount ملفّاً ضخماً (> 100 ميغا) — قد يَستغرق وقتاً", LogColor.WARNING)
        }

        // ٢) المساحة على القرص
        val needBytes = totalBytes * 2
        val freeBytes = runCatching { Files.getFileStore(indexDir).usableSpace }.getOrDefault(Long.MAX_VALUE)
        if (freeBytes < needBytes) {
            msgs += LogEntry(
                "❌",
                "مساحة قرص غير كافية: متاح ${humanSize(freeBytes)}، مطلوب ~${humanSize(needBytes)}",
                LogColor.ERROR,
            )
            fatal = true
        } else {
            msgs += LogEntry("💾", "المساحة المتاحة: ${humanSize(freeBytes)}")
        }

        // ٣) اكتشاف المُكرّرات — نَتَخطّاها لِلاستيرادات الكَبيرة (> ٥٠٠٠ ملفّ)
        // تَحَقُّق المُكرّرات يَفتح Lucene ويَقرأ كلّ الكتب — مُكلِف. لِلاستيراد الضَّخم
        // الأَولى أن نَبدأ فَوراً ونَترك المُستخدِم يَكتشف بَعد الانتهاء.
        if (files.size <= MAX_FILES_FOR_DUPLICATE_CHECK) {
            val duplicates = detectDuplicateTitles(files, indexDir)
            if (duplicates.isNotEmpty()) {
                msgs += LogEntry(
                    "⚠️",
                    "اكتشاف: ${duplicates.size} ملفّاً قد يَكون مكرّراً (نفس العنوان موجود سلفاً)",
                    LogColor.WARNING,
                )
            }
        } else {
            msgs += LogEntry("ℹ", "تَخطّي فَحص المُكرّرات (أَكثر من ${MAX_FILES_FOR_DUPLICATE_CHECK} ملفّ)")
        }

        msgs += LogEntry("📦", "إجمالي حجم الملفّات: ${humanSize(totalBytes)}")
        msgs += LogEntry("✓", "الفحص الأوّليّ نجح — يَبدأ الاستيراد")

        return PreflightResult(fatal, msgs)
    }

    private fun detectDuplicateTitles(files: List<Path>, indexDir: Path): List<Path> {
        return try {
            com.bahthia.search.LibraryStats(indexDir).use { stats ->
                val existingTitles = stats.allBooks().mapNotNull { it.title }.map { it.lowercase().trim() }.toSet()
                files.filter { f ->
                    val baseName = f.fileName.toString().substringBeforeLast('.').lowercase().trim()
                    baseName in existingTitles
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun humanSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024L * 1024 -> "${bytes / 1024} KB"
        bytes < 1024L * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
        else -> String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024))
    }

    private companion object {
        /** فَترة تَحديث الواجِهة — كلّ ١٠٠ms (١٠ تَحديثات في الثّانية، أَكثر من كافٍ بَصَريّاً). */
        const val UI_TICK_MS = 100L

        /** كَم سَطراً سَجلّ نَنقل من الـbuffer إلى Compose state في كلّ tick. */
        const val LOG_DRAIN_PER_TICK = 50

        /** السّقف العامّ لِعَرض السّجلّ في الواجِهة — ما زاد يَذهب فَقط إلى ملفّ. */
        const val LOG_VISIBLE_CAP = 200

        /** عَدد المَلفّات الّتي تُحفظ كَعيِّنة في `queue` (لِلعَرض البَصَريّ فَقط). */
        const val QUEUE_SAMPLE_SIZE = 50

        /** كَم مَرّة نَكتب lockfile لِلتّقدّم على القُرص — كلّ ٥ ثَوان كَافية. */
        const val SAVE_PROGRESS_INTERVAL_MS = 5_000L

        /** عدد الملفّات الأَقصى الّذي نُجري فيه فَحص المُكرّرات — فَوقه نَتَخطّاه. */
        const val MAX_FILES_FOR_DUPLICATE_CHECK = 5_000
    }
}

// ─── أنواع مساعدة ──────────────────────────────────────────────

data class QueueItem(val path: Path, val sizeBytes: Long)

object LogColor {
    val ERROR = Color(0xFFD32F2F)
    val WARNING = Color(0xFFFFA726)
}

data class LogEntry(
    val icon: String,
    val text: String,
    val color: Color? = null,
)
