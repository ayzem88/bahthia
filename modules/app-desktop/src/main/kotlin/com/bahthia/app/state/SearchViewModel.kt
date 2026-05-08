package com.bahthia.app.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.bahthia.domain.Page
import com.bahthia.domain.SearchMode
import com.bahthia.domain.SearchOptions
import com.bahthia.domain.SearchResult
import com.bahthia.search.BahthiaSearcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * حالة شاشة البحث الرئيسيّة. تجمع:
 *   - نصّ الاستعلام
 *   - نمط البحث (كلمة / وزن / جذر / regex)
 *   - الخيارات (تشكيل، حدود، فلترة)
 *   - النتائج
 *   - حالة التحميل
 *
 * تستعمل Compose State + Coroutines.
 */
class SearchViewModel(
    initialSearcher: BahthiaSearcher,
    private val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private var searcher: BahthiaSearcher = initialSearcher

    // ----- inputs -----
    var query by mutableStateOf("")
    var mode by mutableStateOf(SearchMode.WORD)
    var respectDiacritics by mutableStateOf(false)
    var matchWholeLetters by mutableStateOf(false)
    var selectedCategories by mutableStateOf<Set<String>>(emptySet())
    var selectedBookIds by mutableStateOf<Set<Long>>(emptySet())
    var selectedYears by mutableStateOf<Set<String>>(emptySet())
    var selectedCountries by mutableStateOf<Set<String>>(emptySet())
    var resultsLimit by mutableStateOf(1000)
    /** يُحدّد على أيّ حَقل تُطبَّق فَلتَرة [selectedYears] في البحث. */
    var timeMode by mutableStateOf(com.bahthia.domain.TimeMode.DEATH_YEAR)

    /** كم نتيجة تُعرض الآن في الجدول. تَكبر بزرّ "المجموعة التّالية". */
    var displayLimit by mutableStateOf(DEFAULT_PAGE_SIZE)
        private set

    /** عدّاد يَزيد كلّما طلب المستخدم تركيز شريط البحث (Ctrl+F). */
    var focusSearchSignal by mutableStateOf(0)
        private set

    /** عدّاد يَزيد كلّما طلب المستخدم فتح حوار التصدير (Ctrl+E). */
    var exportSignal by mutableStateOf(0)
        private set

    fun requestFocusSearch() { focusSearchSignal++ }
    fun requestExport() { exportSignal++ }

    // ----- outputs -----
    val results = mutableStateListOf<SearchResult>()
    var isSearching by mutableStateOf(false)
        private set
    var lastError by mutableStateOf<String?>(null)
        private set
    var lastDurationMs by mutableStateOf(0L)
        private set

    /** تقدّم الفحص ٠..١ أثناء البحث الجاري. ‎-1‎ يَعني لا بحث جارٍ. */
    var searchProgress by mutableStateOf(-1f)
        private set

    /** إجمالي الصفحات الجاري فحصها (للعَرض). */
    var searchTotalPages by mutableStateOf(0)
        private set

    // ----- selection (in results panel) -----
    var selectedResultIndex by mutableStateOf(-1)

    // ----- صفحة العرض (full-page mode) -----
    /** الصفحة التي تُعرض حالياً في منطقة العرض الكبيرة. */
    var displayedPage by mutableStateOf<Page?>(null)
        private set

    /** عدد صفحات الكتاب الذي ننتمي إليه — لتعطيل التنقّل عند الحدود. */
    var displayedBookPagesCount by mutableStateOf(0)
        private set

    /** عنوان الكتاب الجاري عرضه (للشريط العلوي للوضع التصفّح). */
    var displayedBookTitle by mutableStateOf<String?>(null)
        private set

    /** هل المستخدم في وضع "تصفّح" (تنقّل بين صفحات الكتاب) لا في وضع "نتيجة"؟ */
    var isBrowsingPages by mutableStateOf(false)
        private set

    /** يَزيد كلّما تَغيّرت الصفحة المعروضة — للـ TextViewer ليُمرّر تلقائياً. */
    var displayPageVersion by mutableStateOf(0)
        private set

    private var currentJob: Job? = null
    private var pageLoadJob: Job? = null

    /** ينفّذ بحثاً بناءً على الحالة الحالية. */
    fun runSearch() {
        currentJob?.cancel()
        val q = query.trim()
        if (q.isEmpty()) {
            results.clear()
            return
        }
        // تيليمتري — يَحترم تلقائياً preferences.telemetryEnabled (no-op لو معطّل)
        try {
            AppRuntime.telemetry.recordSearch(mode.name.lowercase())
        } catch (_: Throwable) { /* ignore — لا نُفشل البحث بسبب التيليمتري */ }

        isSearching = true
        lastError = null
        searchProgress = 0f
        searchTotalPages = 0
        // اربط callback التقدّم
        searcher.onScanProgress = { scanned, total ->
            coroutineScope.launch(Dispatchers.Main) {
                searchTotalPages = total
                searchProgress = if (total > 0) scanned.toFloat() / total else 0f
            }
        }
        currentJob = coroutineScope.launch {
            val started = System.currentTimeMillis()
            try {
                val opts = SearchOptions(
                    respectDiacritics = respectDiacritics,
                    matchWholeLetters = matchWholeLetters,
                    categories = selectedCategories.toList().takeIf { it.isNotEmpty() },
                    bookIds = selectedBookIds.toList().takeIf { it.isNotEmpty() },
                    years = selectedYears.toList().takeIf { it.isNotEmpty() },
                    countries = selectedCountries.toList().takeIf { it.isNotEmpty() },
                    limit = resultsLimit,
                    timeMode = timeMode,
                )
                val found = withContext(Dispatchers.IO) {
                    searcher.search(q, mode, opts)
                }
                withContext(Dispatchers.Main) {
                    results.clear()
                    results.addAll(found)
                    selectedResultIndex = if (found.isNotEmpty()) 0 else -1
                    displayLimit = DEFAULT_PAGE_SIZE.coerceAtMost(found.size).coerceAtLeast(0)
                    lastDurationMs = System.currentTimeMillis() - started
                    isSearching = false
                    isBrowsingPages = false
                    searchProgress = -1f
                    searcher.onScanProgress = null
                    if (found.isNotEmpty()) loadPageForCurrentResult()
                    else {
                        displayedPage = null
                        displayedBookTitle = null
                        displayedBookPagesCount = 0
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    lastError = e.message ?: "خطأ غير معروف"
                    isSearching = false
                    lastDurationMs = System.currentTimeMillis() - started
                    searchProgress = -1f
                    searcher.onScanProgress = null
                }
            }
        }
    }

    /** يجلب صفحة كاملة للعرض. لا يَفتح الفهرس من جديد. */
    fun fetchPage(bookId: Long, pageNumber: Int): Page? = try {
        searcher.getPage(bookId, pageNumber)
    } catch (e: Exception) { null }

    /** يجلب كلّ صفحات كتاب مرتّبة. */
    fun fetchBookPages(bookId: Long): List<Page> = try {
        searcher.getBookPages(bookId)
    } catch (e: Exception) { emptyList() }

    /** يجلب ملخّص كتاب (عنوان، مؤلّف، فنّ، سنة، عدد الصفحات). */
    fun fetchBookSummary(bookId: Long): BahthiaSearcher.BookSummary? = try {
        searcher.getBookSummary(bookId)
    } catch (e: Exception) { null }

    fun selectResult(index: Int) {
        if (index in results.indices) {
            selectedResultIndex = index
            isBrowsingPages = false
            loadPageForCurrentResult()
        }
    }

    /** يجلب الصفحة الكاملة للنتيجة المختارة ويُحدّث منطقة العرض. */
    private fun loadPageForCurrentResult() {
        val r = results.getOrNull(selectedResultIndex) ?: run {
            displayedPage = null
            displayedBookTitle = null
            displayedBookPagesCount = 0
            return
        }
        pageLoadJob?.cancel()
        pageLoadJob = coroutineScope.launch {
            val page = withContext(Dispatchers.IO) { fetchPage(r.bookId, r.pageNumber) }
            val summary = withContext(Dispatchers.IO) { fetchBookSummary(r.bookId) }
            withContext(Dispatchers.Main) {
                displayedPage = page
                displayedBookTitle = summary?.title ?: r.bookTitle
                displayedBookPagesCount = summary?.pagesCount ?: 0
                displayPageVersion++
            }
        }
    }

    /** ينتقل لصفحة جديدة (دلتا = +1 أو -1) داخل الكتاب الحاليّ. */
    fun navigateBookPage(delta: Int) {
        val current = displayedPage ?: return
        val target = current.pageNumber + delta
        if (target < 1) return
        if (displayedBookPagesCount > 0 && target > displayedBookPagesCount) return
        pageLoadJob?.cancel()
        pageLoadJob = coroutineScope.launch {
            val page = withContext(Dispatchers.IO) { fetchPage(current.bookId, target) }
            withContext(Dispatchers.Main) {
                if (page != null) {
                    displayedPage = page
                    isBrowsingPages = true
                    selectedResultIndex = -1   // إلغاء تحديد الصفّ — صرنا في وضع "تصفّح"
                    displayPageVersion++
                }
            }
        }
    }

    fun canGoToPreviousPage(): Boolean {
        val p = displayedPage ?: return false
        return p.pageNumber > 1
    }

    fun canGoToNextPage(): Boolean {
        val p = displayedPage ?: return false
        return displayedBookPagesCount == 0 || p.pageNumber < displayedBookPagesCount
    }

    /** يَكشف المجموعة التالية من النتائج (PAGE_SIZE إضافيّاً). */
    fun loadNextGroup() {
        val newLimit = (displayLimit + DEFAULT_PAGE_SIZE).coerceAtMost(results.size)
        if (newLimit > displayLimit) displayLimit = newLimit
    }

    /** يَكشف كلّ النتائج المتاحة. */
    fun loadAllResults() {
        if (displayLimit < results.size) displayLimit = results.size
    }

    /** هل هناك المزيد من النتائج وراء الـ displayLimit الحاليّ؟ */
    fun hasMoreToShow(): Boolean = displayLimit < results.size

    /** عدد النتائج المعروضة الآن للمستخدم. */
    fun visibleResultCount(): Int = displayLimit.coerceAtMost(results.size)

    fun nextResult() {
        if (results.isEmpty()) return
        val newIdx = (selectedResultIndex + 1).coerceAtMost(results.size - 1)
        if (newIdx != selectedResultIndex || isBrowsingPages) {
            selectedResultIndex = newIdx
            isBrowsingPages = false
            loadPageForCurrentResult()
        }
    }

    fun previousResult() {
        if (results.isEmpty()) return
        val newIdx = (selectedResultIndex - 1).coerceAtLeast(0)
        if (newIdx != selectedResultIndex || isBrowsingPages) {
            selectedResultIndex = newIdx
            isBrowsingPages = false
            loadPageForCurrentResult()
        }
    }

    fun reset() {
        query = ""
        results.clear()
        selectedResultIndex = -1
        displayedPage = null
        displayedBookTitle = null
        displayedBookPagesCount = 0
        isBrowsingPages = false
        lastError = null
    }

    fun closeSearcherForIndexMutation() {
        currentJob?.cancel()
        currentJob = null
        isSearching = false
        try {
            searcher.close()
        } catch (_: Exception) {
            // The caller is about to replace index files; closing twice is harmless here.
        }
    }

    fun reopenSearcher(newSearcher: BahthiaSearcher) {
        closeSearcherForIndexMutation()
        searcher = newSearcher
        reset()
    }

    fun close() {
        closeSearcherForIndexMutation()
        coroutineScope.cancel()
    }

    /**
     * يَلتقط الحالة الحاليّة كَجَلسة قابِلة للحِفظ ([SavedSearch]).
     * تُستعمَل من زرّ ★ في DisplayPanel.
     */
    fun snapshot(
        viewMode: LibraryViewModel.ViewMode,
        name: String? = null,
    ): SavedSearch {
        val now = System.currentTimeMillis()
        return SavedSearch(
            id = SavedSearchStore.newId(),
            timestamp = now,
            name = name ?: SavedSearchStore.defaultName(query, now),
            query = query,
            mode = mode,
            respectDiacritics = respectDiacritics,
            matchWholeLetters = matchWholeLetters,
            resultsLimit = resultsLimit,
            selectedCategories = selectedCategories,
            selectedYears = selectedYears,
            selectedCountries = selectedCountries,
            selectedBookIds = selectedBookIds,
            viewMode = viewMode,
            timeMode = timeMode,
            results = results.toList(),
        )
    }

    /**
     * يَستعيد حالة جَلسة مَحفوظة — يُحدِّث كلّ الحقول ويَملأ النتائج مُباشرةً
     * بدون إعادة بَحث (لِضمان نَفس النتائج حتى لو تَغيّر الفهرس).
     */
    fun restoreFromSnapshot(s: SavedSearch) {
        currentJob?.cancel()
        query = s.query
        mode = s.mode
        respectDiacritics = s.respectDiacritics
        matchWholeLetters = s.matchWholeLetters
        resultsLimit = s.resultsLimit
        selectedCategories = s.selectedCategories
        selectedYears = s.selectedYears
        selectedCountries = s.selectedCountries
        selectedBookIds = s.selectedBookIds
        timeMode = s.timeMode
        results.clear()
        results.addAll(s.results)
        selectedResultIndex = if (s.results.isNotEmpty()) 0 else -1
        displayLimit = DEFAULT_PAGE_SIZE.coerceAtMost(s.results.size).coerceAtLeast(0)
        lastError = null
        isSearching = false
        searchProgress = -1f
        // إن كانت هناك نَتيجة مُختارة، نُحمّل صَفحتها لِعَرضها فَوراً
        if (selectedResultIndex >= 0) selectResult(selectedResultIndex)
    }

    private companion object {
        /** حَجم الدُّفعة الافتراضيّ — ٥٠٠ نتيجة في كلّ مَجموعة. */
        const val DEFAULT_PAGE_SIZE = 500
    }
}
