package com.bahthia.search.normalization

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Tests for [BahthiaArabicNormalizer].
 *
 * Behaviour: NO character normalization (per developer decision — option C).
 * The only transformation still active is optional tashkeel after each char
 * when `respectDiacritics = false`.
 */
class BahthiaArabicNormalizerTest {

    // ----------------------------------------------------------------
    // removeDiacritics
    // ----------------------------------------------------------------

    @Test
    fun removeDiacriticsStripsAllHarakat() {
        val input = "اللَّهُ"
        assertEquals("الله", BahthiaArabicNormalizer.removeDiacritics(input))
    }

    @Test
    fun removeDiacriticsLeavesPlainTextUnchanged() {
        assertEquals("الكتاب", BahthiaArabicNormalizer.removeDiacritics("الكتاب"))
        assertEquals("hello", BahthiaArabicNormalizer.removeDiacritics("hello"))
    }

    @Test
    fun removeDiacriticsHandlesEmptyString() {
        assertEquals("", BahthiaArabicNormalizer.removeDiacritics(""))
    }

    @Test
    fun removeDiacriticsHandlesAllHarakaTypes() {
        val input = "اَلْكِتَابُ ٱلْمُبِينٌ"
        val expected = "الكتاب ٱلمبين"
        assertEquals(expected, BahthiaArabicNormalizer.removeDiacritics(input))
    }

    // ----------------------------------------------------------------
    // charToPattern — لا تطبيع حروف
    // ----------------------------------------------------------------

    @Test
    fun charToPatternForLetterEscapesAndAddsOptionalTashkeel() {
        val pattern = BahthiaArabicNormalizer.charToPattern('ك', normalize = true)
        // لا تطبيع، فقط تشكيل اختياري بعد الحرف
        assertTrue(pattern.startsWith("ك"))
        assertTrue(pattern.endsWith("*"))
    }

    @Test
    fun charToPatternForAlefDoesNotMapToVariants() {
        // لا تكافؤ بين أشكال الألف
        val patAlef = BahthiaArabicNormalizer.charToPattern('ا', normalize = true)
        val patHamza = BahthiaArabicNormalizer.charToPattern('أ', normalize = true)
        // كل واحد يبقى نفسه (مع تشكيل اختياري)
        assertTrue(patAlef.startsWith("ا"))
        assertTrue(patHamza.startsWith("أ"))
        assertFalse(patAlef == patHamza, "Alef and hamza should produce distinct patterns")
        // الشكل المتوقّع: حرف ثم تشكيل اختياري
        val expected = "ا[${BahthiaArabicNormalizer.HARAKAT_CLASS}]*"
        assertEquals(expected, patAlef)
    }

    @Test
    fun charToPatternForTaaDoesNotEqualTaaMarbuta() {
        val ta = BahthiaArabicNormalizer.charToPattern('ت', normalize = true)
        val taMarbuta = BahthiaArabicNormalizer.charToPattern('ة', normalize = true)
        // لا تكافؤ
        assertTrue(ta.startsWith("ت"))
        assertTrue(taMarbuta.startsWith("ة"))
    }

    @Test
    fun charToPatternForYaaDoesNotEqualAlefMaqsura() {
        val ya = BahthiaArabicNormalizer.charToPattern('ي', normalize = true)
        val alefMaqsura = BahthiaArabicNormalizer.charToPattern('ى', normalize = true)
        assertTrue(ya.startsWith("ي"))
        assertTrue(alefMaqsura.startsWith("ى"))
    }

    @Test
    fun charToPatternWithoutNormalizeDoesNotAddTashkeel() {
        val pattern = BahthiaArabicNormalizer.charToPattern('ك', normalize = false)
        assertEquals("ك", pattern)
    }

    @Test
    fun charToPatternEscapesRegexMetacharacters() {
        // الأحرف الخاصّة في regex يجب أن تُفلَت
        val pattern = BahthiaArabicNormalizer.charToPattern('.', normalize = false)
        assertEquals("\\.", pattern)
    }

    // ----------------------------------------------------------------
    // buildWordPattern
    // ----------------------------------------------------------------

    @Test
    fun buildWordPatternMatchesExactWordWithOptionalTashkeel() {
        // البحث عن "كتاب" يطابق "كتاب" و "كَتَابٌ" (تشكيل اختياري) لكن ليس "كاتب"
        val pattern = BahthiaArabicNormalizer.buildWordPattern("كتاب", respectDiacritics = false)
        val regex = Regex(pattern)
        assertTrue(regex.containsMatchIn("كتاب"))
        assertTrue(regex.containsMatchIn("كَتَابٌ"))
        assertTrue(regex.containsMatchIn("كِتَابٍ"))
    }

    @Test
    fun buildWordPatternDoesNotMatchAlefVariants() {
        // "إيمان" لا يطابق "ايمان" بعد إلغاء التطبيع
        val pattern = BahthiaArabicNormalizer.buildWordPattern("إيمان", respectDiacritics = false)
        val regex = Regex(pattern)
        assertTrue(regex.containsMatchIn("إيمان"))
        assertFalse(regex.containsMatchIn("ايمان"))
        assertFalse(regex.containsMatchIn("أيمان"))
    }

    @Test
    fun buildWordPatternDoesNotMatchTaaVariants() {
        // "حياة" لا يطابق "حيات"
        val pattern = BahthiaArabicNormalizer.buildWordPattern("حياة", respectDiacritics = false)
        val regex = Regex(pattern)
        assertTrue(regex.containsMatchIn("حياة"))
        assertFalse(regex.containsMatchIn("حيات"))
    }

    @Test
    fun buildWordPatternDoesNotMatchAcrossYaaAlefMaqsura() {
        // "موسى" لا يطابق "موسي"
        val pattern = BahthiaArabicNormalizer.buildWordPattern("موسى", respectDiacritics = false)
        val regex = Regex(pattern)
        assertTrue(regex.containsMatchIn("موسى"))
        assertFalse(regex.containsMatchIn("موسي"))
    }

    @Test
    fun buildWordPatternRespectsDiacriticsWhenAsked() {
        // مع respect_diacritics=true: المطابقة دقيقة
        val pattern = BahthiaArabicNormalizer.buildWordPattern("اللَّه", respectDiacritics = true)
        val regex = Regex(pattern)
        assertTrue(regex.containsMatchIn("اللَّه"))
        assertFalse(regex.containsMatchIn("الله"))
    }

    @Test
    fun buildWordPatternWithWholeLettersWrapsBoundaries() {
        val pattern = BahthiaArabicNormalizer.buildWordPattern("علم", respectDiacritics = false, matchWholeLetters = true)
        val regex = Regex(pattern)
        assertTrue(regex.containsMatchIn("علم"))
        assertTrue(regex.containsMatchIn("ال علم اليوم"))
        assertFalse(regex.containsMatchIn("العلم"))
    }

    // ----------------------------------------------------------------
    // ضمانات سلوكيّة عامّة
    // ----------------------------------------------------------------

    @Test
    fun produceValidRegexForAllArabicAlphabet() {
        val arabicLetters = "ابتثجحخدذرزسشصضطظعغفقكلمنهويءآأإؤئىة"
        for (ch in arabicLetters) {
            val pattern = BahthiaArabicNormalizer.charToPattern(ch, normalize = true)
            try {
                Regex(pattern)
            } catch (e: Exception) {
                throw AssertionError("Invalid regex for '$ch': $pattern", e)
            }
        }
    }

    @Test
    fun emptyWordProducesEmptyPattern() {
        val pattern = BahthiaArabicNormalizer.buildWordPattern("", respectDiacritics = false)
        assertEquals("", pattern)
    }

    @Test
    fun normMapIsEmpty() {
        // ضمانة صريحة أنّ التطبيع مُعطَّل
        assertTrue(BahthiaArabicNormalizer.NORM_MAP.isEmpty())
    }
}
