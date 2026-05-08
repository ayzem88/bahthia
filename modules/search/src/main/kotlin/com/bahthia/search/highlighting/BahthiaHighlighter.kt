package com.bahthia.search.highlighting

/**
 * مُظلِّل النصوص: يُحوّل نتيجة بحث إلى أجزاء (مطابِقة / غير مطابِقة)
 * لتُعرض في الواجهة بتظليل لوني.
 */
object BahthiaHighlighter {

    /** قطعة من النصّ ضمن المقتطف. */
    data class Segment(val text: String, val isMatch: Boolean)

    /** علامتا الفتح والإغلاق الافتراضيّتان للتظليل بصيغة HTML. */
    const val DEFAULT_OPEN  = "<mark>"
    const val DEFAULT_CLOSE = "</mark>"

    /**
     * يقطّع النصّ إلى segments مع تمييز المطابقات.
     *
     * @param text         النصّ الأصلي.
     * @param matchRegex   النمط الذي طابق (نفس النمط المستعمل في البحث).
     * @param maxMatches   حدّ أقصى لعدد المطابقات (حماية).
     */
    fun segments(text: String, matchRegex: Regex, maxMatches: Int = 100): List<Segment> {
        if (text.isEmpty()) return emptyList()
        val out = mutableListOf<Segment>()
        var lastEnd = 0
        var count = 0
        for (m in matchRegex.findAll(text)) {
            if (count >= maxMatches) break
            val start = m.range.first
            val end = m.range.last + 1
            // قبل المطابقة
            if (start > lastEnd) {
                out += Segment(text.substring(lastEnd, start), isMatch = false)
            }
            out += Segment(text.substring(start, end), isMatch = true)
            lastEnd = end
            count++
        }
        if (lastEnd < text.length) {
            out += Segment(text.substring(lastEnd), isMatch = false)
        }
        return out
    }

    /**
     * إصدار HTML من النصّ مع تغليف المطابقات بـ `<mark>`.
     * يُهرَّب أي HTML داخل النصّ الأصلي لمنع XSS.
     */
    fun toHtml(
        text: String,
        matchRegex: Regex,
        openTag: String = DEFAULT_OPEN,
        closeTag: String = DEFAULT_CLOSE,
        maxMatches: Int = 100,
    ): String {
        val parts = segments(text, matchRegex, maxMatches)
        return buildString {
            for (p in parts) {
                if (p.isMatch) append(openTag)
                append(escapeHtml(p.text))
                if (p.isMatch) append(closeTag)
            }
        }
    }

    /**
     * يستخرج مقتطفاً مركَّزاً حول أوّل مطابقة.
     *
     * @param windowChars عدد الأحرف على كلّ جانب من المطابقة.
     */
    fun snippetAround(
        text: String,
        matchRegex: Regex,
        windowChars: Int = 80,
    ): String? {
        val first = matchRegex.find(text) ?: return null
        val start = (first.range.first - windowChars).coerceAtLeast(0)
        val end = (first.range.last + 1 + windowChars).coerceAtMost(text.length)
        val prefix = if (start > 0) "…" else ""
        val suffix = if (end < text.length) "…" else ""
        return prefix + text.substring(start, end) + suffix
    }

    /** بناء مقتطف + تظليل HTML معاً (للعرض الجاهز في WebView/Text). */
    fun snippetHtml(
        text: String,
        matchRegex: Regex,
        windowChars: Int = 80,
        openTag: String = DEFAULT_OPEN,
        closeTag: String = DEFAULT_CLOSE,
    ): String? {
        val raw = snippetAround(text, matchRegex, windowChars) ?: return null
        return toHtml(raw, matchRegex, openTag, closeTag)
    }

    private fun escapeHtml(s: String): String = buildString(s.length) {
        for (c in s) when (c) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&#39;")
            else -> append(c)
        }
    }
}
