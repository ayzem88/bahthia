package com.bahthia.search.indexer

import com.bahthia.domain.Page
import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.analysis.core.WhitespaceTokenizer
import org.apache.lucene.document.Document
import org.apache.lucene.document.Field
import org.apache.lucene.document.LongPoint
import org.apache.lucene.document.SortedDocValuesField
import org.apache.lucene.document.StoredField
import org.apache.lucene.document.StringField
import org.apache.lucene.document.TextField
import org.apache.lucene.index.ConcurrentMergeScheduler
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.index.TieredMergePolicy
import org.apache.lucene.store.FSDirectory
import org.apache.lucene.util.BytesRef
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.nio.file.Path

/**
 * بانٍ فهرس Lucene للمكتبة البحثيّة.
 *
 * يكتب وثيقة لكلّ صفحة من كلّ كتاب. الحقول:
 *
 *   حقل                       النوع                      الغرض
 *   ─────────────────────────  ─────────────────────────  ───────────────
 *   book_id                    LongPoint + Stored          فلترة وعرض
 *   page_num                   StoredField                 عرض فقط
 *   original_page_number       StoredField                 عرض رقم الصفحة الأصلي
 *   content                    TextField (indexed+stored)  للبحث (التشكيل يُزال داخل الـ Analyzer)
 *   book_title                 StringField + Stored        فلترة + عرض
 *   author                     StringField + Stored        عرض
 *   category                   StringField + Stored + Sort فلترة + ترتيب
 *   year                       StringField + Stored + Sort
 *
 * في المرحلة ٢-أسبوع-١ نستعمل [SimpleArabicAnalyzer] (تحليل خفيف).
 * في الأسبوع ٢ يُضاف منطق الأوزان/المشتقّات عبر RegexpQuery، لا عبر Analyzer.
 */
class BahthiaIndexer(
    indexDir: Path,
    private val analyzer: Analyzer = SimpleArabicAnalyzer(),
) : Closeable {

    private val logger = LoggerFactory.getLogger(BahthiaIndexer::class.java)
    private val directory = FSDirectory.open(indexDir)
    private val writer: IndexWriter

    init {
        val config = IndexWriterConfig(analyzer).apply {
            openMode = IndexWriterConfig.OpenMode.CREATE_OR_APPEND
            // ٥١٢ ميغا RAM buffer — يَستوعب آلاف المَستندات قَبل أيّ flush إلى القُرص.
            // البَرنامج العامّ يَحتاج RAM متاحة، لكن الاستيراد عَمليّة قَصيرة الأَمد.
            ramBufferSizeMB = 512.0
            // لا compound files — يَجعل البَناء أَسرع (ملفّات مُنفصلة لِكلّ مَقطع).
            // التَأثير على القِراءة طَفيف جدّاً، والمَكاسب في وَقت الاستيراد كَبيرة.
            useCompoundFile = false
            // سياسة دَمج هَرميّة مُهيَّأة لِبَدفعات ضَخمة — مَقاطع أَكبر وأَقلّ في النّهاية،
            // فَيُصبح البَحث أَسرع بَعد الاستيراد.
            mergePolicy = TieredMergePolicy().apply {
                segmentsPerTier = 20.0
                maxMergeAtOnce = 20
                maxMergedSegmentMB = 2048.0     // اِسمح بِمقاطع حتّى ٢ غيغا
                floorSegmentMB = 16.0
            }
            // مَجدوِل دَمج مُتعدِّد الخُيوط — يَستفيد من الـ CPU بِالكامل.
            mergeScheduler = ConcurrentMergeScheduler().apply {
                setMaxMergesAndThreads(4, 2)
                disableAutoIOThrottle()
            }
        }
        writer = IndexWriter(directory, config)
        logger.info("BahthiaIndexer opened at {}", indexDir)
    }

    /** يُضيف صفحة واحدة إلى الفهرس. */
    fun addPage(
        page: Page,
        bookTitle: String,
        author: String? = null,
        category: String? = null,
        year: String? = null,
        deathYear: String? = null,
        country: String? = null,
    ) {
        val doc = Document().apply {
            // book_id: numeric for filtering + stored for retrieval
            add(LongPoint("book_id", page.bookId))
            add(StoredField("book_id", page.bookId))

            // page numbering
            add(StoredField("page_num", page.pageNumber))
            page.originalPageNumber?.let { add(StoredField("original_page_number", it)) }

            // content (indexed + stored for highlighting)
            // الـ Analyzer (SimpleArabicAnalyzer) يَحذف التشكيل تلقائياً على الـ tokens
            // فلا حاجة لحقل `content_normalized` منفصل (كان مكتوباً لكنّه غير مستعمَل).
            add(TextField("content", page.content, Field.Store.YES))

            // book metadata (stored for display)
            add(StringField("book_title", bookTitle, Field.Store.YES))
            add(SortedDocValuesField("book_title_sort", BytesRef(bookTitle)))

            author?.let {
                add(StringField("author", it, Field.Store.YES))
                add(SortedDocValuesField("author_sort", BytesRef(it)))
            }
            category?.let {
                add(StringField("category", it, Field.Store.YES))
                add(SortedDocValuesField("category_sort", BytesRef(it)))
            }
            year?.let {
                add(StringField("year", it, Field.Store.YES))
                add(SortedDocValuesField("year_sort", BytesRef(it)))
            }
            deathYear?.let {
                add(StringField("death_year", it, Field.Store.YES))
                add(SortedDocValuesField("death_year_sort", BytesRef(it)))
            }
            country?.takeIf { it.isNotBlank() }?.let {
                add(StringField("country", it, Field.Store.YES))
                add(SortedDocValuesField("country_sort", BytesRef(it)))
            }
        }
        writer.addDocument(doc)
    }

    /** يُضيف عدّة صفحات معاً (أداء أفضل). */
    fun addPages(
        pages: Collection<Page>,
        bookTitle: String,
        author: String? = null,
        category: String? = null,
        year: String? = null,
        deathYear: String? = null,
        country: String? = null,
    ) {
        for (p in pages) addPage(p, bookTitle, author, category, year, deathYear, country)
    }

    /** يُجبر الكتابة على القرص. مفيد بعد عملية إدخال كبيرة. */
    fun commit() {
        writer.commit()
        logger.debug("Lucene index committed")
    }

    /**
     * يُحسّن الفهرس بِدَمج المَقاطع الصَّغيرة في عَدد قَليل من المَقاطع الكَبيرة.
     * يُستدعى **مَرّة واحدة** في نِهاية الاستيراد الجَماعيّ (ليس بَعد كلّ كتاب).
     *
     * الفائدة: استعلامات البَحث بَعد ذلك أَسرع بِشكل ملموس لِأنّ Lucene يَفتح
     * مَقاطع أَقلّ. التّكلفة: ثَوانٍ معدودة في نِهاية الاستيراد.
     *
     * @param maxSegments عَدد المَقاطع المُستهدف (الافتراضيّ ٤ — تَوازن جَيّد بَين
     *                    سُرعة الدَّمج وسُرعة البَحث).
     */
    fun optimizeForRead(maxSegments: Int = 4) {
        try {
            logger.info("Optimizing index — merging into {} segments", maxSegments)
            writer.forceMerge(maxSegments)
            writer.commit()
            logger.info("Index optimization complete")
        } catch (e: Exception) {
            logger.warn("Index optimization failed (non-fatal): {}", e.message)
        }
    }

    /**
     * يحذف كلّ صفحات كتاب بمعرّفه. يُستعمل في الحذف الانتقائي.
     * يُلزم استدعاء [commit] بعده لتثبيت التغيير على القرص.
     */
    fun deleteBook(bookId: Long): Long {
        val q = LongPoint.newExactQuery("book_id", bookId)
        val seq = writer.deleteDocuments(q)
        logger.info("Deleted book {} (sequence {})", bookId, seq)
        return seq
    }

    override fun close() {
        try {
            writer.close()
        } finally {
            directory.close()
        }
    }
}

/**
 * Analyzer **بِلا تَطبيع** — يَحفَظ النَّصّ كَما كَتبه المُؤلّف.
 *
 * - يُقسِّم على المسافات فَقط
 * - لا يَحذف التَّشكيل
 * - لا يُوحِّد أَشكال الأَلِف أو التّاء أو اليَاء
 * - لا lowercase (لِأَنّ النَّصّ عَرَبيّ مُعظمه)
 *
 * المُطابَقة الفِعليّة لِلكَلمات تَتمّ بِـregex على النَّصّ المُخزَّن (`Field.Store.YES`)
 * عَبر [com.bahthia.search.BahthiaSearcher]، وهي **حَرفيّة** بِشَكل افتراضيّ.
 */
class SimpleArabicAnalyzer : Analyzer() {
    override fun createComponents(fieldName: String): TokenStreamComponents {
        val tokenizer = WhitespaceTokenizer()
        return TokenStreamComponents(tokenizer)
    }
}
