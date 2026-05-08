package com.bahthia.lifecycle

import com.bahthia.domain.TimeMode
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

/**
 * تخزين مستمرّ للتفضيلات في ملف `preferences.properties`
 * بجانب فهرس المكتبة (في `%APPDATA%/Bahthia/`).
 *
 * يدعم القراءة والكتابة بأمان للمفاتيح التالية:
 *   - mistral_api_key — مفتاح Mistral OCR
 *   - theme           — اسم السمة (earthy/dark/gray/auto)
 *   - font_size       — S/M/L/XL
 *   - font_family     — اسم الخطّ
 *   - telemetry_enabled
 *   - results_limit
 */
class UserPreferences(private val baseDir: Path) {

    private val logger = LoggerFactory.getLogger(UserPreferences::class.java)
    private val file: Path = baseDir.resolve("preferences.properties")
    private val props = Properties()

    init {
        load()
    }

    fun load() {
        if (Files.exists(file)) {
            try {
                Files.newInputStream(file).use { props.load(it) }
            } catch (e: Exception) {
                logger.warn("Failed to load preferences: {}", e.message)
            }
        }
    }

    fun save() {
        try {
            Files.createDirectories(baseDir)
            Files.newOutputStream(file).use { out ->
                props.store(out, "Bahthia Library user preferences")
            }
        } catch (e: Exception) {
            logger.warn("Failed to save preferences: {}", e.message)
        }
    }

    // ─── Getters / Setters محدّدة المفتاح ───

    var mistralApiKey: String
        get() = props.getProperty("mistral_api_key", "")
        set(value) {
            props.setProperty("mistral_api_key", value)
            save()
        }

    var theme: String
        get() = props.getProperty("theme", "earthy")
        set(value) {
            props.setProperty("theme", value)
            save()
        }

    var fontSize: String
        get() = props.getProperty("font_size", "M")
        set(value) {
            props.setProperty("font_size", value)
            save()
        }

    var fontFamily: String
        get() = props.getProperty("font_family", "Sakkal Majalla")
        set(value) {
            props.setProperty("font_family", value)
            save()
        }

    var telemetryEnabled: Boolean
        get() = props.getProperty("telemetry_enabled", "false").toBoolean()
        set(value) {
            props.setProperty("telemetry_enabled", value.toString())
            save()
        }

    var resultsLimit: Int
        get() = props.getProperty("results_limit", "1000").toIntOrNull() ?: 1000
        set(value) {
            props.setProperty("results_limit", value.toString())
            save()
        }

    /**
     * هل استورد البرنامج عينة الكتب المدمجة في حزمة MSI أول مرة؟
     * يصبح `true` بعد أول استيراد ناجح، فلا يعيد المحاولة لو مسح المستخدم المكتبة لاحقا.
     */
    var bundleImported: Boolean
        get() = props.getProperty("bundle_imported", "false").toBoolean()
        set(value) {
            props.setProperty("bundle_imported", value.toString())
            save()
        }

    /**
     * مَعيار البَحث الزَّمني — يَتحكَّم في عَمود "الحَقل الزَّمني" وفَلتَرة البَحث بالسَّنة.
     * الافتراضي: سَنة الوَفاة (مُلائم لِمَكتبة تُراثيّة).
     */
    var timeMode: TimeMode
        get() = when (props.getProperty("time_mode", "death_year")) {
            "usage_date" -> TimeMode.USAGE_DATE
            else         -> TimeMode.DEATH_YEAR
        }
        set(value) {
            val raw = when (value) {
                TimeMode.DEATH_YEAR -> "death_year"
                TimeMode.USAGE_DATE -> "usage_date"
            }
            props.setProperty("time_mode", raw)
            save()
        }

    /** آخر مجلَّد استورد المستخدم منه TXT/DOCX (يُفتَح المُستعرض هنا في المرّة القادمة). */
    var lastImportFolderTxt: String
        get() = props.getProperty("last_import_folder_txt", "")
        set(value) {
            props.setProperty("last_import_folder_txt", value)
            save()
        }

    /** آخر مجلَّد استورد المستخدم منه PDFs. */
    var lastImportFolderPdf: String
        get() = props.getProperty("last_import_folder_pdf", "")
        set(value) {
            props.setProperty("last_import_folder_pdf", value)
            save()
        }

    /** يُفعِّل النسخ الاحتياطيّ الأسبوعيّ التلقائيّ. */
    var autoBackupEnabled: Boolean
        get() = props.getProperty("auto_backup_enabled", "false").toBoolean()
        set(value) {
            props.setProperty("auto_backup_enabled", value.toString())
            save()
        }

    /** زمن آخر نسخة احتياطيّة (Unix epoch بالثواني). صفر إن لم تُنشَأ نسخة بعد. */
    var lastBackupAtEpoch: Long
        get() = props.getProperty("last_backup_at_epoch", "0").toLongOrNull() ?: 0L
        set(value) {
            props.setProperty("last_backup_at_epoch", value.toString())
            save()
        }

    /**
     * عدد عمليّات OCR المتوازية عند استيراد PDFs.
     * القيمة الافتراضيّة ٢ — نطاق آمِن: ١-٤. أكثر من ذلك يُتعب الشبكة وقد يَفشل API.
     */
    var parallelOcrWorkers: Int
        get() = (props.getProperty("parallel_ocr_workers", "2").toIntOrNull() ?: 2).coerceIn(1, 4)
        set(value) {
            props.setProperty("parallel_ocr_workers", value.coerceIn(1, 4).toString())
            save()
        }

    /**
     * عَدد الخُيوط المُتَوازية لِاستيراد TXT/DOCX (قِراءة + تَوكِنة + إِضافة لِـLucene).
     *
     * Lucene's IndexWriter آمِن لِخُيوط مُتعدِّدة — يُمكن استدعاء `addDocument` من
     * عِدّة خُيوط بِأَمان. التَّوازي يَستفيد من كلّ نَوى الـCPU في التَّوكِنة الّتي
     * تَستهلك مُعظم وَقت الاستيراد.
     *
     * الافتراضيّ: عَدد المُعالجات المُتاحة (مَحصور بَين ٢ و ٨).
     * النّطاق المَسموح: ١-١٦. أَكثر من عَدد النّوى نادراً ما يُفيد.
     */
    var parallelTextWorkers: Int
        get() {
            val def = Runtime.getRuntime().availableProcessors().coerceIn(2, 8)
            return (props.getProperty("parallel_text_workers", def.toString()).toIntOrNull() ?: def)
                .coerceIn(1, 16)
        }
        set(value) {
            props.setProperty("parallel_text_workers", value.coerceIn(1, 16).toString())
            save()
        }

    // ─── مواقع الفواصل القابلة للسحب (٠..١) ───
    // الافتراضات تُطابق التَوزيع الأصلي 280/420/840.

    /** نسبة عمود "الحقل المعرفيّ" من عَرض الصفّ الرئيسيّ. */
    var splitCategories: Float
        get() = props.getProperty("split_categories", "0.18").toFloatOrNull() ?: 0.18f
        set(value) {
            props.setProperty("split_categories", value.coerceIn(0.05f, 0.5f).toString())
            save()
        }

    /** نسبة "الكتب" من المساحة المتبقّية بعد عمود الحقل المعرفيّ. */
    var splitBooks: Float
        get() = props.getProperty("split_books", "0.33").toFloatOrNull() ?: 0.33f
        set(value) {
            props.setProperty("split_books", value.coerceIn(0.1f, 0.7f).toString())
            save()
        }

    /** نسبة عارض النصّ من ارتفاع لوحة العَرض (الباقي للجدول). */
    var splitDisplayVertical: Float
        get() = props.getProperty("split_display_vertical", "0.6").toFloatOrNull() ?: 0.6f
        set(value) {
            props.setProperty("split_display_vertical", value.coerceIn(0.2f, 0.85f).toString())
            save()
        }
}
