package com.bahthia.importer

/**
 * مُقسّم النصّ إلى صفحات.
 *
 * نقل دقيق من Python `converters/txt_converter.py`:
 *   ١. إن وُجدت علامات `={4,}\s*رقم\s*={4,}` → التقسيم بالعلامات
 *   ٢. وإلا: التقسيم على ٣٠٠ كلمة عربيّة (احتياطي)
 *
 * دلالة العلامة: العلامة تُعنوِن الصفحة التي **تليها**.
 * المحتوى قبل أوّل علامة يُحفَظ كصفحة بـ `originalPageNumber=null`.
 */
object PageSplitter {

    private val PAGE_MARKER_RE = Regex("""^\s*={4,}\s*([0-9٠-٩]+)\s*={4,}\s*$""")
    private val ARABIC_WORD_RE = Regex("[؀-ۿݐ-ݿࢠ-ࣿ]+")
    private val ARABIC_DIGIT_TRANS: Map<Char, Char> = "٠١٢٣٤٥٦٧٨٩".toList()
        .zip("0123456789".toList())
        .toMap()

    /** صفحة منفردة + رقمها الأصلي (إن وُجد). */
    data class Split(val content: String, val originalPageNumber: String?)

    /**
     * يُقسّم النصّ إلى صفحات.
     *
     * @param content        النصّ الكامل.
     * @param wordsPerPage   عند فشل العلامات: حدّ الكلمات لكل صفحة (افتراضي ٣٠٠).
     */
    fun split(content: String, wordsPerPage: Int = 300): List<Split> {
        val byMarkers = splitByMarkers(content, wordsPerPage)
        if (byMarkers != null) return byMarkers
        return splitByWordCount(content, wordsPerPage).map { Split(it, null) }
    }

    private fun splitByMarkers(content: String, wordsPerPage: Int): List<Split>? {
        val lines = content.lines()
        if (lines.none { PAGE_MARKER_RE.matches(it) }) return null

        val pages = mutableListOf<Split>()
        val buffer = mutableListOf<String>()
        var currentNumber: String? = null

        for (line in lines) {
            val m = PAGE_MARKER_RE.find(line)
            if (m != null) {
                val pageText = buffer.joinToString("\n").trim()
                if (pageText.isNotEmpty()) pages += Split(pageText, currentNumber)
                buffer.clear()
                currentNumber = m.groupValues[1].map { ARABIC_DIGIT_TRANS[it] ?: it }.joinToString("")
            } else {
                buffer += line
            }
        }
        // الصفحة الأخيرة بعد آخر علامة
        val tail = buffer.joinToString("\n").trim()
        if (tail.isNotEmpty()) pages += Split(tail, currentNumber)

        return pages.takeIf { it.isNotEmpty() }
    }

    private fun splitByWordCount(content: String, wordsPerPage: Int): List<String> {
        val cleaned = content.trim().replace(Regex("""\n\s*\n+"""), "\n\n")
        if (cleaned.isEmpty()) return listOf(content)

        val pages = mutableListOf<String>()
        val current = mutableListOf<String>()
        var currentWordCount = 0

        for (line in cleaned.split('\n')) {
            val lineWords = ARABIC_WORD_RE.findAll(line).count()
            if (currentWordCount + lineWords > wordsPerPage && current.isNotEmpty()) {
                pages += current.joinToString("\n")
                current.clear()
                current += line
                currentWordCount = lineWords
            } else {
                current += line
                currentWordCount += lineWords
            }
        }
        if (current.isNotEmpty()) pages += current.joinToString("\n")
        return if (pages.isEmpty()) listOf(content) else pages
    }
}
