package com.bahthia.importer

/**
 * مستخرج الترويسات من بداية الملفات النصيّة.
 *
 * نقل دقيق لـ Python `converters/flexible_metadata_extractor.py`:
 *   - يقرأ أول ٢٠ سطراً غير فارغ
 *   - يكتشف الأسطر بصيغة `[الحقل]: القيمة`
 *   - يتوقّف عند سطر `[التصنيف]`
 *   - يحوّل الأرقام الهنديّة إلى لاتينيّة
 *   - يدعم الحقول الديناميكيّة (أيّ ترويسة لا تتطابق مع المعروفة)
 */
object HeaderExtractor {

    /** القيم الافتراضيّة للحقول الأساسيّة. */
    private const val DEFAULT_AUTHOR    = "مؤلف غير معروف"
    private const val DEFAULT_CATEGORY  = "غير مصنّف"
    private const val DEFAULT_YEAR      = "0000"

    private val HEADER_LINE_RE = Regex("""^\[([^\]]+)\]:\s*(.*)""")
    private val ARABIC_DIGIT_TRANSLATE = mapOf(
        '٠' to '0', '١' to '1', '٢' to '2', '٣' to '3', '٤' to '4',
        '٥' to '5', '٦' to '6', '٧' to '7', '٨' to '8', '٩' to '9',
    )

    private val FIELD_TITLE       = setOf("الكتاب")
    private val FIELD_AUTHOR      = setOf("المؤلّف", "المؤلف")
    private val FIELD_CATEGORY    = setOf("التّصنيف", "التصنيف", "التصنيفات", "الفئة", "القسم")
    private val FIELD_YEAR        = setOf("السّنة", "السنة", "سنة النشر", "تاريخ النشر",
                                          "تاريخ الاستعمال", "تاريخ الإستعمال")
    private val FIELD_DEATH_YEAR  = setOf("الوفاة", "سنة الوفاة", "تاريخ الوفاة")
    private val FIELD_WORD_COUNT  = setOf("عدد الكلمات", "الكلمات")
    private val FIELD_PAGES_COUNT = setOf("عدد الصّفحات", "عدد الصفحات", "الصفحات")
    private val USAGE_DATE_LABELS = setOf("تاريخ الاستعمال", "تاريخ الإستعمال")

    private val UNDEFINED_VALUES  = setOf("غير محدّد", "غير محدد", "0000", "0", "متفرقات")

    private val DUAL_DATE_RE = Regex("""(\d{1,4})\s*(?:ق\.?\s*)?\s*(?:ه|هـ)\s*/\s*(\d{1,4})\s*م""")
    private val SINGLE_YEAR_RE = Regex("""(\d{1,4})\s*(م|ه|هـ)?""")
    private val ARABIC_WORD_RE = Regex("[؀-ۿݐ-ݿࢠ-ࣿ]+")

    /** معلومات الكتاب المُستخرَجة. */
    data class Metadata(
        val title: String,
        val author: String,
        val category: String,
        val year: String,
        val deathYear: String? = null,
        val wordCount: Int? = null,
        val pagesCount: Int? = null,
        val dynamicFields: Map<String, String> = emptyMap(),
    )

    /**
     * يستخرج الميتاداتا من النصّ الكامل + اسم الملف.
     *
     * @param content   محتوى الملف.
     * @param filename  اسم الملف (يُستعمل كعنوان افتراضي إن لم تُذكر `[الكتاب]`).
     */
    fun extract(content: String, filename: String): Metadata {
        // اسم الملف بدون امتداد
        val defaultTitle = filename.substringAfterLast('/').substringAfterLast('\\').substringBeforeLast('.')

        var title = defaultTitle
        var author = DEFAULT_AUTHOR
        var category = DEFAULT_CATEGORY
        var year = DEFAULT_YEAR
        var deathYear: String? = null
        var wordCount: Int? = null
        var pagesCount: Int? = null
        val dynamic = mutableMapOf<String, String>()

        // أوّل ٢٠ سطراً غير فارغ
        val nonEmpty = content.lines().mapNotNull { it.trim().takeIf { l -> l.isNotEmpty() } }.take(20)

        for (line in nonEmpty) {
            val match = HEADER_LINE_RE.find(line) ?: continue
            val field = match.groupValues[1].trim()
            val rawValue = match.groupValues[2].trim()
            if (field.isEmpty() || rawValue.isEmpty()) continue
            val value = translateArabicDigits(rawValue)

            when (field) {
                in FIELD_CATEGORY -> {
                    val cat = if (value in setOf("غير محدّد", "غير محدد", "متفرقات")) DEFAULT_CATEGORY else value
                    category = cat
                    break // التوقّف عند التصنيف (سلوك Python الدقيق)
                }
                in FIELD_TITLE -> {
                    if (value !in UNDEFINED_VALUES) title = value
                }
                in FIELD_AUTHOR -> {
                    if (value !in UNDEFINED_VALUES) author = value
                }
                in FIELD_YEAR -> {
                    parseYear(value)?.let { year = it }
                }
                in FIELD_DEATH_YEAR -> {
                    parseYear(value)?.let { deathYear = it }
                }
                in FIELD_WORD_COUNT -> {
                    if (value !in UNDEFINED_VALUES) wordCount = parseInt(value)
                }
                in FIELD_PAGES_COUNT -> {
                    if (value !in UNDEFINED_VALUES) pagesCount = parseInt(value)
                }
                else -> {
                    dynamic[field] = value
                }
            }
        }

        // آليّة احتياطيّة للسنة من تاريخ الاستعمال
        if (year == DEFAULT_YEAR) {
            for (line in nonEmpty) {
                val match = HEADER_LINE_RE.find(line) ?: continue
                val field = match.groupValues[1].trim()
                if (field !in USAGE_DATE_LABELS) continue
                val value = translateArabicDigits(match.groupValues[2].trim())
                parseYear(value)?.let { year = it }
                break
            }
        }

        // إن لم يُذكر عدد الكلمات، عُدّ من المحتوى
        if (wordCount == null) {
            wordCount = ARABIC_WORD_RE.findAll(content).count()
        }

        return Metadata(
            title = title,
            author = author,
            category = category,
            year = year,
            deathYear = deathYear,
            wordCount = wordCount,
            pagesCount = pagesCount,
            dynamicFields = dynamic,
        )
    }

    private fun translateArabicDigits(s: String): String =
        s.map { ARABIC_DIGIT_TRANSLATE[it] ?: it }.joinToString("")

    /** يُحلّل قيمة سنة. يدعم التواريخ المزدوجة هـ/م. */
    private fun parseYear(value: String): String? {
        if (value.isBlank() || value in UNDEFINED_VALUES) return null
        DUAL_DATE_RE.find(value)?.let { return value.trim() }
        val m = SINGLE_YEAR_RE.find(value) ?: return null
        val num = m.groupValues[1]
        if (num == "0000" || num == "0") return null
        val suffix = m.groupValues.getOrNull(2) ?: ""
        return num + suffix
    }

    private fun parseInt(value: String): Int? {
        val cleaned = value
            .replace(",", "")
            .replace("،", "")
            .replace(Regex("""\s+"""), "")
            .replace(Regex("""كلمة.*"""), "")
        return Regex("""\d+""").find(cleaned)?.value?.toIntOrNull()
    }
}
