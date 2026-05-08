package com.bahthia.search

import com.bahthia.domain.TimeMode
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.LeafReaderContext
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.MatchAllDocsQuery
import org.apache.lucene.store.FSDirectory
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.nio.file.Path

/**
 * مزوّد إحصاءات المكتبة من فهرس Lucene الحقيقي.
 *
 * يقرأ كلّ مستندات الفهرس مرّة واحدة ويحسب:
 *   - قائمة الفئات مع عدد الكتب في كل فئة
 *   - قائمة الكتب مع عناوينها
 *   - إحصاءات إجمالية (عدد الكتب، الصفحات، السنوات...)
 */
class LibraryStats(indexDir: Path) : Closeable {

    private val logger = LoggerFactory.getLogger(LibraryStats::class.java)
    private val directory = FSDirectory.open(indexDir)
    private val reader = DirectoryReader.open(directory)

    /** عدد المستندات (الصفحات) المُفهرسة. */
    val totalPages: Int get() = reader.numDocs()

    /**
     * **مرور واحد** على كلّ المستندات يُخرج كلّ ما تَحتاجه الواجهة:
     *   - الفئات + السنوات + الجنسيّات (مع عَدّ الكتب الفريد لكلّ منها)
     *   - قائمة الكتب + إحصاءات إجماليّة (عدد، صفحات، كلمات تقريبيّة، مؤلّفون)
     *
     * هذا أسرع ٣× من استدعاء [categories]/[distinctYears]/[distinctRegions] متفرّقة،
     * ويُمكّن الـ ViewModel من تَخزين النتيجة مرّة واحدة والتَبديل بين العروض فوراً.
     */
    fun aggregateAll(
        timeMode: TimeMode = TimeMode.DEATH_YEAR,
        /**
         * Callback يُستدعى كلّ ~٢٠٠ مُستند بِـ`(scanned, total)`.
         * يُستعمَل لِشاشة البَدء (Splash) لِعَرض شَريط تَقدُّم حَقيقيّ.
         */
        onProgress: ((scanned: Int, total: Int) -> Unit)? = null,
    ): Aggregate {
        val byCategory = mutableMapOf<String, MutableSet<Long>>()
        val byYear     = mutableMapOf<String, MutableSet<Long>>()
        val byRegion   = mutableMapOf<String, MutableSet<Long>>()
        val unclassifiedCats   = mutableSetOf<Long>()
        val unclassifiedYears  = mutableSetOf<Long>()
        val unclassifiedRegions = mutableSetOf<Long>()
        val authors = mutableSetOf<String>()
        val booksMap = mutableMapOf<Long, BookInfo>()
        var totalChars = 0L
        // اسم حَقل المِحوَر الزَّمني وفقَ الإعداد
        val timeField = when (timeMode) {
            TimeMode.DEATH_YEAR -> "death_year"
            TimeMode.USAGE_DATE -> "year"
        }
        val FIELDS = setOf(
            "book_id", "book_title", "author", "category",
            "year", "death_year", "country", "content",
        )

        // إِجمالي المُستندات لِحساب نِسبة التَقدُّم
        val totalDocs = reader.leaves().sumOf { it.reader().maxDoc() }
        var scanned = 0
        // ابلاغ ابتدائي بـ 0
        onProgress?.invoke(0, totalDocs.coerceAtLeast(1))

        for (leaf in reader.leaves()) {
            val rdr = leaf.reader()
            val storedFields = rdr.storedFields()
            for (i in 0 until rdr.maxDoc()) {
                scanned++
                if (onProgress != null && (scanned % 200 == 0 || scanned == totalDocs)) {
                    onProgress(scanned, totalDocs.coerceAtLeast(1))
                }
                val doc = storedFields.document(i, FIELDS) ?: continue
                val bookId = doc.get("book_id")?.toLongOrNull() ?: continue

                // تَجميع الكلمات — تقريبي عبر طول المحتوى (مَتوسّط ٥ أحرف للكلمة العربيّة بفراغ)
                doc.get("content")?.let { totalChars += it.length }

                // تَجميع الفئة
                val cat = doc.get("category")
                if (cat.isNullOrBlank()) {
                    unclassifiedCats.add(bookId)
                } else {
                    byCategory.getOrPut(cat) { mutableSetOf() }.add(bookId)
                }

                // تَجميع المِحوَر الزَّمني — وفقَ الإعداد (سَنة الوَفاة أو تاريخ الاستعمال)
                val timeVal = doc.get(timeField)
                if (timeVal.isNullOrBlank() || timeVal == "0000") {
                    unclassifiedYears.add(bookId)
                } else {
                    byYear.getOrPut(timeVal) { mutableSetOf() }.add(bookId)
                }

                // تَجميع الجنسيّة
                val countryVal = doc.get("country")
                if (countryVal.isNullOrBlank()) {
                    unclassifiedRegions.add(bookId)
                } else {
                    byRegion.getOrPut(countryVal) { mutableSetOf() }.add(bookId)
                }

                // معلومات الكتاب — أوّل مرّة فقط
                if (bookId !in booksMap) {
                    val author = doc.get("author")
                    booksMap[bookId] = BookInfo(
                        id = bookId,
                        title = doc.get("book_title") ?: "بلا عنوان",
                        author = author,
                        category = cat,
                        year = doc.get("year"),
                        deathYear = doc.get("death_year"),
                        country = doc.get("country"),
                    )
                    if (!author.isNullOrBlank()) authors.add(author)
                }
            }
        }

        // بناء قوائم العَرض
        val totalUnique = booksMap.keys.size
        val categoryList = mutableListOf<CategoryStat>().apply {
            add(CategoryStat(ALL_BOOKS_SENTINEL, totalUnique))
            if (unclassifiedCats.isNotEmpty()) add(CategoryStat(UNCLASSIFIED_SENTINEL, unclassifiedCats.size))
            for ((c, ids) in byCategory.entries.sortedBy { it.key }) {
                add(CategoryStat(c, ids.size))
            }
        }
        // قَوائم السنوات والمناطق — مع إضافة "غير مُصنَّف" في الأَعلى إن وُجِد
        val yearList = mutableListOf<YearStat>().apply {
            if (unclassifiedYears.isNotEmpty()) add(YearStat(UNCLASSIFIED_SENTINEL, unclassifiedYears.size))
            for ((y, ids) in byYear.entries.sortedBy { it.key }) add(YearStat(y, ids.size))
        }
        val regionList = mutableListOf<RegionStat>().apply {
            if (unclassifiedRegions.isNotEmpty()) add(RegionStat(UNCLASSIFIED_SENTINEL, unclassifiedRegions.size))
            for ((r, ids) in byRegion.entries.sortedBy { it.key }) add(RegionStat(r, ids.size))
        }

        return Aggregate(
            totalBooks = totalUnique,
            totalPages = reader.numDocs(),
            // تَقدير: الكلمة العربيّة في المتوسّط ٥ أحرف بفراغها
            totalWordsEstimate = (totalChars / 5L).coerceAtLeast(0L),
            authorCount = authors.size,
            byCategory = categoryList,
            byYear = yearList,
            byRegion = regionList,
            books = booksMap.values.sortedBy { it.title },
        )
    }

    /**
     * يحسب الفئات الحقيقيّة مع عدد الكتب في كلّ منها.
     * يجمع المعرّفات الفريدة للكتب ضمن كل فئة (كتاب واحد له صفحات كثيرة).
     */
    fun categories(): List<CategoryStat> {
        val map = mutableMapOf<String, MutableSet<Long>>()
        var unclassified = mutableSetOf<Long>()

        for (leaf in reader.leaves()) {
            val rdr = leaf.reader()
            val storedFields = rdr.storedFields()
            for (i in 0 until rdr.maxDoc()) {
                val doc = storedFields.document(i, setOf("book_id", "category")) ?: continue
                val bookId = doc.get("book_id")?.toLongOrNull() ?: continue
                val cat = doc.get("category")
                if (cat.isNullOrBlank()) {
                    unclassified.add(bookId)
                } else {
                    map.getOrPut(cat) { mutableSetOf() }.add(bookId)
                }
            }
        }

        val out = mutableListOf<CategoryStat>()
        // مدخل افتراضي "جميع الكتب" — مفتاح حسّاس [ALL_BOOKS_SENTINEL] لا يَتداخل مع
        // أيّ تصنيف حقيقي. الواجهة تَستبدله بالعنوان المُترجَم عند العرض.
        val allBooks = (map.values.flatten() + unclassified).toSet()
        out += CategoryStat(ALL_BOOKS_SENTINEL, allBooks.size)
        if (unclassified.isNotEmpty()) out += CategoryStat(UNCLASSIFIED_SENTINEL, unclassified.size)
        for ((cat, ids) in map.entries.sortedBy { it.key }) {
            out += CategoryStat(cat, ids.size)
        }
        return out
    }

    companion object {
        /** سنتينل لتمييز خانة "جميع الكتب" عن التصنيفات الحقيقيّة — لا يَظهر للمستخدم. */
        const val ALL_BOOKS_SENTINEL = "__ALL_BOOKS__"
        /** سنتينل لخانة "غير مصنّف". */
        const val UNCLASSIFIED_SENTINEL = "__UNCLASSIFIED__"
    }

    /** قائمة كل الكتب: id, title, author, category, year, country. */
    fun allBooks(): List<BookInfo> {
        val seen = mutableSetOf<Long>()
        val out = mutableListOf<BookInfo>()

        for (leaf in reader.leaves()) {
            val rdr = leaf.reader()
            val storedFields = rdr.storedFields()
            for (i in 0 until rdr.maxDoc()) {
                val doc = storedFields.document(
                    i,
                    setOf("book_id", "book_title", "author", "category", "year", "country"),
                ) ?: continue
                val bookId = doc.get("book_id")?.toLongOrNull() ?: continue
                if (!seen.add(bookId)) continue
                out += BookInfo(
                    id = bookId,
                    title = doc.get("book_title") ?: "بلا عنوان",
                    author = doc.get("author"),
                    category = doc.get("category"),
                    year = doc.get("year"),
                    country = doc.get("country"),
                )
            }
        }
        return out.sortedBy { it.title }
    }

    /** مجموع الكتب الفريدة. */
    fun totalBooks(): Int = allBooks().size

    /**
     * يحسب السنوات الفريدة مع عدد الكتب لكلّ سنة.
     * يَجمع المعرّفات الفريدة للكتب لأنّ كلّ كتاب يَملك صفحات كثيرة.
     */
    fun distinctYears(): List<YearStat> {
        val map = mutableMapOf<String, MutableSet<Long>>()
        for (leaf in reader.leaves()) {
            val rdr = leaf.reader()
            val storedFields = rdr.storedFields()
            for (i in 0 until rdr.maxDoc()) {
                val doc = storedFields.document(i, setOf("book_id", "year")) ?: continue
                val bookId = doc.get("book_id")?.toLongOrNull() ?: continue
                val y = doc.get("year")?.takeIf { it.isNotBlank() && it != "0000" } ?: continue
                map.getOrPut(y) { mutableSetOf() }.add(bookId)
            }
        }
        return map.entries
            .sortedBy { it.key }
            .map { (y, ids) -> YearStat(y, ids.size) }
    }

    /**
     * يحسب الأقاليم/الدول الفريدة مع عدد الكتب.
     * يَستعمل الحقل `country` المخزَّن من ترويسة `[دولة المؤلّف]`.
     */
    fun distinctRegions(): List<RegionStat> {
        val map = mutableMapOf<String, MutableSet<Long>>()
        for (leaf in reader.leaves()) {
            val rdr = leaf.reader()
            val storedFields = rdr.storedFields()
            for (i in 0 until rdr.maxDoc()) {
                val doc = storedFields.document(i, setOf("book_id", "country")) ?: continue
                val bookId = doc.get("book_id")?.toLongOrNull() ?: continue
                val r = doc.get("country")?.takeIf { it.isNotBlank() } ?: continue
                map.getOrPut(r) { mutableSetOf() }.add(bookId)
            }
        }
        return map.entries
            .sortedBy { it.key }
            .map { (r, ids) -> RegionStat(r, ids.size) }
    }

    override fun close() {
        try { reader.close() } finally { directory.close() }
    }
}

/** نتيجة المرور الواحد — كلّ ما تَحتاجه واجهة المكتبة دفعة واحدة. */
data class Aggregate(
    val totalBooks: Int,
    val totalPages: Int,
    val totalWordsEstimate: Long,
    val authorCount: Int,
    val byCategory: List<CategoryStat>,
    val byYear: List<YearStat>,
    val byRegion: List<RegionStat>,
    val books: List<BookInfo>,
)

/** فئة + عدد كتبها الفعلي. */
data class CategoryStat(val name: String, val booksCount: Int)

/** سنة + عدد كتب صدرت/كُتبت فيها. */
data class YearStat(val year: String, val booksCount: Int)

/** إقليم/دولة + عدد كتب مؤلّفيها منها. */
data class RegionStat(val name: String, val booksCount: Int)

/** معلومات كتاب لعرضه في القائمة. */
data class BookInfo(
    val id: Long,
    val title: String,
    val author: String?,
    val category: String?,
    val year: String?,
    val deathYear: String? = null,
    val country: String? = null,
)
