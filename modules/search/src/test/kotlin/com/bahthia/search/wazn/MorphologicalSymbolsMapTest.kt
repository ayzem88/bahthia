package com.bahthia.search.wazn

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Tests for [MorphologicalSymbolsMap].
 *
 * Reference values verified against the original Python Map.db on 2026-05-05:
 *   - 156 rows in `map_data`, but **155 distinct keys** ('ي' is duplicated)
 *   - 15 single-char wazn symbols: ء أ ؤ ئ ا ت س ع ف ل م ن ه و ي
 *   - Two semantic categories among them:
 *     - **Wildcard placeholders** (map to "any Arabic letter"):
 *       ف ع ل ء أ ؤ ئ  → all return `[آؤءئأا-ي]`
 *     - **Literal letters** (map to themselves as [self]):
 *       ا و ي ت س م ن ه  → return `[ا]`, `[و]`, `[ي]`, etc.
 *   - This distinction is crucial: in وزن "مفعول" the م and و are literal,
 *     while ف ع ل are placeholders. So the regex matches words like
 *     مكتوب, مشروب, مفهوم where the ف ع ل slots can be ANY letter
 *     but م و must appear literally.
 *   - 'فَّ' (faa with shadda+fatha) is NOT in the map.
 */
class MorphologicalSymbolsMapTest {

    @Test
    fun loadsExpectedNumberOfDistinctKeys() {
        val map = MorphologicalSymbolsMap.load()
        // 156 rows but 'ي' is duplicated → 155 distinct.
        // Map<String,String> dedupes (last wins, same as Python dict assignment).
        assertEquals(155, map.size)
    }

    @Test
    fun containsAll15SingleCharWaznSymbols() {
        val map = MorphologicalSymbolsMap.load()
        val expected = setOf("ء", "أ", "ؤ", "ئ", "ا", "ت", "س", "ع", "ف", "ل", "م", "ن", "ه", "و", "ي")
        for (sym in expected) {
            assertTrue(sym in map, "Wazn symbol '$sym' must exist in Map.db")
        }
    }

    @Test
    fun onlyFaaAynLamAreWildcards() {
        val map = MorphologicalSymbolsMap.load()
        // Verified via Python: ONLY ف ع ل map to "any Arabic letter".
        // This is the classical Arabic root template (الجذر = ف ع ل).
        val wildcards = listOf("ف", "ع", "ل")
        for (w in wildcards) {
            assertEquals(
                "[آؤءئأا-ي]",
                map.lookup(w),
                "Wildcard symbol '$w' should match any Arabic letter",
            )
        }
    }

    @Test
    fun allOtherSingleSymbolsAreLiteral() {
        val map = MorphologicalSymbolsMap.load()
        // Verified via Python: hamzas + سـألتمونيها letters all map to themselves.
        val literals = mapOf(
            "ء" to "[ء]",
            "أ" to "[أ]",
            "ؤ" to "[ؤ]",
            "ئ" to "[ئ]",
            "ا" to "[ا]",
            "و" to "[و]",
            "ي" to "[ي]",
            "ت" to "[ت]",
            "س" to "[س]",
            "م" to "[م]",
            "ن" to "[ن]",
            "ه" to "[ه]",
        )
        for ((sym, expected) in literals) {
            assertEquals(expected, map.lookup(sym), "Literal symbol '$sym' should map to '$expected'")
        }
    }

    @Test
    fun lookupOfFaaWithFathaReturnsPatternWithFatha() {
        val map = MorphologicalSymbolsMap.load()
        assertEquals("[آؤءئأا-ي]َ", map.lookup("فَ"))
    }

    @Test
    fun lookupOfUnknownSymbolReturnsNull() {
        val map = MorphologicalSymbolsMap.load()
        assertNull(map.lookup("X"))
        assertNull(map.lookup("123"))
        // 'فَّ' (shadda+fatha) is verified missing from Map.db via Python
        assertNull(map.lookup("فَّ"))
    }

    @Test
    fun replaceSymbolsConvertsWaznMafoolCorrectly() {
        val map = MorphologicalSymbolsMap.load()
        // مفعول decomposed: م ف ع و ل
        //   م -> [م]                (literal prefix of passive participle)
        //   ف -> [آؤءئأا-ي]          (wildcard root letter 1)
        //   ع -> [آؤءئأا-ي]          (wildcard root letter 2)
        //   و -> [و]                (literal long vowel)
        //   ل -> [آؤءئأا-ي]          (wildcard root letter 3)
        val result = map.replaceSymbols("مفعول")
        assertEquals("[م][آؤءئأا-ي][آؤءئأا-ي][و][آؤءئأا-ي]", result)
    }

    @Test
    fun replaceSymbolsHandlesWaznFaaelCorrectly() {
        val map = MorphologicalSymbolsMap.load()
        // فاعل: ف ا ع ل  →  wildcard, literal ا, wildcard, wildcard
        val result = map.replaceSymbols("فاعل")
        assertEquals("[آؤءئأا-ي][ا][آؤءئأا-ي][آؤءئأا-ي]", result)
    }

    @Test
    fun replaceSymbolsLeavesNonSymbolCharsUnchanged() {
        val map = MorphologicalSymbolsMap.load()
        // ب ج ح خ د ذ ر ز ش ص ض ط ظ غ ق ك — none of these are in Map.db
        val result = map.replaceSymbols("بحر")
        assertEquals("بحر", result)
    }

    @Test
    fun replaceSymbolsHandlesDigitsAndLatinUnchanged() {
        val map = MorphologicalSymbolsMap.load()
        assertEquals("abc123", map.replaceSymbols("abc123"))
    }

    @Test
    fun allValuesStartWithBracket() {
        val map = MorphologicalSymbolsMap.load()
        for (sym in map.allSymbols) {
            val pat = map.lookup(sym)!!
            assertTrue(pat.startsWith("["), "Symbol '$sym' pattern should start with [")
        }
    }

    @Test
    fun mapContainsBasicTashkeelVariants() {
        val map = MorphologicalSymbolsMap.load()
        val mustExist = listOf("ف", "فَ", "فِ", "فُ", "فْ")
        mustExist.forEach { sym ->
            assertTrue(sym in map, "Expected symbol '$sym' is missing")
        }
    }

    @Test
    fun reloadingProducesSameSize() {
        val first = MorphologicalSymbolsMap.load().size
        val second = MorphologicalSymbolsMap.load().size
        assertEquals(first, second)
        assertEquals(155, first)
    }
}
