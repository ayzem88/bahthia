package com.bahthia.app.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.bahthia.app.ui.screens.books.BookEntry
import com.bahthia.app.ui.screens.categories.CategoryEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

/**
 * يحمّل الفئات والكتب من فهرس Lucene الحقيقي،
 * ويُحدّث الواجهة عند التغيير.
 *
 * يدعم ٣ أوضاع عرض في العمود الأوّل (مطابقة لـ Python):
 *   - [ViewMode.CATEGORIES] — الفئات/الفنون
 *   - [ViewMode.YEARS]      — السنوات
 *   - [ViewMode.REGIONS]    — الدول/الأقاليم (دولة المؤلّف)
 *
 * زرّ التبديل في رأس CategoriesPanel يَدور بين الثلاثة.
 */
class LibraryViewModel(
    private val coroutineScope: CoroutineScope,
) {
    private val logger = LoggerFactory.getLogger(LibraryViewModel::class.java)

    /** أوضاع العرض في عمود الفئات. */
    enum class ViewMode { CATEGORIES, YEARS, REGIONS }

    val categories = mutableStateListOf<CategoryEntry>()
    val books = mutableStateListOf<BookEntry>()
    var totalBooks by mutableStateOf(0)
        private set
    var totalPages by mutableStateOf(0)
        private set
    /** عدد كلمات تقريبيّ في كلّ المكتبة. */
    var totalWords by mutableStateOf(0L)
        private set
    /** عدد المؤلّفين الفريدين. */
    var totalAuthors by mutableStateOf(0)
        private set
    var loaded by mutableStateOf(false)
        private set

    /**
     * تَقدُّم تَحميل المكتبة (٠..١) — يُستعمَل في شاشة البَدء (Splash) لِعَرض
     * شَريط تَقدُّم حَقيقيّ يَعكس عَدد المُستندات المَفحوصة من فَهرس Lucene.
     * `-1f` يَعني "بَعد لم يَبدأ".
     */
    var loadProgress by mutableStateOf(-1f)
        private set

    /** الإحصاء المحسوب مرّة واحدة من القرص — يُستعمَل للتَبديل الفوريّ بين الأوضاع. */
    private var cached: com.bahthia.search.Aggregate? = null

    /** الوضع الحاليّ للعمود الأوّل. */
    var viewMode by mutableStateOf(ViewMode.CATEGORIES)
        private set

    private data class LoadResult(
        val cats: List<CategoryEntry>,
        val bks: List<BookEntry>,
        val totBooks: Int,
        val totPages: Int,
    )

    /**
     * يَدور بين الأوضاع الثلاثة. لو وُجد cache في الذاكرة، التَبديل **فوريّ**
     * (لا قراءة من القرص). وإلا يُعاد التحميل.
     */
    fun cycleViewMode() {
        viewMode = when (viewMode) {
            ViewMode.CATEGORIES -> ViewMode.YEARS
            ViewMode.YEARS      -> ViewMode.REGIONS
            ViewMode.REGIONS    -> ViewMode.CATEGORIES
        }
        val snap = cached
        if (snap != null) applySnapshot(snap) else load()
    }

    /** يُبطِل الـ cache — يُستدعى بعد كلّ تَغيير في الفهرس (استيراد/حذف/استعادة/مسح). */
    fun invalidate() {
        cached = null
    }

    /**
     * يُبدّل الـ [viewMode] خارجيّاً (يُستعمَل عند استرجاع جَلسة مَحفوظة).
     * يَستفيد من الـ cache إن وُجد، وإلّا يُعيد التَحميل.
     */
    fun changeViewMode(mode: ViewMode) {
        if (viewMode == mode) return
        viewMode = mode
        val snap = cached
        if (snap != null) applySnapshot(snap) else load()
    }

    /**
     * يُطبّق التَحديدات المُسجَّلة في جَلسة مَحفوظة على القَوائم الحاليّة:
     * يُحدّد الفئات/السنوات/الدُّول والكتب التي كانت مُختارة سابِقاً.
     * يَستدعى بعد [setViewMode] لتَطابق نَوع التحديدات مع الـ viewMode الحاليّ.
     */
    fun applySelections(
        selectedCategoryNames: Set<String>,
        selectedBookIdsToApply: Set<Long>,
    ) {
        for (i in categories.indices) {
            categories[i] = categories[i].copy(
                selected = categories[i].name in selectedCategoryNames,
            )
        }
        for (i in books.indices) {
            books[i] = books[i].copy(
                selected = books[i].id in selectedBookIdsToApply,
            )
        }
    }

    /** عنوان الترويسة وفق الوضع الحاليّ — مُترجَم. */
    fun headerTitle(): String = when (viewMode) {
        ViewMode.CATEGORIES -> com.bahthia.i18n.tr("panel.categories.title")
        ViewMode.YEARS      -> com.bahthia.i18n.tr("panel.categories.title.years")
        ViewMode.REGIONS    -> com.bahthia.i18n.tr("panel.categories.title.regions")
    }

    /** الـ tooltip للزرّ. */
    fun cycleTooltip(): String = when (viewMode) {
        ViewMode.CATEGORIES -> com.bahthia.i18n.tr("panel.categories.cycle.toYears")
        ViewMode.YEARS      -> com.bahthia.i18n.tr("panel.categories.cycle.toRegions")
        ViewMode.REGIONS    -> com.bahthia.i18n.tr("panel.categories.cycle.toCategories")
    }

    fun load() {
        coroutineScope.launch {
            try {
                // مَعيار البَحث الزَّمنيّ يُحدِّد أيُّ حَقلٍ يُغذّي عَمود "الحَقل الزَّمنيّ"
                val timeMode = AppRuntime.preferences.timeMode
                loadProgress = 0f
                val agg = withContext(Dispatchers.IO) {
                    AppRuntime.openLibraryStats().use { stats ->
                        stats.aggregateAll(timeMode) { scanned, total ->
                            // التَحديث على Main thread لأنّ State من Compose
                            coroutineScope.launch(Dispatchers.Main) {
                                loadProgress = (scanned.toFloat() / total).coerceIn(0f, 1f)
                            }
                        }
                    }
                }
                cached = agg
                applySnapshot(agg)
                loadProgress = 1f
                logger.info(
                    "Library aggregated: {} books, {} pages, ~{} words, {} authors",
                    agg.totalBooks, agg.totalPages, agg.totalWordsEstimate, agg.authorCount,
                )
            } catch (e: Exception) {
                logger.error("Failed to load library stats", e)
                loadProgress = 1f  // نَمرُّ خَطأً صَامتاً، ولا نُجمِّد الـsplash
            }
        }
    }

    /**
     * يُطبِّق aggregate **مَحسوباً مُسبَقاً** (مَثَلاً من نِهاية الاستيراد) — فَوريّ
     * بِلا I/O. هذا يَجعل المكتبة جاهزة في الواجِهة الرّئيسيّة لَحظةَ إِغلاق حِوار
     * الاستيراد، بَدل تَحميلها بَعد ذلك.
     */
    fun applyPrecomputedAggregate(agg: com.bahthia.search.Aggregate) {
        cached = agg
        applySnapshot(agg)
        logger.info(
            "Library seeded from precomputed aggregate: {} books, {} pages",
            agg.totalBooks, agg.totalPages,
        )
    }

    /**
     * يُطبّق الـ Aggregate المحفوظ على الـ State الحاليّ بحسب [viewMode].
     * **فوريّ** — مجرّد تَبديل قوائم في الذاكرة لا I/O.
     */
    private fun applySnapshot(agg: com.bahthia.search.Aggregate) {
        val cats = when (viewMode) {
            ViewMode.CATEGORIES -> agg.byCategory.map { CategoryEntry(it.name, it.booksCount, false) }
            ViewMode.YEARS      -> agg.byYear.map { CategoryEntry(it.year, it.booksCount, false) }
            ViewMode.REGIONS    -> agg.byRegion.map { CategoryEntry(it.name, it.booksCount, false) }
        }
        val bks = agg.books.map {
            BookEntry(
                id = it.id,
                title = it.title,
                author = it.author,
                category = it.category,
                year = it.year,
                deathYear = it.deathYear,
                country = it.country,
                selected = false,
            )
        }
        categories.clear(); categories.addAll(cats)
        books.clear(); books.addAll(bks)
        totalBooks = agg.totalBooks
        totalPages = agg.totalPages
        totalWords = agg.totalWordsEstimate
        totalAuthors = agg.authorCount
        loaded = true
    }

    fun toggleCategory(name: String) {
        val idx = categories.indexOfFirst { it.name == name }
        if (idx >= 0) categories[idx] = categories[idx].copy(selected = !categories[idx].selected)
    }

    fun selectAllCategoriesAction() {
        for (i in categories.indices) categories[i] = categories[i].copy(selected = true)
    }

    fun clearAllCategoriesAction() {
        for (i in categories.indices) categories[i] = categories[i].copy(selected = false)
    }

    /**
     * الكتب التي يجب عرضها في عمود "الكتب" بناءً على الاختيار في عمود الفئات.
     *
     * القواعد (مطلوبة من المستخدم):
     *   - إن لم يَختَر شيئاً → لا تُعرض أيّ كتب (العمود فارغ)
     *   - إن اختار "جميع الكتب" → تُعرض كلّ الكتب
     *   - إن اختار فئة/سنة/دولة محدَّدة → تُعرض كتب ذلك الاختيار فقط
     */
    fun visibleBooks(): List<BookEntry> {
        val selected = categories.filter { it.selected }
        if (selected.isEmpty()) return emptyList()

        // "جميع الكتب" مفعّل = كلّ شيء (فقط في وضع CATEGORIES)
        if (selected.any { it.name == ALL_BOOKS_SENTINEL }) return books

        val rawTargets = selected.map { it.name }.toSet()
        val unclassifiedSelected = UNCLASSIFIED_SENTINEL in rawTargets
        val realTargets = rawTargets - UNCLASSIFIED_SENTINEL

        // مُساعِد: قِيمة الحَقل تُعتبر "غير مُصنَّفة" إذا فَرَغت أو كانت "0000"
        fun isBlank(v: String?) = v.isNullOrBlank() || v == "0000"

        return when (viewMode) {
            ViewMode.CATEGORIES -> books.filter { b ->
                (unclassifiedSelected && isBlank(b.category)) || (b.category in realTargets)
            }
            ViewMode.YEARS -> {
                val timeMode = AppRuntime.preferences.timeMode
                books.filter { b ->
                    val v = when (timeMode) {
                        com.bahthia.domain.TimeMode.DEATH_YEAR -> b.deathYear
                        com.bahthia.domain.TimeMode.USAGE_DATE -> b.year
                    }
                    (unclassifiedSelected && isBlank(v)) || (!isBlank(v) && v in realTargets)
                }
            }
            ViewMode.REGIONS -> books.filter { b ->
                (unclassifiedSelected && isBlank(b.country)) || (b.country in realTargets)
            }
        }
    }

    fun toggleBook(id: Long) {
        val idx = books.indexOfFirst { it.id == id }
        if (idx >= 0) books[idx] = books[idx].copy(selected = !books[idx].selected)
    }

    /** يُحدّد كلّ الكتب المرئيّة الآن (الفلترة الحاليّة). */
    fun selectAllBooksAction() {
        val visibleIds = visibleBooks().map { it.id }.toSet()
        for (i in books.indices) {
            if (books[i].id in visibleIds) books[i] = books[i].copy(selected = true)
        }
    }

    /** يُلغي تحديد كلّ الكتب المرئيّة الآن. */
    fun clearAllBooksAction() {
        val visibleIds = visibleBooks().map { it.id }.toSet()
        for (i in books.indices) {
            if (books[i].id in visibleIds) books[i] = books[i].copy(selected = false)
        }
    }

    /**
     * الفئات المختارة لتمريرها لمحرّك البحث (وضع CATEGORIES فقط).
     *
     * في أوضاع YEARS و REGIONS، الإدخالات تَملأ الحقول الأخرى — ترى
     * [effectiveSelectedYears] و [effectiveSelectedCountries].
     */
    fun effectiveSelectedCategories(): Set<String> {
        if (viewMode != ViewMode.CATEGORIES) return emptySet()
        if (categories.any { it.name == ALL_BOOKS_SENTINEL && it.selected }) return emptySet()
        val realCategories = categories
            .map { it.name }
            .filterNot { it == ALL_BOOKS_SENTINEL }
            .toSet()
        val selected = categories
            .filter { it.selected }
            .map { it.name }
            .filterNot { it == ALL_BOOKS_SENTINEL }
            // مَفتاح "غير مصنّف" الداخلي يُمرَّر للمحرّك بنصّه الحقيقي في Lucene
            .map { if (it == UNCLASSIFIED_SENTINEL) UNCLASSIFIED_REAL else it }
            .toSet()
        return if (selected.isEmpty() || selected == realCategories) emptySet() else selected
    }

    /**
     * السنوات المختارة (وضع YEARS فقط) — تُمرَّر إلى محرّك البحث.
     * يُستبعَد [UNCLASSIFIED_SENTINEL] لأنّه لا قيمةَ مَخزّنة في Lucene تُطابقه؛
     * المستخدم الذي يَريد فلتَرة كُتب بدون سنة يَختارها يدويّاً من عمود "الكتب".
     */
    fun effectiveSelectedYears(): Set<String> {
        if (viewMode != ViewMode.YEARS) return emptySet()
        return categories.filter { it.selected }
            .map { it.name }
            .filterNot { it == UNCLASSIFIED_SENTINEL }
            .toSet()
    }

    /**
     * الدول/الأقاليم المختارة (وضع REGIONS فقط) — تُمرَّر إلى محرّك البحث.
     * يُستبعَد [UNCLASSIFIED_SENTINEL] (انظر التَوضيح أعلاه).
     */
    fun effectiveSelectedCountries(): Set<String> {
        if (viewMode != ViewMode.REGIONS) return emptySet()
        return categories.filter { it.selected }
            .map { it.name }
            .filterNot { it == UNCLASSIFIED_SENTINEL }
            .toSet()
    }

    fun effectiveSelectedBookIds(): Set<Long> {
        val allIds = books.map { it.id }.toSet()
        val selected = books.filter { it.selected }.map { it.id }.toSet()
        return if (selected.isEmpty() || selected == allIds) emptySet() else selected
    }

    private companion object {
        // متطابق مع LibraryStats.ALL_BOOKS_SENTINEL — سنتينل داخلي لا يُعرَض للمستخدم.
        const val ALL_BOOKS_SENTINEL = "__ALL_BOOKS__"
        const val UNCLASSIFIED_SENTINEL = "__UNCLASSIFIED__"
        // النصّ الحقيقيّ لقيمة "غير مصنّف" المخزَّنة في الفهرس (للتمرير لـ Searcher).
        const val UNCLASSIFIED_REAL = "غير مصنّف"
    }
}
