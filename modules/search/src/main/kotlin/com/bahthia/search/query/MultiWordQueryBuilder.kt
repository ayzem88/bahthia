package com.bahthia.search.query

import com.bahthia.search.normalization.BahthiaArabicNormalizer

/**
 * بانٍ نمط متعدّد الكلمات.
 *
 * المعاني (مطلوبة من المستخدم):
 *   - بدون رمز:  «جملة مطابقة بحرفها» — تُتجاهل علامات الترقيم بين الكلمات.
 *   - الرمز `|`: «تقارب» — كلمتان متجاورتان في نفس السطر أو السطر الذي يَليه،
 *                  بشرط ألّا تَتجاوز المسافة بينهما خمس كلمات (بأيّ ترتيب).
 *   - الرمز `+`: «نفس الصفحة» — يُعالَج خارج هذا البانٍ بـ `buildAndPatterns`
 *                  لأنّه يَتطلّب فحوصاً مستقلّة لا regex واحداً.
 */
object MultiWordQueryBuilder {

    /** علامات الترقيم المسموح بظهورها بين كلمات الجملة المطابقة. */
    private const val PUNCT = """[،,؛;:.!؟?\-–—()\[\]{}«»"'"""+'`'+"""]*"""

    /** فاصل بين كلمتَين في «جملة مطابقة»: يَسمح بعلامات ترقيم اختياريّة قبل وبعد المسافة. */
    private const val WORD_SEP = """${PUNCT}\s+${PUNCT}"""

    /** أقصى عدد كلمات تَفصل بين كلمتَي «التقارب». */
    private const val MAX_PROXIMITY_WORDS = 5

    /**
     * يحلّل الاستعلام ويبني نمط regex.
     *
     * **ملاحظة**: لا يَدعم الرمز `+` — هذا يُعالَج بمسار منفصل في `BahthiaSearcher`
     * لأنّه يَتطلّب فحص وجود كلّ كلمة مستقلّة في المحتوى، لا regex واحداً.
     *
     * @param query                نصّ الاستعلام
     * @param respectDiacritics    احترام التشكيل
     * @param matchWholeLetters    حدود كلمة عربيّة
     */
    fun build(
        query: String,
        respectDiacritics: Boolean = false,
        matchWholeLetters: Boolean = false,
    ): String {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return ""

        // التقارب: كلمة1 | كلمة2 ⇒ خمس كلمات بينهما كحدّ أقصى، بأيّ ترتيب
        if ('|' in trimmed) {
            val parts = trimmed.split('|')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            if (parts.size < 2) {
                // كلمة وحيدة بعد إزالة الفواصل — نَعود لبحث الكلمة المعتاد
                return BahthiaArabicNormalizer.buildWordPattern(parts.firstOrNull() ?: "", respectDiacritics, matchWholeLetters)
            }
            // ندعم زوجاً واحداً للبساطة (طلب المستخدم: كلمتَين فقط)
            val (a, b) = parts[0] to parts[1]
            val pa = BahthiaArabicNormalizer.buildWordPattern(a, respectDiacritics, matchWholeLetters)
            val pb = BahthiaArabicNormalizer.buildWordPattern(b, respectDiacritics, matchWholeLetters)
            // الجسر بين الكلمتَين: من صفر إلى خمس كلمات (مسموح فيها سطر جديد واحد كحدّ أقصى).
            // نَستعمل `[\s\S]` بدل `.` ليُغطّي السطر التّالي أيضاً.
            val bridge = """(?:${WORD_SEP}\S+){0,$MAX_PROXIMITY_WORDS}${WORD_SEP}"""
            // أيّ ترتيب: a→b أو b→a
            return "(?:${pa}${bridge}${pb}|${pb}${bridge}${pa})"
        }

        // جملة مطابقة (بدون رمز): كلمات بالترتيب مع تجاهل علامات الترقيم بينها
        val words = trimmed.split(Regex("""\s+""")).filter { it.isNotEmpty() }
        if (words.size == 1) {
            return BahthiaArabicNormalizer.buildWordPattern(words[0], respectDiacritics, matchWholeLetters)
        }
        val wordPatterns = words.map {
            BahthiaArabicNormalizer.buildWordPattern(it, respectDiacritics, matchWholeLetters)
        }
        return wordPatterns.joinToString(separator = WORD_SEP)
    }

    /**
     * يَفصل استعلام `+` إلى أنماط فرديّة.
     *
     * كلّ نمط يَجب أن يَتطابق على الأقلّ مرّة في الصفحة (AND على مستوى الصفحة).
     * يُعيد قائمة فارغة إن لم يَكن الاستعلام يَحوي `+`.
     */
    fun buildAndPatterns(
        query: String,
        respectDiacritics: Boolean = false,
        matchWholeLetters: Boolean = false,
    ): List<String> {
        if ('+' !in query) return emptyList()
        return query.split('+')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { BahthiaArabicNormalizer.buildWordPattern(it, respectDiacritics, matchWholeLetters) }
    }
}
