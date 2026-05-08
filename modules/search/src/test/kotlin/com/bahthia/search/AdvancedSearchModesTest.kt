package com.bahthia.search

import com.bahthia.domain.Page
import com.bahthia.domain.SearchMode
import com.bahthia.domain.SearchOptions
import com.bahthia.search.indexer.BahthiaIndexer
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/**
 * اختبارات أوضاع البحث المتقدّمة:
 *   - PATTERN (الوزن الصرفي)
 *   - DERIVATIVES (الجذر والمشتقّات)
 *   - REGEX
 *   - WORD مع `+` و `|`
 */
class AdvancedSearchModesTest {

    @TempDir lateinit var tempDir: Path
    private lateinit var indexDir: Path

    @BeforeEach
    fun setup() { indexDir = tempDir.resolve("index") }

    private fun buildSampleIndex() {
        BahthiaIndexer(indexDir).use { idx ->
            // كلمات تتضمّن أوزان مختلفة
            idx.addPage(
                Page(bookId = 1, pageNumber = 1, content = "كَتَبَ كاتبٌ كتاباً مكتوباً مكتبة كتابة"),
                bookTitle = "اللغة",
                category = "اللغة",
            )
            idx.addPage(
                Page(bookId = 2, pageNumber = 1, content = "علم العلماء كلَّ الناسِ علم النحو والصرف"),
                bookTitle = "النحو",
                category = "اللغة",
            )
            idx.addPage(
                Page(bookId = 3, pageNumber = 1, content = "قال رسول الله صلى الله عليه وسلم في الحديث"),
                bookTitle = "الحديث",
                category = "الحديث",
            )
            idx.addPage(
                Page(bookId = 4, pageNumber = 1, content = "ضرب الرجل ضرباً شديداً وهو مضروب ضارب"),
                bookTitle = "النحو 2",
                category = "اللغة",
            )
            idx.commit()
        }
    }

    // ----- WORD: متعدّد الكلمات -----

    @Test
    fun wordSearchSimplePhrase() {
        buildSampleIndex()
        BahthiaSearcher(indexDir).use { s ->
            val r = s.searchWord("رسول الله")
            assertTrue(r.isNotEmpty())
            assertTrue(r.any { it.bookId == 3L })
        }
    }

    @Test
    fun wordSearchProximityWithPipe() {
        buildSampleIndex()
        BahthiaSearcher(indexDir).use { s ->
            // الرمز `|` = تقارب: كلمتان متجاورتان بينهما خمس كلمات أو أقلّ بأيّ ترتيب
            // الصفحة 2 فيها "علم العلماء كلَّ الناسِ علم النحو" — "علم" و "النحو"
            // بينهما خمس كلمات بالضبط.
            val r = s.searchWord("علم | النحو")
            assertTrue(r.any { it.bookId == 2L }, "Expected book 2 (within 5 words)")
        }
    }

    @Test
    fun wordSearchAndOnSamePageWithPlus() {
        buildSampleIndex()
        BahthiaSearcher(indexDir).use { s ->
            // الرمز `+` = كلتا الكلمتين في نفس الصفحة بأيّ موضع وأيّ ترتيب
            // الصفحة 1 فيها "كاتب" و "مكتوباً" → تَطابق
            val r = s.searchWord("كاتب + مكتوبا")
            assertTrue(r.any { it.bookId == 1L })
        }
    }

    // ----- PATTERN: الوزن الصرفي -----

    @Test
    fun patternMafoolMatchesPassiveParticiples() {
        buildSampleIndex()
        BahthiaSearcher(indexDir).use { s ->
            // الوزن "مفعول" يجب أن يطابق "مكتوب" و "مضروب"
            val r = s.searchPattern("مفعول")
            assertTrue(r.isNotEmpty(), "Should find passive participles")
            val terms = r.map { it.matchedTerm }
            assertTrue(terms.any { it.contains("مكتوب") || it.contains("مضروب") })
        }
    }

    @Test
    fun patternFaaelMatchesActiveParticiples() {
        buildSampleIndex()
        BahthiaSearcher(indexDir).use { s ->
            // "فاعل" يجب أن يطابق "كاتب" و "ضارب"
            val r = s.searchPattern("فاعل")
            assertTrue(r.isNotEmpty(), "Should find active participles")
            val terms = r.map { it.matchedTerm }
            assertTrue(terms.any { it.contains("كاتب") || it.contains("ضارب") })
        }
    }

    @Test
    fun patternEmptyQueryReturnsEmpty() {
        buildSampleIndex()
        BahthiaSearcher(indexDir).use { s ->
            assertTrue(s.searchPattern("").isEmpty())
            assertTrue(s.searchPattern("   ").isEmpty())
        }
    }

    // ----- DERIVATIVES: الجذر والمشتقّات -----

    @Test
    fun derivativesOfRootKtbFindsAllDerivatives() {
        buildSampleIndex()
        BahthiaSearcher(indexDir).use { s ->
            val r = s.searchDerivatives("كتب")
            assertTrue(r.isNotEmpty())
            // يجب أن يطابق كتب، كاتب، كتاب، مكتوب، مكتبة، كتابة
            val terms = r.map { it.matchedTerm }.toSet()
            assertTrue(terms.size >= 3, "Expected multiple derivatives, got: $terms")
        }
    }

    @Test
    fun derivativesOfRootElmFindsScholars() {
        buildSampleIndex()
        BahthiaSearcher(indexDir).use { s ->
            val r = s.searchDerivatives("علم")
            assertTrue(r.isNotEmpty())
            val terms = r.map { it.matchedTerm }
            assertTrue(terms.any { it.contains("علم") || it.contains("علماء") })
        }
    }

    // ----- REGEX -----

    @Test
    fun regexExactWordMatch() {
        buildSampleIndex()
        BahthiaSearcher(indexDir).use { s ->
            val r = s.searchRegex("قال")
            assertTrue(r.isNotEmpty())
            assertTrue(r.any { it.bookId == 3L })
        }
    }

    @Test
    fun regexWithCharClass() {
        buildSampleIndex()
        BahthiaSearcher(indexDir).use { s ->
            // كل كلمة من ثلاثة أحرف تبدأ بـ ك أو م وتنتهي بـ ب
            val r = s.searchRegex("[كم].ب")
            assertTrue(r.isNotEmpty())
        }
    }

    // ----- نقطة الدخول الموحَّدة -----

    @Test
    fun unifiedSearchEntryPointDispatches() {
        buildSampleIndex()
        BahthiaSearcher(indexDir).use { s ->
            val word = s.search("الله", SearchMode.WORD)
            val pattern = s.search("مفعول", SearchMode.PATTERN)
            val deriv = s.search("كتب", SearchMode.DERIVATIVES)
            assertTrue(word.isNotEmpty())
            assertTrue(pattern.isNotEmpty())
            assertTrue(deriv.isNotEmpty())
        }
    }

    @Test
    fun searchWithCategoryFilter() {
        buildSampleIndex()
        BahthiaSearcher(indexDir).use { s ->
            val all = s.searchPattern("مفعول")
            val onlyLanguage = s.searchPattern("مفعول", SearchOptions(categories = listOf("اللغة")))
            assertTrue(all.size >= onlyLanguage.size)
            for (r in onlyLanguage) {
                assertTrue(r.bookCategory == "اللغة")
            }
        }
    }
}
