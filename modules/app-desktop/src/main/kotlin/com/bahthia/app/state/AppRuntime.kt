package com.bahthia.app.state

import com.bahthia.domain.AppMetadata
import com.bahthia.domain.Page
import com.bahthia.lifecycle.AutoBackupScheduler
import com.bahthia.lifecycle.AutoUpdater
import com.bahthia.lifecycle.BackupManager
import com.bahthia.lifecycle.CrashReporter
import com.bahthia.lifecycle.TelemetryService
import com.bahthia.search.BahthiaSearcher
import com.bahthia.search.indexer.BahthiaIndexer
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * تهيئة بيئة تشغيل التطبيق.
 *
 * - يُحدِّد مجلد البيانات (`%APPDATA%/Bahthia` على Windows، `~/.bahthia` على غيره)
 * - يُنشئ فهرس Lucene إن لم يكن موجوداً، ويُملأه بكتب تجريبيّة
 * - يفتح [BahthiaSearcher] جاهزاً للاستعمال
 *
 * في المرحلة ١ (هجرة البيانات) ستستبدل الكتب التجريبية ببيانات المستخدم
 * من قواعد Python القديمة عبر `MigrationTool`.
 */
object AppRuntime {

    private val logger = LoggerFactory.getLogger(AppRuntime::class.java)

    /** مسار مجلد بيانات التطبيق. */
    val dataDir: Path = run {
        val appDataEnv = System.getenv("APPDATA") // Windows
        val home = System.getProperty("user.home")
        val base = if (!appDataEnv.isNullOrBlank()) {
            Paths.get(appDataEnv, "Bahthia")
        } else {
            Paths.get(home, ".bahthia")
        }
        Files.createDirectories(base)
        base
    }

    /** مسار فهرس Lucene. */
    val indexDir: Path = dataDir.resolve("lucene-index")

    /**
     * يُنشئ الفهرس إن لم يكن موجوداً (أو غير مُكتمل) ويملؤه بكتب تجريبيّة.
     *
     * **فَحص السّلامة عميق**: لا نَكتفي بِـ `DirectoryReader.indexExists()` (الذي يَفحص
     * وُجود `segments_N` فَقط)، بَل نُحاول فِعليّاً فَتح `DirectoryReader.open()` —
     * هذا يَكشف تَلف المَقاطع (مِثل `file mismatch, expected id=...`) الّذي يَمرّ
     * من الفَحص السّطحيّ ثُمّ يَنفجر عند أوّل قِراءة.
     *
     * إن فَشل الفَحص العَميق، نَحذف كلّ مُحتويات `lucene-index/` ونَبني فهرساً
     * جَديداً بِكتب تَجريبيّة بَدل تَعطيل البَرنامج عند الإقلاع.
     */
    fun ensureIndexInitialized() {
        Files.createDirectories(indexDir)

        // ١) فَحص خَفيف: هل يوجد ملفّ segments_N أصلاً؟
        val hasSegments = try {
            org.apache.lucene.store.FSDirectory.open(indexDir).use { dir ->
                org.apache.lucene.index.DirectoryReader.indexExists(dir)
            }
        } catch (_: Throwable) { false }

        // ٢) فَحص عَميق: لو يَدّعي أنّه موجود، نُحاول فَتح reader فِعلاً
        val isHealthy = hasSegments && try {
            org.apache.lucene.store.FSDirectory.open(indexDir).use { dir ->
                org.apache.lucene.index.DirectoryReader.open(dir).use { /* فَتح كافٍ */ }
            }
            true
        } catch (e: Throwable) {
            logger.warn("Lucene index at {} failed deep open-check: {} — will rebuild",
                indexDir, e.message)
            false
        }

        if (isHealthy) {
            logger.info("Lucene index already exists and is healthy at {}", indexDir)
            return
        }

        // ٣) إِفراغ المُجلَّد بِالكامل (شامل أيّ ملفّات leftover)
        if (Files.list(indexDir).use { it.findAny().isPresent }) {
            logger.warn("Wiping corrupt/incomplete index directory at {}", indexDir)
            Files.list(indexDir).use { stream ->
                stream.forEach { f -> runCatching { Files.deleteIfExists(f) } }
            }
        }

        // ٤) محاولة استيراد كتب العينة المدمجة، وإلا الرجوع للكتب التجريبية الإنلاين
        val bundledBooks = locateBundledBooksDir()
        if (bundledBooks != null && hasTextFiles(bundledBooks)) {
            logger.info("Importing bundled sample books from {}...", bundledBooks)
            try {
                com.bahthia.importer.LibraryImporter(indexDir).use { imp ->
                    val results = imp.importDirectory(bundledBooks)
                    val imported = results.count { it.ok }
                    logger.info("Bundled import done: {} books indexed", imported)
                }
                preferences.bundleImported = true
                return
            } catch (e: Exception) {
                logger.warn("Bundled import failed ({}). Falling back to inline samples.", e.message)
            }
        } else {
            logger.info("No bundled books folder found — using inline samples for dev/test.")
        }

        // Fallback: كتب تجريبية إنلاين (تستعمل في dev بدون bundling أو لو فشل الاستيراد)
        logger.info("Creating initial Lucene index at {} with inline sample books...", indexDir)
        BahthiaIndexer(indexDir).use { idx ->
            sampleBooks().forEach { (page, meta) ->
                idx.addPage(
                    page = page,
                    bookTitle = meta.bookTitle,
                    author = meta.author,
                    category = meta.category,
                    year = meta.year,
                    deathYear = meta.deathYear,
                    country = meta.country,
                )
            }
            idx.commit()
        }
        logger.info("Initial Lucene index ready.")
    }

    /**
     * يحدد موقع مجلد كتب العينة (`Books/`) داخل مصادر التطبيق.
     *
     * - في النسخة المغلفة (MSI): يقرأ من `compose.application.resources.dir`
     * - في وضع التطوير (gradlew run): يقرأ من نفس المسار (Compose Desktop يضبطه تلقائيا)
     * - يرجع `null` إذا لم يتوفر المسار (مثلا في الاختبارات).
     */
    private fun locateBundledBooksDir(): Path? {
        val resourcesDir = System.getProperty("compose.application.resources.dir")
            ?: return null
        val booksDir = Paths.get(resourcesDir, "Books")
        return if (Files.isDirectory(booksDir)) booksDir else null
    }

    /** يفحص إن كان المجلد يحوي ملف TXT واحدا على الأقل. */
    private fun hasTextFiles(dir: Path): Boolean = try {
        Files.list(dir).use { stream ->
            stream.anyMatch { it.fileName.toString().endsWith(".txt", ignoreCase = true) }
        }
    } catch (_: Exception) { false }

    fun openSearcher(): BahthiaSearcher = BahthiaSearcher(indexDir)

    /** يفتح [com.bahthia.search.LibraryStats] لقراءة الفئات والكتب الحقيقيّة. */
    fun openLibraryStats(): com.bahthia.search.LibraryStats =
        com.bahthia.search.LibraryStats(indexDir)

    /** التفضيلات المستمرّة على القرص. */
    val preferences: com.bahthia.lifecycle.UserPreferences by lazy {
        com.bahthia.lifecycle.UserPreferences(dataDir)
    }

    /** مَخزن جَلسات البَحث المحفوظة (المفضّلة) — حدّ ٥٠ جَلسة. */
    val savedSearchStore: SavedSearchStore by lazy {
        SavedSearchStore(dataDir)
    }

    /** مَخزن تَقدّم الاستيراد — يَدعم استئناف بعد انهيار. */
    val importProgressStore: ImportProgressStore by lazy {
        ImportProgressStore(dataDir)
    }

    /**
     * ViewModel وحيد لعمليّة الاستيراد طول حياة التطبيق.
     *
     * ملكيّة الـ ViewModel هنا (لا في [com.bahthia.app.ui.screens.import.ImportDialog])
     * تَسمح بِبَقاء الاستيراد جارياً عند إخفاء الحوار، وعَرض شريط حالة في الواجهة
     * الرئيسيّة، ثمّ إعادة فتح الحوار بنفس الحالة.
     *
     * عَدد الـ workers المتوازية لـ OCR يُقرأ من [preferences.parallelOcrWorkers].
     * يَجب استدعاء [ImportViewModel.close] عند إنهاء التطبيق (يَفعل من Main.kt).
     */
    val importVM: ImportViewModel by lazy {
        ImportViewModel(
            progressStore = importProgressStore,
            parallelWorkersProvider = { preferences.parallelOcrWorkers },
            parallelTextWorkersProvider = { preferences.parallelTextWorkers },
        )
    }

    // ─────────────────────────────────────────────────────────────
    // المرحلة الرابعة — خدمات دورة الحياة
    // ─────────────────────────────────────────────────────────────

    /** مُبلِّغ الأعطال — يكتب الاستثناءات إلى `crashes/<timestamp>.log`. */
    val crashReporter: CrashReporter by lazy {
        CrashReporter(
            crashesDir = dataDir.resolve("crashes"),
            appVersion = AppMetadata.VERSION,
        )
    }

    /** مدير النسخ الاحتياطي — تصدير/استعادة فهرس Lucene + التفضيلات. */
    val backupManager: BackupManager by lazy { BackupManager(dataDir) }

    /** مُتحقّق من التحديثات — يستفسر `bahthia.com/api/version.json`. */
    val autoUpdater: AutoUpdater by lazy {
        AutoUpdater(
            feedUrl = AppMetadata.UPDATE_FEED,
            currentVersion = AppMetadata.VERSION,
        )
    }

    /** خدمة تيليمتري محلّيّة — مُحترَمة لـ `preferences.telemetryEnabled`. */
    val telemetry: TelemetryService by lazy {
        TelemetryService(
            storeFile = dataDir.resolve("telemetry.json"),
            appVersion = AppMetadata.VERSION,
            isEnabled = { preferences.telemetryEnabled },
        )
    }

    /** مُجدوِل النسخ الاحتياطي الأسبوعيّ. */
    val autoBackupScheduler: AutoBackupScheduler by lazy {
        AutoBackupScheduler(preferences, backupManager)
    }

    /** يُهيِّئ خدمات دورة الحياة عند بدء التطبيق. */
    fun installLifecycleServices() {
        crashReporter.install()
        // فلَش التيليمتري عند الإغلاق
        Runtime.getRuntime().addShutdownHook(Thread {
            try { telemetry.flush() } catch (_: Exception) { /* ignore */ }
        })
        // نسخة احتياطيّة أسبوعيّة (لو مفعَّلة) — في الخلفيّة، لا تَعطَل البدء
        Thread {
            try {
                val res = autoBackupScheduler.runIfDue()
                if (res is AutoBackupScheduler.Result.Created) {
                    logger.info("Weekly auto-backup ran: {}", res.file)
                }
            } catch (e: Exception) {
                logger.warn("Auto-backup check failed: {}", e.message)
            }
        }.apply { isDaemon = true; name = "auto-backup-check" }.start()
        logger.info("Lifecycle services installed.")
    }

    /**
     * يحذف كتاباً واحداً من الفهرس بمعرّفه.
     * المتّصل مسؤول عن إغلاق الـ searcher قبل النداء وإعادة فتحه بعده.
     */
    fun deleteBook(bookId: Long) {
        BahthiaIndexer(indexDir).use { idx ->
            idx.deleteBook(bookId)
            idx.commit()
        }
        logger.info("Book {} deleted from library", bookId)
    }

    /**
     * يمسح كل البيانات (الفهرس) من المكتبة.
     * يُستعمل قبل استيراد بيانات جديدة، أو بطلب من المستخدم.
     */
    fun clearLibrary() {
        if (!Files.exists(indexDir)) return
        Files.walk(indexDir).use { stream ->
            stream
                .sorted(Comparator.reverseOrder())
                .forEach { Files.deleteIfExists(it) }
        }
        Files.createDirectories(indexDir)
        // إنشاء فهرس فارغ صالح للقراءة
        com.bahthia.search.indexer.BahthiaIndexer(indexDir).use { it.commit() }
        logger.info("Library cleared at {}", indexDir)
    }

    private data class Meta(
        val bookTitle: String,
        val author: String? = null,
        val category: String? = null,
        val year: String? = null,
        val deathYear: String? = null,
        val country: String? = null,
    )

    /** كتب تجريبيّة بسيطة للحصول على مكتبة قابلة للبحث فور التثبيت. */
    private fun sampleBooks(): List<Pair<Page, Meta>> = listOf(
        Page(1, 1, content = "بسم الله الرحمن الرحيم. الحمد لله رب العالمين الرحمن الرحيم مالك يوم الدين")
            to Meta("الفاتحة", category = "القرآن", year = "0001"),
        Page(1, 2, content = "إياك نعبد وإياك نستعين اهدنا الصراط المستقيم صراط الذين أنعمت عليهم")
            to Meta("الفاتحة", category = "القرآن", year = "0001"),
        Page(2, 1, content = "كتاب الصلاة. باب وقت الفجر إذا طلع الفجر صلِّ ركعتين قبل صلاة الفجر")
            to Meta("صحيح البخاري", author = "البخاري", category = "الحديث", year = "0256",
                   deathYear = "0256", country = "بخارى"),
        Page(2, 2, content = "كتاب الزكاة. من ملك نصاباً وحال عليه الحول وجبت عليه الزكاة من ماله")
            to Meta("صحيح البخاري", author = "البخاري", category = "الحديث", year = "0256",
                   deathYear = "0256", country = "بخارى"),
        Page(3, 1, content = "العلم نور والجهل ظلام. اطلبوا العلم من المهد إلى اللحد. العلماء ورثة الأنبياء")
            to Meta("جامع بيان العلم", author = "ابن عبد البر", category = "العلم", year = "0463",
                   deathYear = "0463", country = "الأندلس"),
        Page(4, 1, content = "كاتب يكتب كتاباً مكتوباً مكتبةً كتابةً. كتب الناس فقام كاتبٌ منهم")
            to Meta("نماذج لغوية", category = "اللغة", year = "0500", country = "العراق"),
        Page(5, 1, content = "ضرب الرجل ضرباً شديداً وهو مضروب ضارب. الضرب أنواع كثيرة في علم الصرف")
            to Meta("شذا العرف", author = "الحملاوي", category = "اللغة", year = "1316",
                   deathYear = "1351", country = "مصر"),
        Page(6, 1, content = "قال رسول الله صلى الله عليه وسلم: إنما الأعمال بالنيات. وإنما لكل امرئ ما نوى")
            to Meta("الأربعون النووية", author = "النووي", category = "الحديث", year = "0676",
                   deathYear = "0676", country = "الشام"),
    )
}
