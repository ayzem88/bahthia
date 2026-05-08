package com.bahthia.search.highlighting

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BahthiaHighlighterTest {

    @Test
    fun segmentsWithSingleMatch() {
        val text = "بسم الله الرحمن"
        val parts = BahthiaHighlighter.segments(text, Regex("الله"))
        assertEquals(3, parts.size)
        assertEquals(false, parts[0].isMatch)
        assertEquals(true, parts[1].isMatch)
        assertEquals("الله", parts[1].text)
    }

    @Test
    fun segmentsWithMultipleMatches() {
        val text = "كتاب علم كتاب فقه"
        val parts = BahthiaHighlighter.segments(text, Regex("كتاب"))
        val matchCount = parts.count { it.isMatch }
        assertEquals(2, matchCount)
    }

    @Test
    fun segmentsWithNoMatchReturnsSinglePart() {
        val text = "كلام لا يحوي الكلمة"
        val parts = BahthiaHighlighter.segments(text, Regex("xyz"))
        assertEquals(1, parts.size)
        assertEquals(false, parts[0].isMatch)
    }

    @Test
    fun toHtmlWrapsMatchesInMarkTags() {
        val html = BahthiaHighlighter.toHtml("بسم الله الرحمن", Regex("الله"))
        assertTrue(html.contains("<mark>الله</mark>"))
    }

    @Test
    fun toHtmlEscapesSpecialCharacters() {
        val html = BahthiaHighlighter.toHtml("<script>alert(1)</script>", Regex("alert"))
        assertTrue(html.contains("&lt;script&gt;"))
        assertTrue(html.contains("<mark>alert</mark>"))
    }

    @Test
    fun snippetAroundReturnsContext() {
        val text = " ".repeat(200) + "كلمة الهدف هنا" + " ".repeat(200)
        val snippet = BahthiaHighlighter.snippetAround(text, Regex("الهدف"), windowChars = 20)
        assertTrue(snippet != null)
        assertTrue(snippet!!.contains("الهدف"))
        assertTrue(snippet.startsWith("…"))
        assertTrue(snippet.endsWith("…"))
    }

    @Test
    fun snippetAroundReturnsNullWhenNoMatch() {
        val text = "بعض النصّ"
        val snippet = BahthiaHighlighter.snippetAround(text, Regex("xyz"))
        assertNull(snippet)
    }

    @Test
    fun snippetHtmlCombinesSnippetAndHighlight() {
        val text = "بسم الله الرحمن الرحيم الحمد لله"
        val html = BahthiaHighlighter.snippetHtml(text, Regex("الله"), windowChars = 5)
        assertTrue(html != null)
        assertTrue(html!!.contains("<mark>الله</mark>"))
    }

    @Test
    fun customTagsAreUsed() {
        val html = BahthiaHighlighter.toHtml("بسم الله", Regex("الله"), openTag = "[[", closeTag = "]]")
        assertTrue(html.contains("[[الله]]"))
    }
}
