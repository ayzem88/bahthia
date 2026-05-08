package com.bahthia.search.normalization

/**
 * إضافة التشكيل الاختياري بعد كل حرف عربي في نمط regex.
 *
 * نقل دقيق من `_add_optional_tashkeel_pattern` في
 * `db/database.py:1127-1170`.
 *
 * المنطق:
 *   1. إن `respectDiacritics = false`: يحذف التشكيل من النمط
 *   2. يضيف `[تشكيل]*` بعد كل حرف عربي مفرد
 *   3. يحافظ على المجموعات `[...]` كما هي ويضيف التشكيل بعدها
 *   4. اختيارياً: يضيف حدود كلمة عربية
 */
object TashkeelPattern {

    private const val ARABIC_LETTERS = "ء-ي"
    private const val HARAKAT = "ً-ْ"
    private val ARABIC_LETTER_REGEX = Regex("[$ARABIC_LETTERS]")
    private val ALL_TASHKEEL_REGEX = Regex("[$HARAKAT]")

    /**
     * يضيف التشكيل الاختياري إلى نمط regex.
     *
     * @param pattern              النمط الأصلي (قد يحوي حروفاً ومجموعات [...])
     * @param respectDiacritics    إن true: يحافظ على التشكيل، لا يضيف اختياريّاً
     * @param withWordBoundary     إن true: يلفّ النتيجة بحدود كلمة عربية
     */
    fun addOptionalTashkeel(
        pattern: String,
        respectDiacritics: Boolean = false,
        withWordBoundary: Boolean = false,
    ): String {
        var work = pattern
        if (!respectDiacritics) {
            // 1. حذف التشكيل من النمط
            work = ALL_TASHKEEL_REGEX.replace(work, "")
            // 2. إضافة تشكيل اختياري بعد كل حرف عربي أو مجموعة
            work = injectOptionalTashkeel(work)
        }
        if (withWordBoundary) {
            work = "(?<![$ARABIC_LETTERS])$work(?![$ARABIC_LETTERS])"
        }
        return work
    }

    /**
     * يمشي على النمط حرفاً حرفاً ويضيف `[تشكيل]*` بعد كل حرف عربي
     * أو مجموعة `[...]`. منطق نقل حرفي من Python.
     */
    private fun injectOptionalTashkeel(pattern: String): String {
        val out = StringBuilder()
        var i = 0
        val tashkeelOpt = "[$HARAKAT]*"
        while (i < pattern.length) {
            val ch = pattern[i]
            // (أ) بداية مجموعة [...]
            if (ch == '[') {
                val end = pattern.indexOf(']', i)
                if (end != -1) {
                    out.append(pattern, i, end + 1) // المجموعة كاملة
                    out.append(tashkeelOpt)
                    i = end + 1
                    continue
                }
            }
            // (ب) حرف عربي عادي
            if (ARABIC_LETTER_REGEX.matches(ch.toString())) {
                out.append(ch)
                out.append(tashkeelOpt)
            } else {
                // (ج) أيّ حرف آخر: يبقى كما هو
                out.append(ch)
            }
            i++
        }
        return out.toString()
    }
}
