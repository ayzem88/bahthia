package com.bahthia.importer

import com.bahthia.domain.Page
import com.bahthia.search.indexer.BahthiaIndexer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.io.FileInputStream
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * مدير استيراد الكتب الكامل.
 *
 * يوحّد منطق:
 *   - قراءة الملف بترميز صحيح (TXT بترميزات متعدّدة، DOCX عبر POI)
 *   - استخراج الترويسات ([HeaderExtractor])
 *   - تقسيم الصفحات ([PageSplitter])
 *   - الفهرسة في Lucene عبر [BahthiaIndexer]
 */
class LibraryImporter(
    private val indexDir: Path,
) : Closeable {

    private val logger = LoggerFactory.getLogger(LibraryImporter::class.java)
    private val indexer = BahthiaIndexer(indexDir)
    private val nextBookId = AtomicLong(loadInitialBookId(indexDir))

    /** نتيجة استيراد ملف واحد. */
    data class ImportResult(
        val file: Path,
        val ok: Boolean,
        val bookTitle: String? = null,
        val pagesAdded: Int = 0,
        val error: String? = null,
    )

    /** يستورد ملفاً واحداً (TXT أو DOCX). */
    fun importFile(file: Path): ImportResult {
        val res = importFileWithoutCommit(file)
        // Commit مباشرةً عند استيراد ملفّ منفرد (الاستعمال خارج importDirectory).
        if (res.ok) {
            try { indexer.commit() } catch (_: Exception) { /* تُلتقط في الـ batch لاحقاً */ }
        }
        return res
    }

    /** نواة الاستيراد بدون commit — يَستعملها importDirectory لـ batching. */
    private fun importFileWithoutCommit(file: Path): ImportResult {
        return try {
            require(Files.isRegularFile(file)) { "Not a regular file: $file" }
            val ext = file.fileName.toString().substringAfterLast('.').lowercase()
            val content = when (ext) {
                "txt" -> readTxt(file)
                "docx" -> readDocx(file)
                else -> return ImportResult(file, ok = false, error = "نوع غير مدعوم: $ext")
            } ?: return ImportResult(file, ok = false, error = "فشل قراءة الملف")

            val meta = HeaderExtractor.extract(content, file.fileName.toString())
            val splits = PageSplitter.split(content)

            // الجنسيّة: نأخذ القيمة من ترويسة [الجنسيّة] أو [الجنسية].
            // إن لم تَرد الترويسة في الملف نَكتب "غير مصنّف".
            val nationality = meta.dynamicFields.entries.firstOrNull { (k, _) ->
                k in NATIONALITY_FIELD_LABELS
            }?.value?.takeIf { it.isNotBlank() } ?: DEFAULT_NATIONALITY

            val bookId = nextBookId.getAndIncrement()
            for ((idx, split) in splits.withIndex()) {
                indexer.addPage(
                    page = Page(
                        bookId = bookId,
                        pageNumber = idx + 1,
                        originalPageNumber = split.originalPageNumber,
                        content = split.content,
                    ),
                    bookTitle = meta.title,
                    author = meta.author,
                    category = meta.category,
                    year = meta.year,
                    deathYear = meta.deathYear,
                    country = nationality,
                )
            }
            logger.info("Imported '{}' ({} pages, book_id={})", meta.title, splits.size, bookId)
            ImportResult(file, ok = true, bookTitle = meta.title, pagesAdded = splits.size)
        } catch (e: Exception) {
            logger.error("Failed to import {}: {}", file, e.message)
            ImportResult(file, ok = false, error = e.message ?: "خطأ غير معروف")
        }
    }

    /**
     * يستورد كلّ الملفات من مجلد (recursively).
     *
     * يَجمع كلّ [BATCH_COMMIT_SIZE] كتاب في الذاكرة ثمّ يُجري commit واحداً، بدلاً من
     * commit بعد كلّ كتاب — هذا يَختصر I/O بشكل ملموس. آخر دفعة تَلحقها commit ضامن.
     *
     * @param shouldCancel  يُستدعى قبل كلّ ملفّ. لو رجع `true` نَوقف الاستيراد ونَعمل
     *                      commit للملفّات المُنجَزة. هذا يَدعم زرّ "إلغاء" في الواجهة.
     */
    fun importDirectory(
        directory: Path,
        shouldCancel: () -> Boolean = { false },
        onProgress: (current: Path, done: Int, total: Int) -> Unit = { _, _, _ -> },
    ): List<ImportResult> {
        require(Files.isDirectory(directory)) { "Not a directory: $directory" }
        val files = Files.walk(directory).use { stream ->
            stream
                .filter { Files.isRegularFile(it) }
                .filter {
                    val ext = it.fileName.toString().substringAfterLast('.').lowercase()
                    ext == "txt" || ext == "docx"
                }
                .sorted()
                .toList()
        }
        return importFiles(files, shouldCancel, onProgress)
    }

    /**
     * **استيراد مُتوازٍ** لِـ TXT/DOCX — يَستفيد من كلّ نَوى الـCPU.
     *
     * البِنية:
     *   - عَدّاد ذَرّيّ مُشترَك (`cursor`) يُوزِّع المَلفّات على [workerCount] خُيوط
     *   - كلّ خَيط يَقرأ ملفّاً، يُحلِّل، ويَستدعي `addPage` (Lucene Thread-safe)
     *   - خَيط `commit` دَوريّ مُنفصِل يَكتب لِلقُرص كلّ ٣٠ ثانية (لِدَعم الاستئناف)
     *   - commit نِهائيّ مَضمون عند الانتهاء أو الإلغاء
     *
     * الفائدة المُتوقَّعة لِـ ٤٥ ألف ملفّ TXT صَغير:
     *   - تَوازي ٤ → ٢-٣× أَسرع (الاختناق هو tokenization)
     *   - تَوازي ٨ → ٣-٤× أَسرع (إن كان CPU بِـ ٨+ نَوى)
     *
     * @param workerCount عَدد الخُيوط (الموصى ٤-٨)
     * @param shouldCancel فَحص قَبل كلّ ملفّ — إن `true` نَتوقّف ونَعمل final commit
     */
    suspend fun importFilesParallel(
        files: List<Path>,
        workerCount: Int,
        shouldCancel: () -> Boolean = { false },
        onProgress: (current: Path, done: Int, total: Int) -> Unit = { _, _, _ -> },
        onFileDone: (result: ImportResult, done: Int, total: Int) -> Unit = { _, _, _ -> },
    ): List<ImportResult> = coroutineScope {
        val total = files.size
        val cursor = AtomicInteger(0)
        val processed = AtomicInteger(0)
        val results = java.util.Collections.synchronizedList(mutableListOf<ImportResult>())
        val commitMutex = Mutex()

        // خَيط commit دَوريّ — يَكتب لِلقُرص كلّ ٣٠ث لِدَعم الاستئناف بَعد الانهيار
        val periodicCommit = launch(Dispatchers.IO) {
            while (isActive && !shouldCancel()) {
                delay(PERIODIC_COMMIT_INTERVAL_MS)
                if (!shouldCancel()) {
                    commitMutex.withLock {
                        runCatching {
                            indexer.commit()
                            persistMaxBookId()
                        }
                    }
                }
            }
        }

        try {
            // workers — كلّ واحِد يَأخذ ملفّاً تالياً من العَدّاد المُشترك
            val workers = (0 until workerCount).map {
                async(Dispatchers.IO) {
                    while (true) {
                        if (shouldCancel()) break
                        val idx = cursor.getAndIncrement()
                        if (idx >= files.size) break
                        val f = files[idx]
                        val r = importFileWithoutCommit(f)
                        results.add(r)
                        val done = processed.incrementAndGet()
                        // الـcallbacks يَجب أن تَكون thread-safe (في ImportViewModel: atomics)
                        onProgress(f, done, total)
                        onFileDone(r, done, total)
                    }
                }
            }
            workers.awaitAll()
        } finally {
            periodicCommit.cancel()
            // commit نِهائيّ — أَهمّ commit، يَضمن أنّ كلّ ما في RAM buffer وَصل لِلقُرص
            commitMutex.withLock {
                runCatching {
                    indexer.commit()
                    persistMaxBookId()
                }
            }
        }
        results.toList()
    }

    /**
     * يَستورد قائمة ملفّات صريحة (يُستعمل من ImportViewModel للقوائم المتعدّدة).
     * نفس آليّة التَجميع و الـ commit و دعم الإلغاء.
     */
    fun importFiles(
        files: List<Path>,
        shouldCancel: () -> Boolean = { false },
        onProgress: (current: Path, done: Int, total: Int) -> Unit = { _, _, _ -> },
        /**
         * يُستدعى بعد كلّ ملفّ مع نتيجته. يَسمح للواجهة بتحديث العدّاد فوراً بدل
         * انتظار كلّ الملفّات. اختياري — إن تُرك فارغاً نَرجع للسلوك القديم.
         */
        onFileDone: (result: ImportResult, done: Int, total: Int) -> Unit = { _, _, _ -> },
    ): List<ImportResult> {
        val total = files.size
        val results = mutableListOf<ImportResult>()
        var unflushed = 0
        for ((i, f) in files.withIndex()) {
            if (shouldCancel()) {
                logger.info("Import cancelled by user at {}/{} files.", i, total)
                break
            }
            val r = importFileWithoutCommit(f)
            results += r
            if (r.ok) unflushed++
            if (unflushed >= BATCH_COMMIT_SIZE) {
                try {
                    indexer.commit()
                    persistMaxBookId()
                } catch (e: Exception) {
                    logger.warn("Batch commit failed: {}", e.message)
                }
                unflushed = 0
            }
            onProgress(f, i + 1, total)
            onFileDone(r, i + 1, total)
        }
        // Commit نهائي لضمان حفظ ما تَبقّى من الدفعة الأخيرة.
        try {
            indexer.commit()
            persistMaxBookId()
        } catch (e: Exception) {
            logger.error("Final commit failed: {}", e.message)
        }
        return results
    }

    private fun readTxt(file: Path): String? {
        val encodings = listOf(
            Charsets.UTF_8,
            Charset.forName("UTF-8"),
            Charset.forName("windows-1256"),
            Charset.forName("ISO-8859-6"),
        )
        for (enc in encodings) {
            try {
                val text = FileInputStream(file.toFile()).use { stream ->
                    val bytes = stream.readAllBytes()
                    // استبعاد BOM إن وجد
                    val cleaned = if (bytes.size >= 3 && bytes[0] == 0xEF.toByte()
                                      && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
                        bytes.copyOfRange(3, bytes.size)
                    } else bytes
                    String(cleaned, enc)
                }
                if (looksValidArabic(text)) return text
            } catch (_: Exception) { /* try next */ }
        }
        return null
    }

    private fun readDocx(file: Path): String? {
        return try {
            FileInputStream(file.toFile()).use { stream ->
                XWPFDocument(stream).use { doc ->
                    doc.paragraphs.joinToString("\n") { it.text }
                }
            }
        } catch (e: Exception) {
            logger.warn("Failed DOCX read: {}", e.message)
            null
        }
    }

    /** تحقّق سريع: هل النصّ يحوي محارف عربيّة كافيّة؟ */
    private fun looksValidArabic(text: String): Boolean {
        if (text.isBlank()) return false
        val sample = text.take(2000)
        val arabicCount = sample.count { it.code in 0x0600..0x06FF }
        return arabicCount > 5 || sample.all { it.code in 0x20..0x7E || it.isWhitespace() }
    }

    /**
     * يحاول معرفة آخر `book_id` للاستئناف بعد عمليّات سابقة.
     *
     * **الاستراتيجيّة سَريعة**: نَقرأ من ملفّ `bookid.max` بِجوار الفهرس (نَكتبه بَعد كلّ
     * استيراد). الفَحص الكَلاسيكيّ (مَشي على كلّ المَستندات) لا يَتمّ إلّا كَنُسخة احتياط
     * لِلفهارس القَديمة الّتي لا تَملك الملفّ. حتّى في النّسخة الاحتياط، نُحدِّث الملفّ
     * فَوراً لِأَنّ المَرّة التّالية تَكون فَوريّة.
     */
    private fun loadInitialBookId(indexDir: Path): Long {
        // ١) المسار السّريع: ملفّ bookid.max
        val maxFile = indexDir.resolve(BOOK_ID_MAX_FILE)
        runCatching {
            if (Files.exists(maxFile)) {
                val v = Files.readString(maxFile, Charsets.UTF_8).trim().toLongOrNull()
                if (v != null && v >= 0) return v + 1
            }
        }

        // ٢) النَّسخة الاحتياطيّة: مَسح الفهرس مَرّة واحِدة (بَطيء لِلمَكتبات الكَبيرة)
        val scanned = try {
            org.apache.lucene.store.FSDirectory.open(indexDir).use { dir ->
                if (!org.apache.lucene.index.DirectoryReader.indexExists(dir)) return@use 0L
                org.apache.lucene.index.DirectoryReader.open(dir).use { reader ->
                    var maxId = 0L
                    val storedFields = reader.storedFields()
                    for (i in 0 until reader.maxDoc()) {
                        val doc = storedFields.document(i, setOf("book_id")) ?: continue
                        val id = doc.get("book_id")?.toLongOrNull() ?: continue
                        if (id > maxId) maxId = id
                    }
                    maxId
                }
            }
        } catch (_: Exception) { 0L }

        // ٣) اِكتب الملفّ فَوراً كَي تَكون المَرّة القادمة فَوريّة
        runCatching {
            Files.createDirectories(indexDir)
            Files.writeString(maxFile, scanned.toString(), Charsets.UTF_8)
        }
        return scanned + 1
    }

    /** يُحدِّث ملفّ `bookid.max` بِالقيمة الحاليّة. يُستدعى بَعد كلّ commit. */
    private fun persistMaxBookId() {
        val current = nextBookId.get() - 1
        if (current <= 0) return
        runCatching {
            val maxFile = indexDir.resolve(BOOK_ID_MAX_FILE)
            Files.writeString(maxFile, current.toString(), Charsets.UTF_8)
        }
    }

    override fun close() {
        runCatching { persistMaxBookId() }
        indexer.close()
    }

    /**
     * يُحسِّن الفهرس لِلقِراءة بَعد الانتهاء من استيراد جَماعيّ ضَخم.
     * يُستدعى مَرّة واحِدة في نِهاية الجَلسة (لا بَعد كلّ ملفّ).
     */
    fun optimizeForRead() {
        runCatching { indexer.optimizeForRead() }
    }

    private companion object {
        /**
         * الترويسة الوحيدة المقبولة لتحديد الجنسيّة.
         * نَقبل صيغتَي الإملاء — بالشدّة وبدونها — لا غير.
         */
        val NATIONALITY_FIELD_LABELS = setOf(
            "الجنسيّة",
            "الجنسية",
        )

        /** القيمة المُسجَّلة لمن لم تَرد له ترويسة الجنسيّة. */
        const val DEFAULT_NATIONALITY = "غير مصنّف"

        /**
         * عَدد الكتب الّتي تُستورَد قَبل إِجراء commit واحد لِلقُرص.
         * كَان ١٠ — رَفعناه إلى ١٠٠٠ لِيَتّسق مع RAM buffer = ٥١٢ ميغا.
         * النّتيجة: في استيراد ٤٥ ألف ملفّ نَعمل ~٤٥ commit بَدل ٤٥٠٠.
         */
        const val BATCH_COMMIT_SIZE = 1000

        /** اسم ملفّ تَخزين أَكبر `book_id` بِجوار الفهرس — لِتَجنّب مَسح الفهرس عند البَدء. */
        const val BOOK_ID_MAX_FILE = "bookid.max"

        /** فَترة الـcommit الدَّوريّ في الاستيراد المُتَوازي — كلّ ٣٠ ثانية. */
        const val PERIODIC_COMMIT_INTERVAL_MS = 30_000L
    }
}
