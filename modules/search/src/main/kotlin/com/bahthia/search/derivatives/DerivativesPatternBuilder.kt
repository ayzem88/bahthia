package com.bahthia.search.derivatives

/**
 * بانٍ نمط البحث عن مشتقّات جذر.
 *
 * نقل دقيق من `db/database.py:search_derivatives` (السطر ١٤٨٦-١٥٢٤):
 *   - يبني regex من الجذر (مثل "كتب") يطابق كلّ مشتقّاته
 *   - لكلّ حرف من الجذر: `.*<حرف>` (يسمح بأحرف زائدة قبله من سـألتمونيها)
 *   - مع معالجة تنويعات الهمزة والواو/الياء عند تفعيل [enableVowelProcessing]
 *
 * مثال: الجذر "كتب" بـ `enableVowelProcessing=true` يصبح:
 *   `.*ك.*ت.*ب.*`
 * يطابق: كتب، كاتب، مكتوب، كتاب، كتابة، مكتبة، تكاتبا، ...
 */
object DerivativesPatternBuilder {

    /** أحرف الزيادة المسموح بها (سـألتمونيها) + التشكيل. */
    const val ALLOWED_LETTERS = "أإءئآؤسلتمونيهاةكًٌٍَُِّْ"

    /** تنويعات الهمزة. */
    val HAMZA_VARIATIONS = setOf('ء', 'أ', 'إ', 'آ', 'ؤ', 'ئ')

    /** تنويعات الواو/الياء (الحروف المعتلّة). */
    val WAWI_VARIATIONS = setOf('و', 'ي', 'ا')

    /**
     * يبني نمط regex لجذر معيّن.
     *
     * @param root                       الجذر (٢-٤ حروف عادة)
     * @param enableVowelProcessing      إن true: يطابق تنويعات الهمزة والواو/الياء
     */
    fun buildRootPattern(root: String, enableVowelProcessing: Boolean = true): String {
        val parts = root.map { letterPattern(it, enableVowelProcessing) }
        return parts.joinToString(separator = "", postfix = ".*")
    }

    /** نمط حرف واحد من الجذر. */
    fun letterPattern(letter: Char, enableVowelProcessing: Boolean): String =
        when {
            !enableVowelProcessing -> ".*$letter"
            letter == 'ء' -> ".*[ءأإآؤئ]"
            letter == 'و' || letter == 'ي' -> ".*[ويا]"
            else -> ".*$letter"
        }

    /**
     * مجموعة الأحرف المسموح بها (للجذر + التشكيل + سـألتمونيها).
     * تُستعمل لاحقاً للتأكّد من أنّ الكلمة المطابقة لا تحوي حروفاً غريبة.
     */
    fun allowedCharsFor(root: String, enableVowelProcessing: Boolean): Set<Char> {
        val set = (root.toSet() + ALLOWED_LETTERS.toSet()).toMutableSet()
        if (enableVowelProcessing) {
            if ('ء' in root) set.addAll(HAMZA_VARIATIONS)
            if ('و' in root || 'ي' in root) set.addAll(WAWI_VARIATIONS)
        }
        return set
    }
}
