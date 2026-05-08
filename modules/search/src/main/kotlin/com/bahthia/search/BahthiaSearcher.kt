package com.bahthia.search

import com.bahthia.domain.Page
import com.bahthia.domain.SearchMode
import com.bahthia.domain.SearchOptions
import com.bahthia.domain.SearchResult
import com.bahthia.search.derivatives.DerivativesPatternBuilder
import com.bahthia.search.indexer.SimpleArabicAnalyzer
import com.bahthia.search.normalization.BahthiaArabicNormalizer
import com.bahthia.search.normalization.TashkeelPattern
import com.bahthia.search.query.MultiWordQueryBuilder
import com.bahthia.search.wazn.MorphologicalSymbolsMap
import com.bahthia.search.wazn.WaznPatternBuilder
import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.document.Document
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.Term
import org.apache.lucene.search.BooleanClause
import org.apache.lucene.search.BooleanQuery
import org.apache.lucene.search.FieldExistsQuery
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.MatchAllDocsQuery
import org.apache.lucene.search.Query
import org.apache.lucene.search.ScoreDoc
import org.apache.lucene.search.TermQuery
import org.apache.lucene.store.FSDirectory
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.nio.file.Path

/**
 * واجهة البحث الموحَّدة على فهرس Lucene.
 *
 * ## معماريّة التنفيذ (مطابقة لنسخة Python)
 *
 * 1. **Lucene**: لتصفية المستندات بحسب الميتاداتا (تصنيف، كتاب، سنة)
 * 2. **Java regex على المحتوى**: لإيجاد المطابقات الفعلية
 *    - `searchWord/searchPattern/searchRegex`: regex على المحتوى الكامل
 *    - `searchDerivatives`: تقسيم بالمسافات + regex لكل كلمة + تحقّق من الأحرف المسموحة
 *
 * هذا يعكس الطريقة التي يعمل بها Python: فلترة بالـ SQL ثمّ regex بـ Python على المحتوى.
 */
class BahthiaSearcher(
    indexDir: Path,
    private val analyzer: Analyzer = SimpleArabicAnalyzer(),
    private val symbolsMap: MorphologicalSymbolsMap = MorphologicalSymbolsMap.load(),
) : Closeable {

    private val logger = LoggerFactory.getLogger(BahthiaSearcher::class.java)
    private val directory = FSDirectory.open(indexDir)
    private val reader = DirectoryReader.open(directory)
    private val searcher = IndexSearcher(reader)

    /**
     * تَتبّع تقدّم الفحص — يُستدعى دورياً (كلّ ٥٠ مستنداً) أثناء البحث.
     * يَستعمله الـ SearchViewModel لعَرض شريط التقدّم في الواجهة.
     * تَعيين القيمة لـ `null` يُعطّل الـ callback.
     */
    @Volatile var onScanProgress: ((scanned: Int, total: Int) -> Unit)? = null

    /**
     * يُحدّد أكبر عدد مستندات يُمكن أن نَفحصها بناءً على إعدادات المستخدم.
     *
     * يَحترم `options.limit`:
     *   - إن كان `Int.MAX_VALUE` (يَعني "دون حدّ" في الإعدادات) → نَفحص كلّ شيء.
     *   - وإلّا → نَفحص ما يَكفي لإيجاد الـ limit المطلوب (نَأخذ هامشاً ١٠ أضعاف
     *     لأنّ regex قد يَستبعد بعضها بعد الفلترة الميتاداتيّة).
     */
    private fun candidateScanCap(options: SearchOptions): Int {
        if (options.limit == Int.MAX_VALUE) return Int.MAX_VALUE
        val total = reader.numDocs()
        val want = options.limit.toLong() * 10L
        return if (want >= total.toLong() || want > Int.MAX_VALUE) total
               else want.toInt().coerceAtLeast(100)
    }

    init {
        logger.info("BahthiaSearcher opened: {} documents indexed", reader.numDocs())
    }

    /** نقطة دخول موحَّدة. */
    fun search(
        query: String,
        mode: SearchMode = SearchMode.WORD,
        options: SearchOptions = SearchOptions(),
    ): List<SearchResult> = when (mode) {
        SearchMode.WORD        -> searchWord(query, options)
        SearchMode.PATTERN     -> searchPattern(query, options)
        SearchMode.DERIVATIVES -> searchDerivatives(query, options)
        SearchMode.REGEX       -> searchRegex(query, options)
    }

    // ----------------------------------------------------------------
    // أنواع البحث
    // ----------------------------------------------------------------

    /**
     * بحث كلمة/جملة. يَدعم:
     *   - كلمة مفردة
     *   - جملة مطابقة بحرفها (تَتجاهل علامات الترقيم بين الكلمات)
     *   - `كلمة1 | كلمة2` ⇒ تقارب (خمس كلمات بينهما، أيّ ترتيب)
     *   - `كلمة1 + كلمة2` ⇒ كلتاهما في الصفحة بأيّ موضع وأيّ ترتيب
     */
    fun searchWord(query: String, options: SearchOptions = SearchOptions()): List<SearchResult> {
        // مسار خاصّ: AND على مستوى الصفحة
        if ('+' in query) {
            val subPatterns = MultiWordQueryBuilder.buildAndPatterns(
                query = query,
                respectDiacritics = options.respectDiacritics,
                matchWholeLetters = options.matchWholeLetters,
            )
            if (subPatterns.size < 2) return emptyList()
            val compiled = subPatterns.mapNotNull { compileRegexSafely(it) }
            if (compiled.size != subPatterns.size) return emptyList()
            return runPageWideAnd(compiled, query, options)
        }

        // المسار العامّ: كلمة/جملة/تقارب
        val pattern = MultiWordQueryBuilder.build(
            query = query,
            respectDiacritics = options.respectDiacritics,
            matchWholeLetters = options.matchWholeLetters,
        )
        if (pattern.isEmpty()) return emptyList()
        val regex = compileRegexSafely(pattern) ?: return emptyList()
        return runFullContentRegex(regex, query, options)
    }

    /** بحث الوزن الصرفي عبر Map.db. */
    fun searchPattern(wazn: String, options: SearchOptions = SearchOptions()): List<SearchResult> {
        if (wazn.isBlank()) return emptyList()
        val pattern = WaznPatternBuilder.build(
            wazn = wazn,
            symbolsMap = symbolsMap,
            respectDiacritics = options.respectDiacritics,
            matchWholeLetters = options.matchWholeLetters,
        )
        val regex = compileRegexSafely(pattern) ?: return emptyList()
        return runFullContentRegex(regex, wazn, options)
    }

    /**
     * بحث الجذر والمشتقّات (سـألتمونيها).
     * نفس منطق Python: تقسيم بالمسافات + regex لكل كلمة + تحقّق من allowed_chars.
     */
    fun searchDerivatives(
        root: String,
        options: SearchOptions = SearchOptions(),
        enableVowelProcessing: Boolean = true,
        minLength: Int = 3,
        maxLength: Int = 20,
    ): List<SearchResult> {
        if (root.isBlank()) return emptyList()
        val pattern = DerivativesPatternBuilder.buildRootPattern(root, enableVowelProcessing)
        val regex = compileRegexSafely(pattern) ?: return emptyList()
        val allowedChars = DerivativesPatternBuilder.allowedCharsFor(root, enableVowelProcessing)

        return runDerivativesScan(
            wordRegex = regex,
            allowedChars = allowedChars,
            minLength = minLength,
            maxLength = maxLength,
            originalQuery = root,
            options = options,
        )
    }

    /**
     * بحث Regex خام (يكتبه المستخدم نفسه).
     *
     * مطابق لـ Python `search_regex` في `db/sharded_database.py:900+`:
     *   - يستعمل `compileUserRegex` (يفرض MULTILINE، يمنع DOTALL)
     *   - يطبّق `prepare_text_for_regex` على المحتوى قبل البحث
     *   - يُرجع كلّ المطابقات (`finditer`)
     */
    fun searchRegex(pattern: String, options: SearchOptions = SearchOptions()): List<SearchResult> {
        if (pattern.isBlank()) return emptyList()
        val regex = try {
            com.bahthia.search.normalization.RegexTextPreparation.compileUserRegex(pattern)
        } catch (e: Exception) {
            logger.warn("Invalid user regex: {}", e.message)
            return emptyList()
        }
        return runFullContentRegex(regex, pattern, options, prepareText = true)
    }

    // ----------------------------------------------------------------
    // التنفيذ الداخلي
    // ----------------------------------------------------------------

    /**
     * مطابقة **واحدة** لكل صفحة (الأولى فقط) — مطابق لـ Python `find_first_match`.
     * يُستعمل في `searchWord`.
     */
    private fun runFirstMatchPerDoc(
        regex: Regex,
        originalQuery: String,
        options: SearchOptions,
    ): List<SearchResult> {
        val docs = fetchCandidateDocs(options)
        val results = mutableListOf<SearchResult>()
        for (scoreDoc in docs) {
            if (results.size >= options.limit) break
            val doc = searcher.storedFields().document(scoreDoc.doc)
            val content = doc.get("content") ?: continue
            val match = regex.find(content) ?: continue
            results += docToResult(
                doc = doc,
                score = scoreDoc.score,
                matchedTerm = match.value,
                matchPosition = match.range.first,
                snippet = buildSnippetAt(content, match.range.first, match.range.last + 1),
            )
        }
        return results
    }

    /**
     * يُشغّل regex على المحتوى الكامل لكل مستند مُرشَّح.
     * يُرجع كل المطابقات (مطابق لـ Python `compiled.finditer` في `search_pattern` و `search_regex`).
     */
    private fun runFullContentRegex(
        regex: Regex,
        originalQuery: String,
        options: SearchOptions,
        prepareText: Boolean = false,
    ): List<SearchResult> {
        val docs = fetchCandidateDocs(options)
        val results = mutableListOf<SearchResult>()
        val total = docs.size
        onScanProgress?.invoke(0, total)

        for ((i, scoreDoc) in docs.withIndex()) {
            if (i % 50 == 0 && i > 0) onScanProgress?.invoke(i, total)
            if (results.size >= options.limit) break
            val doc = searcher.storedFields().document(scoreDoc.doc)
            val rawContent = doc.get("content") ?: continue
            val content = if (prepareText)
                com.bahthia.search.normalization.RegexTextPreparation.prepare(rawContent)
            else rawContent

            for (m in regex.findAll(content)) {
                if (results.size >= options.limit) break
                results += docToResult(
                    doc = doc,
                    score = scoreDoc.score,
                    matchedTerm = m.value,
                    matchPosition = m.range.first,
                    snippet = buildSnippetAt(content, m.range.first, m.range.last + 1),
                )
            }
        }
        onScanProgress?.invoke(total, total)
        return results
    }

    /**
     * AND على مستوى الصفحة — كلّ الأنماط يجب أن يَظهر منها واحد على الأقلّ
     * في محتوى الصفحة. يُعيد نتيجة واحدة لكلّ صفحة مطابقة (snippet من أوّل مطابقة).
     */
    private fun runPageWideAnd(
        regexes: List<Regex>,
        originalQuery: String,
        options: SearchOptions,
    ): List<SearchResult> {
        val docs = fetchCandidateDocs(options)
        val results = mutableListOf<SearchResult>()
        for (scoreDoc in docs) {
            if (results.size >= options.limit) break
            val doc = searcher.storedFields().document(scoreDoc.doc)
            val content = doc.get("content") ?: continue

            // كلّ الأنماط يجب أن يُطابق
            val firstMatches = regexes.map { it.find(content) }
            if (firstMatches.any { it == null }) continue

            // الـ snippet نَأخذه حول أوّل مطابقة (الأبكر موضعاً في النصّ)
            val earliest = firstMatches.filterNotNull().minByOrNull { it.range.first }!!
            results += docToResult(
                doc = doc,
                score = scoreDoc.score,
                matchedTerm = earliest.value,
                matchPosition = earliest.range.first,
                snippet = buildSnippetAt(content, earliest.range.first, earliest.range.last + 1),
            )
        }
        return results
    }

    /**
     * مسح المشتقّات: لكلّ مستند، قسّم بالمسافات وافحص كل كلمة على حدة.
     * منطق Python في `search_derivatives`.
     */
    private fun runDerivativesScan(
        wordRegex: Regex,
        allowedChars: Set<Char>,
        minLength: Int,
        maxLength: Int,
        originalQuery: String,
        options: SearchOptions,
    ): List<SearchResult> {
        val docs = fetchCandidateDocs(options)
        // تجميع: word → (count, first occurrence)
        data class FoundEntry(val resultBuilder: () -> SearchResult, var count: Int)
        val foundWords = LinkedHashMap<String, FoundEntry>()

        for (scoreDoc in docs) {
            if (foundWords.size >= options.limit) break
            val doc = searcher.storedFields().document(scoreDoc.doc)
            val content = doc.get("content") ?: continue

            var pos = 0
            for (rawWord in content.split(Regex("\\s+"))) {
                val wordStart = content.indexOf(rawWord, pos).coerceAtLeast(0)
                pos = wordStart + rawWord.length
                if (rawWord.isEmpty()) continue
                val cleaned = BahthiaArabicNormalizer.removeDiacritics(rawWord)
                if (cleaned.length < minLength || cleaned.length > maxLength) continue
                if (cleaned.any { it !in allowedChars }) continue
                if (!wordRegex.containsMatchIn(cleaned)) continue

                val existing = foundWords[rawWord]
                if (existing != null) {
                    existing.count++
                } else {
                    val capturedDoc = doc
                    val capturedScore = scoreDoc.score
                    val capturedPos = wordStart
                    val capturedSnippet = buildSnippetAt(content, wordStart, wordStart + rawWord.length)
                    foundWords[rawWord] = FoundEntry(
                        resultBuilder = {
                            docToResult(
                                doc = capturedDoc,
                                score = capturedScore,
                                matchedTerm = rawWord,
                                matchPosition = capturedPos,
                                snippet = capturedSnippet,
                            )
                        },
                        count = 1,
                    )
                }
            }
        }

        // ترتيب حسب التكرار (مثل Python)
        return foundWords.values
            .sortedByDescending { it.count }
            .take(options.limit)
            .map { it.resultBuilder() }
    }

    // ----------------------------------------------------------------
    // قراءة المحتوى — للعرض الكامل خارج البحث
    // ----------------------------------------------------------------

    /** ملخّص بيانات كتاب — ما يتوفّر في فهرس Lucene فقط. */
    data class BookSummary(
        val bookId: Long,
        val title: String?,
        val author: String?,
        val category: String?,
        val year: String?,
        val pagesCount: Int,
    )

    /** يُرجع صفحة واحدة بمعرّف الكتاب ورقم الصفحة الداخلي. */
    fun getPage(bookId: Long, pageNumber: Int): Page? {
        val q = BooleanQuery.Builder()
            .add(org.apache.lucene.document.LongPoint.newExactQuery("book_id", bookId), BooleanClause.Occur.MUST)
            .build()
        val docs = searcher.search(q, reader.numDocs().coerceAtLeast(1)).scoreDocs
        for (sd in docs) {
            val doc = searcher.storedFields().document(sd.doc)
            val pn = doc.get("page_num")?.toIntOrNull() ?: continue
            if (pn != pageNumber) continue
            return Page(
                bookId = bookId,
                pageNumber = pn,
                originalPageNumber = doc.get("original_page_number"),
                content = doc.get("content") ?: "",
            )
        }
        return null
    }

    /** يُرجع كلّ صفحات كتاب مرتّبة بـ pageNumber تصاعدياً. */
    fun getBookPages(bookId: Long): List<Page> {
        val q = org.apache.lucene.document.LongPoint.newExactQuery("book_id", bookId)
        val docs = searcher.search(q, reader.numDocs().coerceAtLeast(1)).scoreDocs
        val pages = ArrayList<Page>(docs.size)
        for (sd in docs) {
            val doc = searcher.storedFields().document(sd.doc)
            val pn = doc.get("page_num")?.toIntOrNull() ?: continue
            pages += Page(
                bookId = bookId,
                pageNumber = pn,
                originalPageNumber = doc.get("original_page_number"),
                content = doc.get("content") ?: "",
            )
        }
        return pages.sortedBy { it.pageNumber }
    }

    /** بطاقة كتاب — العنوان والمؤلّف والفنّ والسنة + عدد الصفحات. */
    fun getBookSummary(bookId: Long): BookSummary? {
        val q = org.apache.lucene.document.LongPoint.newExactQuery("book_id", bookId)
        val docs = searcher.search(q, reader.numDocs().coerceAtLeast(1)).scoreDocs
        if (docs.isEmpty()) return null
        val first = searcher.storedFields().document(docs[0].doc)
        return BookSummary(
            bookId = bookId,
            title = first.get("book_title"),
            author = first.get("author"),
            category = first.get("category"),
            year = first.get("year"),
            pagesCount = docs.size,
        )
    }

    private fun fetchCandidateDocs(options: SearchOptions): Array<ScoreDoc> {
        val baseQuery: Query = MatchAllDocsQuery()
        val filtered = applyFilters(baseQuery, options)
        val cap = candidateScanCap(options)
        // Lucene يَطلب رقماً موجباً لـ topN؛ لو كان الفهرس فارغاً نُعيد فارغاً مباشرة
        val effective = cap.coerceAtLeast(reader.numDocs().coerceAtLeast(1))
        return searcher.search(filtered, effective).scoreDocs
    }

    private fun applyFilters(baseQuery: Query, options: SearchOptions): Query {
        val needsFilter = !options.categories.isNullOrEmpty()
            || !options.subcategories.isNullOrEmpty()
            || !options.bookIds.isNullOrEmpty()
            || !options.years.isNullOrEmpty()
            || !options.countries.isNullOrEmpty()
        if (!needsFilter) return baseQuery

        val builder = BooleanQuery.Builder()
        builder.add(baseQuery, BooleanClause.Occur.MUST)

        options.categories?.takeIf { it.isNotEmpty() }?.let { cats ->
            val sub = BooleanQuery.Builder()
            for (c in cats) {
                if (c in UNCLASSIFIED_CATEGORY_NAMES) {
                    sub.add(TermQuery(Term("category", c)), BooleanClause.Occur.SHOULD)
                    sub.add(
                        BooleanQuery.Builder()
                            .add(MatchAllDocsQuery(), BooleanClause.Occur.MUST)
                            .add(FieldExistsQuery("category_sort"), BooleanClause.Occur.MUST_NOT)
                            .build(),
                        BooleanClause.Occur.SHOULD,
                    )
                } else {
                    sub.add(TermQuery(Term("category", c)), BooleanClause.Occur.SHOULD)
                }
            }
            builder.add(sub.build(), BooleanClause.Occur.MUST)
        }
        options.subcategories?.takeIf { it.isNotEmpty() }?.let { subs ->
            val sub = BooleanQuery.Builder()
            for (s in subs) sub.add(TermQuery(Term("subcategory", s)), BooleanClause.Occur.SHOULD)
            builder.add(sub.build(), BooleanClause.Occur.MUST)
        }
        options.bookIds?.takeIf { it.isNotEmpty() }?.let { ids ->
            val sub = BooleanQuery.Builder()
            for (id in ids) sub.add(
                org.apache.lucene.document.LongPoint.newExactQuery("book_id", id),
                BooleanClause.Occur.SHOULD,
            )
            builder.add(sub.build(), BooleanClause.Occur.MUST)
        }
        options.years?.takeIf { it.isNotEmpty() }?.let { years ->
            // الحَقل الزَّمنيّ يَختلف وفقَ معيار البَحث الزَّمنيّ في الإعدادات
            val yearField = when (options.timeMode) {
                com.bahthia.domain.TimeMode.DEATH_YEAR -> "death_year"
                com.bahthia.domain.TimeMode.USAGE_DATE -> "year"
            }
            val sub = BooleanQuery.Builder()
            for (y in years) sub.add(TermQuery(Term(yearField, y)), BooleanClause.Occur.SHOULD)
            builder.add(sub.build(), BooleanClause.Occur.MUST)
        }
        options.countries?.takeIf { it.isNotEmpty() }?.let { countries ->
            val sub = BooleanQuery.Builder()
            for (c in countries) sub.add(TermQuery(Term("country", c)), BooleanClause.Occur.SHOULD)
            builder.add(sub.build(), BooleanClause.Occur.MUST)
        }
        return builder.build()
    }

    private fun docToResult(
        doc: Document,
        score: Float,
        matchedTerm: String,
        matchPosition: Int,
        snippet: String,
    ): SearchResult = SearchResult(
        bookId = doc.get("book_id")?.toLongOrNull() ?: 0L,
        pageNumber = doc.get("page_num")?.toIntOrNull() ?: 0,
        originalPageNumber = doc.get("original_page_number"),
        matchedTerm = matchedTerm,
        matchPosition = matchPosition,
        contextSnippet = snippet,
        bookTitle = doc.get("book_title"),
        bookAuthor = doc.get("author"),
        bookCategory = doc.get("category"),
        bookYear = doc.get("year"),
        relevance = score,
    )

    private fun buildSnippetAt(content: String, start: Int, end: Int): String {
        // ١٠٠ حرف على كل جانب — مطابق لـ Python `search_regex` (سطر 980-981)
        val s = (start - 100).coerceAtLeast(0)
        val e = (end + 100).coerceAtMost(content.length)
        return content.substring(s, e)
    }

    private fun compileRegexSafely(pattern: String): Regex? = try {
        Regex(pattern)
    } catch (e: Exception) {
        logger.warn("Invalid regex: {} ({})", pattern, e.message)
        null
    }

    private companion object {
        val UNCLASSIFIED_CATEGORY_NAMES = setOf("غير مصنّف", "غير مصنف")
    }

    override fun close() {
        try { reader.close() } finally { directory.close() }
    }
}
