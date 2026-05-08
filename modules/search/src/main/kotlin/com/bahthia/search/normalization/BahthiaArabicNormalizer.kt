package com.bahthia.search.normalization

/**
 * تطبيع عربي مخصّص — منقول حرفاً بحرف من نسخة Python:
 *   `shared/arabic_search.py` → `_NORM_MAP` و `_char_to_pattern`
 *
 * ## الغرض
 * بناء أنماط regex للبحث عن الكلمات العربية بحيث تطابق التنويعات المختلفة:
 *   - أشكال الألف (آ أ إ ا ٱ) كلها متكافئة
 *   - التاء المربوطة والمفتوحة (ة ت) متكافئتان
 *   - الألف المقصورة والياء (ى ي) متكافئتان
 *   - التشكيل اختياري (إن `respectDiacritics=false`)
 *
 * ## ملاحظة مهمّة عن الفرق مع Lucene
 * `org.apache.lucene.analysis.ar.ArabicNormalizer` يُطبّع **`ة → ه`** (إلى الهاء).
 * نسختنا تُطبّق **`ة ↔ ت`** (التاء المربوطة تكافئ المفتوحة).
 * هذا فرق سلوكيّ مقصود — نستعمل القاعدة العربيّة التراثيّة.
 */
object BahthiaArabicNormalizer {

    // -------------------------------------------------------------
    // الثوابت الأساسيّة (مطابقة لـ shared/arabic_search.py:21-24)
    // -------------------------------------------------------------

    /** علامات التشكيل العربية الشائعة (الحركات + علامات إضافيّة). */
    const val HARAKAT_CLASS = "ً-ٰٟۖ-ۭ"

    /** الحروف العربيّة الأساسيّة (همزات + ألفات + باقي الحروف). */
    const val ARABIC_LETTERS_CLASS = "ء-ي"

    /** الحروف اللاتينية والأرقام. */
    const val ALNUM_CLASS = "a-zA-Z0-9"

    /** فئة موحَّدة تشمل العربي + اللاتيني + الأرقام (لحدود الكلمة). */
    const val WORD_CHARS_CLASS = "$ALNUM_CLASS$ARABIC_LETTERS_CLASS"

    // -------------------------------------------------------------
    // مجموعات التكافؤ (مطابقة لـ ALEF_VARIANTS, TAA_VARIANTS, إلخ)
    // -------------------------------------------------------------

    /** أشكال الألف: آ أ إ ا ٱ (الألف الخنجريّة الفوقيّة معدودة في Python أيضاً). */
    const val ALEF_VARIANTS = "آأإاٱ"

    /** التاء المربوطة والمفتوحة. */
    const val TAA_VARIANTS = "ةت"

    /** الألف المقصورة والياء. */
    const val ALEF_MAQSURA_VARIANTS = "ىي"

    /** الواو الهمزيّة (ؤ). */
    const val WAW_HAMZA = "ؤ"

    /** الياء الهمزيّة (ئ). */
    const val YAA_HAMZA = "ئ"

    // -------------------------------------------------------------
    // NORM_MAP — مُعطَّلة بقرار المستخدم (الخيار ج)
    // -------------------------------------------------------------

    /**
     * خريطة تطبيع الحروف. **فارغة عمداً** بناءً على قرار المطوّر:
     *   - لا تكافؤ بين أشكال الألف (آ أ إ ا ٱ تبقى متمايزة)
     *   - لا تكافؤ بين التاء المربوطة والمفتوحة
     *   - لا تكافؤ بين الألف المقصورة والياء
     *
     * **ملاحظة**: هذا يختلف عن سلوك نسخة Python الحالية (التي تحوي
     * تكافؤات في `_NORM_MAP`). إن أردتَ لاحقاً إعادة تفعيل أيّ تكافؤ،
     * أضف عناصر إلى الخريطة هنا.
     */
    val NORM_MAP: Map<Char, String> = emptyMap()

    // -------------------------------------------------------------
    // الدوال
    // -------------------------------------------------------------

    private val harakatRegex = Regex("[$HARAKAT_CLASS]")

    /** أحرف regex الخاصّة التي يجب إفلاتها — مطابقة لـ Python's `re.escape()` السلوكية. */
    private val REGEX_METACHARS = setOf(
        '.', '^', '$', '|', '?', '*', '+', '(', ')', '[', ']', '{', '}', '\\',
    )

    /**
     * يُفلت حرفاً واحداً للاستعمال في regex، مطابقاً لسلوك Python's `re.escape`:
     *   - الأحرف الخاصّة → مسبوقة بـ `\`
     *   - الأحرف العادية (عربي، لاتيني، تشكيل) → كما هي
     *
     * يختلف عن `Regex.escape()` في Kotlin الذي يلفّ بـ `\Q...\E` (سلوك مختلف).
     */
    private fun escapeChar(c: Char): String =
        if (c in REGEX_METACHARS) "\\$c" else c.toString()

    /** يحذف التشكيل من النصّ. مكافئ لـ `remove_diacritics` في Python. */
    fun removeDiacritics(text: String): String = harakatRegex.replace(text, "")

    /**
     * يحوّل حرفاً واحداً إلى نمط regex يطابق كلّ أشكاله.
     * مكافئ تامّ لـ `_char_to_pattern` في `arabic_search.py:55-68`.
     *
     * @param char        الحرف المراد تحويله.
     * @param normalize   إن `true` (= `respect_diacritics=false`): يُطبَّق التطبيع الكامل
     *                    (تنويعات الألف/التاء/الياء + تشكيل اختياري).
     */
    fun charToPattern(char: Char, normalize: Boolean = true): String {
        return if (normalize && char in NORM_MAP) {
            // حرف ضمن مجموعة تكافؤ → استبدله بالمجموعة + تشكيل اختياري
            NORM_MAP[char]!! + "[$HARAKAT_CLASS]*"
        } else {
            // حرف عادي → أفلته (escape) + ربّما تشكيل اختياري
            val escaped = escapeChar(char)
            if (normalize) escaped + "[$HARAKAT_CLASS]*" else escaped
        }
    }

    /**
     * يبني نمط regex يطابق كلمة كاملة مع تنويعاتها.
     * مكافئ مباشر لـ `build_word_pattern` في `arabic_search.py:123-150`.
     *
     * @param word                    الكلمة الأصليّة.
     * @param respectDiacritics       إن `true`: التطبيع معطّل (مطابقة دقيقة).
     * @param matchWholeLetters       إن `true`: يُلفّ النمط بحدود كلمة عربيّة.
     */
    fun buildWordPattern(
        word: String,
        respectDiacritics: Boolean = false,
        matchWholeLetters: Boolean = false,
    ): String {
        val normalize = !respectDiacritics
        val cleaned = if (normalize) removeDiacritics(word) else word

        val core = cleaned.map { charToPattern(it, normalize) }.joinToString("")

        return if (matchWholeLetters) {
            // حدود كلمة عربيّة: لا حرف عربي ولا تشكيل ملاصق
            wrapWithArabicBoundaries(core)
        } else {
            core
        }
    }

    /**
     * يلفّ نمطاً بحدود كلمة عربيّة (lookbehind + lookahead).
     * مكافئ لـ منطق `_strict_diacritic_word_wrap` في Python (مبسَّط).
     */
    fun wrapWithArabicBoundaries(pattern: String): String =
        "(?<![$ARABIC_LETTERS_CLASS$HARAKAT_CLASS])$pattern(?![$ARABIC_LETTERS_CLASS$HARAKAT_CLASS])"
}
