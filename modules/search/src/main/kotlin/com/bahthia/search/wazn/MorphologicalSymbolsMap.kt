package com.bahthia.search.wazn

import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.sql.DriverManager
import kotlin.io.path.createTempFile

/**
 * قارئ خريطة الرموز العربية للأوزان الصرفية من ملف `Map.db`.
 *
 * هذا الملف **ملكيّتك الفكريّة** المُنقَلة من نسخة Python كما هي:
 *  - ١٥٦ مدخلاً يربط رمزاً صرفيّاً (`ف`، `فَ`، `فِ`، …) بنمط regex مكافئ
 *    (`[آؤءئأا-ي]`، `[آؤءئأا-ي]َ`، …)
 *  - تستعمله الدالة [WaznPatternBuilder] (تأتي قريباً) لتحويل وزن مثل
 *    "مفعول" إلى regex يطابق "مكتوب"، "مشروب"، "مرفوع" وأخواتها.
 *
 * ## الموقع
 * يُحمَّل من classpath عبر `resources/com/bahthia/search/wazn/Map.db`.
 * في وقت التشغيل يُنسَخ إلى ملف مؤقّت لأنّ `sqlite-jdbc` يحتاج مساراً حقيقياً.
 *
 * ## الاستعمال
 * ```
 * val map = MorphologicalSymbolsMap.load()
 * val pattern = map.lookup("فَ")  // "[آؤءئأا-ي]َ"
 * ```
 *
 * @see com.bahthia.search.wazn.WaznPatternBuilder
 */
class MorphologicalSymbolsMap private constructor(
    private val data: Map<String, String>,
) {
    /** عدد المداخل (يُتوقَّع ١٥٦). */
    val size: Int get() = data.size

    /** يُرجع نمط regex المرتبط بالرمز، أو `null` إن لم يوجد. */
    fun lookup(symbol: String): String? = data[symbol]

    /** هل الرمز معروف في الخريطة؟ مفيد للتمييز بين رموز الوزن والحروف العادية. */
    operator fun contains(symbol: String): Boolean = symbol in data

    /** مُكافِئ مباشر لـ `_replace_symbols_weights` في Python. */
    fun replaceSymbols(word: String): String =
        word.map { ch -> data[ch.toString()] ?: ch.toString() }
            .joinToString("")

    /** كل الرموز المعروفة (للاختبارات والتشخيص). */
    val allSymbols: Set<String> get() = data.keys

    companion object {
        private val logger = LoggerFactory.getLogger(MorphologicalSymbolsMap::class.java)
        private const val RESOURCE_PATH = "/com/bahthia/search/wazn/Map.db"
        private const val TABLE_NAME = "map_data"

        /** يُحمِّل الخريطة من classpath (الوضع الإنتاجي). */
        fun load(): MorphologicalSymbolsMap {
            val resource = MorphologicalSymbolsMap::class.java.getResourceAsStream(RESOURCE_PATH)
                ?: error("Map.db not found in classpath at $RESOURCE_PATH")

            val tempFile = createTempFile(prefix = "bahthia-map-", suffix = ".db")
            tempFile.toFile().deleteOnExit()
            resource.use { input ->
                Files.copy(input, tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            }

            return loadFromFile(tempFile)
        }

        /** يُحمِّل الخريطة من مسار محدّد (للاختبارات أو حالات خاصّة). */
        fun loadFromFile(dbPath: Path): MorphologicalSymbolsMap {
            val data = mutableMapOf<String, String>()
            val url = "jdbc:sqlite:${dbPath.toAbsolutePath()}"

            DriverManager.getConnection(url).use { conn ->
                conn.createStatement().use { stmt ->
                    stmt.executeQuery("SELECT key, value FROM $TABLE_NAME").use { rs ->
                        while (rs.next()) {
                            val key = rs.getString("key") ?: continue
                            val value = rs.getString("value") ?: continue
                            data[key] = value
                        }
                    }
                }
            }

            logger.info("Map.db loaded: {} entries", data.size)
            return MorphologicalSymbolsMap(data.toMap())
        }
    }
}
