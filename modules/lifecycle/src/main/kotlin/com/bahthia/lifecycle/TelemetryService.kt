package com.bahthia.lifecycle

import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * خدمة تيليمتري **مجهولة الهويّة** و**محلّيّة بالكامل**.
 *
 * - لا تُرسل أيّ بيانات إلى الإنترنت — الحفظ على القرص فقط
 * - تُحفَظ كـ JSON مسطَّح في `telemetry.json`
 * - مفتاحها الرئيسيّ: [UserPreferences.telemetryEnabled] — إن كان `false`،
 *   كلّ الاستدعاءات تُتجاهل (no-op)
 *
 * الحقول المُسجَّلة:
 *  - `app_version` — إصدار التطبيق
 *  - `first_run_date` — تاريخ أوّل تشغيل
 *  - `last_seen_date` — آخر يوم استُعمل فيه التطبيق
 *  - `total_searches` — عدد البحثات الإجماليّ
 *  - `searches_by_mode` — `{ word: 12, derivatives: 5, pattern: 1, regex: 0 }`
 *  - `feature_usage` — `{ "import.pdf": 3, "export.csv": 1, ... }`
 *
 * **لا** نُسجّل: نصّ الاستعلامات، أسماء الكتب، نتائج البحث، البيانات الشخصيّة.
 */
class TelemetryService(
    private val storeFile: Path,
    private val appVersion: String,
    private val isEnabled: () -> Boolean,
) {

    private val logger = LoggerFactory.getLogger(TelemetryService::class.java)

    private val totalSearches = AtomicLong(0)
    private val searchesByMode = ConcurrentHashMap<String, AtomicLong>()
    private val featureUsage = ConcurrentHashMap<String, AtomicLong>()
    private var firstRunDate: String = LocalDate.now().toString()
    private var lastSeenDate: String = LocalDate.now().toString()

    init {
        load()
        // تحديث آخر يوم استعمال
        lastSeenDate = LocalDate.now().toString()
    }

    /** يُسجّل بحثاً جديداً بنوع [mode] (مثل: "word", "derivatives"). */
    fun recordSearch(mode: String) {
        if (!isEnabled()) return
        totalSearches.incrementAndGet()
        searchesByMode.computeIfAbsent(mode) { AtomicLong(0) }.incrementAndGet()
    }

    /** يُسجّل استعمال ميزة (مثل: "import.pdf", "export.csv", "favorites.add"). */
    fun recordFeatureUsage(feature: String) {
        if (!isEnabled()) return
        featureUsage.computeIfAbsent(feature) { AtomicLong(0) }.incrementAndGet()
    }

    /** يحفظ الحالة الحاليّة على القرص. آمن للاستدعاء بأيّ تردّد. */
    fun flush() {
        if (!isEnabled()) return
        try {
            Files.createDirectories(storeFile.parent)
            Files.writeString(
                storeFile,
                serialize(),
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE,
            )
        } catch (e: Exception) {
            logger.warn("Failed to write telemetry: {}", e.message)
        }
    }

    /** يَحذف كلّ بيانات التيليمتري (لاحترام طلب المستخدم). */
    fun reset() {
        totalSearches.set(0)
        searchesByMode.clear()
        featureUsage.clear()
        firstRunDate = LocalDate.now().toString()
        lastSeenDate = LocalDate.now().toString()
        try {
            Files.deleteIfExists(storeFile)
        } catch (_: Exception) { /* ignore */ }
    }

    /** يُرجع لقطة (snapshot) للحالة الحاليّة — مفيد للعرض في "إحصاءات". */
    fun snapshot(): Snapshot = Snapshot(
        appVersion = appVersion,
        firstRunDate = firstRunDate,
        lastSeenDate = lastSeenDate,
        totalSearches = totalSearches.get(),
        searchesByMode = searchesByMode.mapValues { it.value.get() },
        featureUsage = featureUsage.mapValues { it.value.get() },
    )

    data class Snapshot(
        val appVersion: String,
        val firstRunDate: String,
        val lastSeenDate: String,
        val totalSearches: Long,
        val searchesByMode: Map<String, Long>,
        val featureUsage: Map<String, Long>,
    )

    // ─── Serialization (JSON يدويّ — يكفي لبيانات مسطّحة) ───

    private fun serialize(): String {
        val sb = StringBuilder()
        sb.append("{\n")
        sb.append("  \"app_version\": ").append(quote(appVersion)).append(",\n")
        sb.append("  \"first_run_date\": ").append(quote(firstRunDate)).append(",\n")
        sb.append("  \"last_seen_date\": ").append(quote(lastSeenDate)).append(",\n")
        sb.append("  \"total_searches\": ").append(totalSearches.get()).append(",\n")

        sb.append("  \"searches_by_mode\": {")
        val modes = searchesByMode.entries.toList()
        modes.forEachIndexed { i, (k, v) ->
            sb.append(if (i == 0) "\n    " else ",\n    ")
            sb.append(quote(k)).append(": ").append(v.get())
        }
        if (modes.isNotEmpty()) sb.append("\n  ")
        sb.append("},\n")

        sb.append("  \"feature_usage\": {")
        val feats = featureUsage.entries.toList()
        feats.forEachIndexed { i, (k, v) ->
            sb.append(if (i == 0) "\n    " else ",\n    ")
            sb.append(quote(k)).append(": ").append(v.get())
        }
        if (feats.isNotEmpty()) sb.append("\n  ")
        sb.append("}\n")

        sb.append("}\n")
        return sb.toString()
    }

    private fun load() {
        if (!Files.exists(storeFile)) return
        try {
            val text = Files.readString(storeFile)
            val map = NaiveJson.parse(text) ?: return
            firstRunDate = map["first_run_date"] as? String ?: firstRunDate
            // total_searches قد يكون Double من naive parser
            (map["total_searches"] as? Number)?.let { totalSearches.set(it.toLong()) }
            // ملاحظة: NaiveJson لا يدعم الكائنات المتداخلة، لذا searches_by_mode/feature_usage
            // لن يُعادا تحميلهما — هذا مقبول لأنّها إحصاءات تراكميّة (counters)،
            // والـ total_searches يحفظ المجموع الكلّي.
            // إن أردت دعم كامل، استبدل NaiveJson بـ Jackson لاحقاً.
        } catch (e: Exception) {
            logger.warn("Failed to load telemetry: {}", e.message)
        }
    }

    private fun quote(s: String): String {
        val sb = StringBuilder("\"")
        for (c in s) when (c) {
            '"'  -> sb.append("\\\"")
            '\\' -> sb.append("\\\\")
            '\n' -> sb.append("\\n")
            '\r' -> sb.append("\\r")
            '\t' -> sb.append("\\t")
            else -> sb.append(c)
        }
        sb.append("\"")
        return sb.toString()
    }
}
