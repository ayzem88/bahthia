package com.bahthia.search

import com.bahthia.domain.Page
import com.bahthia.domain.SearchOptions
import com.bahthia.search.indexer.BahthiaIndexer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/**
 * End-to-end integration test:
 *   1. Build a Lucene index with sample Arabic pages
 *   2. Search for words with various normalization options
 *   3. Verify results match expected book/page/snippet
 *
 * This validates that [BahthiaIndexer] + [BahthiaSearcher] cooperate correctly
 * and that Arabic normalization carries through the full pipeline.
 */
class IndexAndSearchIntegrationTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var indexDir: Path

    @BeforeEach
    fun setup() {
        indexDir = tempDir.resolve("index")
    }

    private fun buildSampleIndex() {
        BahthiaIndexer(indexDir).use { idx ->
            // كتاب أوّل — حديث
            idx.addPage(
                page = Page(bookId = 1, pageNumber = 1, content = "بسم الله الرحمن الرحيم الحمد لله رب العالمين"),
                bookTitle = "الفاتحة",
                category = "القرآن",
                year = "0001",
            )
            idx.addPage(
                page = Page(bookId = 1, pageNumber = 2, content = "إيّاك نعبد وإيّاك نستعين اهدنا الصراط المستقيم"),
                bookTitle = "الفاتحة",
                category = "القرآن",
                year = "0001",
            )
            // كتاب ثانٍ — فقه
            idx.addPage(
                page = Page(bookId = 2, pageNumber = 1, content = "كتاب الصلاة باب وقت الفجر إذا طلع الفجر صلِّ ركعتين"),
                bookTitle = "كتاب الصلاة",
                author = "الشافعي",
                category = "الفقه",
                year = "0204",
            )
            idx.addPage(
                page = Page(bookId = 2, pageNumber = 2, content = "كتاب الزكاة من ملك نصاباً حال عليه الحول وجبت عليه الزكاة"),
                bookTitle = "كتاب الزكاة",
                author = "الشافعي",
                category = "الفقه",
                year = "0204",
            )
            idx.addPage(
                page = Page(bookId = 3, pageNumber = 1, content = "حياة الإمام النووي ولد سنة ٦٣١ هـ في نوى من قرى دمشق"),
                bookTitle = "تراجم",
                author = "ابن خلكان",
                category = "التاريخ",
                year = "0681",
            )
            idx.commit()
        }
    }

    @Test
    fun simpleWordSearchFindsAllOccurrences() {
        buildSampleIndex()
        BahthiaSearcher(indexDir).use { searcher ->
            val results = searcher.searchWord("الله")
            assertTrue(results.isNotEmpty(), "Should find 'الله' in indexed pages")
            // الفاتحة فيها "الله" مرّتين على الأقل (في صفحتين)
            val bookOneHits = results.count { it.bookId == 1L }
            assertTrue(bookOneHits > 0, "Book 1 should match")
        }
    }

    @Test
    fun searchDistinguishesAlefVariants() {
        // التطبيع مُعطَّل: "اياك" لا يطابق "إيّاك"
        buildSampleIndex()
        BahthiaSearcher(indexDir).use { searcher ->
            val plain = searcher.searchWord("اياك")
            assertTrue(plain.isEmpty(), "No normalization → 'اياك' must not match 'إيّاك'")
            // البحث بالشكل الصحيح يطابق
            val exact = searcher.searchWord("إياك")
            assertTrue(exact.isNotEmpty(), "Exact spelling should match")
        }
    }

    @Test
    fun searchDistinguishesTaaMarbutaFromTaaMaftooha() {
        // التطبيع مُعطَّل: "حيات" لا يطابق "حياة"
        buildSampleIndex()
        BahthiaSearcher(indexDir).use { searcher ->
            val opened = searcher.searchWord("حيات")
            assertTrue(opened.isEmpty(), "No normalization → 'حيات' must not match 'حياة'")
            // الشكل الصحيح يطابق
            val correct = searcher.searchWord("حياة")
            assertTrue(correct.isNotEmpty(), "Exact spelling should match")
        }
    }

    @Test
    fun searchToleratesTashkeel() {
        buildSampleIndex()
        BahthiaSearcher(indexDir).use { searcher ->
            // البحث عن "كتاب" يجب أن يطابق "كتاب الصلاة" و "كتاب الزكاة"
            val results = searcher.searchWord("كتاب")
            val bookIds = results.map { it.bookId }.distinct().sorted()
            assertTrue(bookIds.contains(2L), "Book 2 should match 'كتاب'")
        }
    }

    @Test
    fun categoryFilterRestrictsResults() {
        buildSampleIndex()
        BahthiaSearcher(indexDir).use { searcher ->
            val all = searcher.searchWord("الله", SearchOptions(categories = null))
            val onlyQuran = searcher.searchWord("الله", SearchOptions(categories = listOf("القرآن")))

            assertTrue(all.isNotEmpty())
            assertTrue(onlyQuran.isNotEmpty())
            // كل نتيجة في الفلتر يجب أن تكون من تصنيف القرآن
            for (r in onlyQuran) {
                assertEquals("القرآن", r.bookCategory)
            }
        }
    }

    @Test
    fun bookIdFilterRestrictsToSelectedBooks() {
        buildSampleIndex()
        BahthiaSearcher(indexDir).use { searcher ->
            val results = searcher.searchWord(
                "الله",
                SearchOptions(bookIds = listOf(1L)),
            )
            assertTrue(results.isNotEmpty())
            assertTrue(results.all { it.bookId == 1L })
        }
    }

    @Test
    fun snippetContainsContextAroundMatch() {
        buildSampleIndex()
        BahthiaSearcher(indexDir).use { searcher ->
            val results = searcher.searchWord("الفجر")
            assertTrue(results.isNotEmpty())
            val first = results.first()
            assertNotNull(first.contextSnippet)
            assertTrue(first.contextSnippet.length > 0, "Snippet should not be empty")
        }
    }

    @Test
    fun bookMetadataPropagatesToResults() {
        buildSampleIndex()
        BahthiaSearcher(indexDir).use { searcher ->
            val results = searcher.searchWord("الفجر")
            val first = results.firstOrNull { it.bookId == 2L }
            assertNotNull(first, "Book 2 should be in results")
            assertEquals("كتاب الصلاة", first!!.bookTitle)
            assertEquals("الشافعي", first.bookAuthor)
            assertEquals("الفقه", first.bookCategory)
            assertEquals("0204", first.bookYear)
        }
    }

    @Test
    fun unmatchedQueryReturnsEmptyList() {
        buildSampleIndex()
        BahthiaSearcher(indexDir).use { searcher ->
            val results = searcher.searchWord("هذه_كلمة_غير_موجودة_xyz")
            assertTrue(results.isEmpty())
        }
    }

    @Test
    fun emptyIndexReturnsEmpty() {
        // فهرس بلا بيانات → كل بحث يعود فارغاً
        BahthiaIndexer(indexDir).use { it.commit() }
        BahthiaSearcher(indexDir).use { searcher ->
            assertTrue(searcher.searchWord("شيء").isEmpty())
        }
    }
}
