package com.bahthia.search.wazn

import com.bahthia.search.normalization.TashkeelPattern

/**
 * بانٍ نمط الوزن الصرفي.
 *
 * نقل من `db/database.py:search_pattern` (السطر ١٣٩٩-١٤٠٧):
 *   1. تحويل الوزن (مثل "مفعول") إلى regex عبر [MorphologicalSymbolsMap]
 *   2. إضافة التشكيل الاختياري عبر [TashkeelPattern]
 *   3. اختياريّاً: حدود كلمة عربية
 *
 * مثال: "مفعول" يصبح:
 *   `[م][تشكيل]*[آؤءئأا-ي][تشكيل]*[آؤءئأا-ي][تشكيل]*[و][تشكيل]*[آؤءئأا-ي][تشكيل]*`
 *
 * يطابق: مكتوب، مشروب، مفهوم، مرفوع، …
 */
object WaznPatternBuilder {

    /**
     * يبني regex pattern للوزن.
     *
     * @param wazn                  الوزن (مثلاً: مفعول، فاعل، استفعال)
     * @param symbolsMap            خريطة الرموز المُحمَّلة من Map.db
     * @param respectDiacritics     إن true: يحافظ على التشكيل
     * @param matchWholeLetters     إن true: حدود كلمة عربية
     */
    fun build(
        wazn: String,
        symbolsMap: MorphologicalSymbolsMap,
        respectDiacritics: Boolean = false,
        matchWholeLetters: Boolean = false,
    ): String {
        val replaced = symbolsMap.replaceSymbols(wazn)
        return TashkeelPattern.addOptionalTashkeel(
            pattern = replaced,
            respectDiacritics = respectDiacritics,
            withWordBoundary = matchWholeLetters,
        )
    }
}
