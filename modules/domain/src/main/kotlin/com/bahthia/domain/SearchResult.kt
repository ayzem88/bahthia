package com.bahthia.domain

/**
 * نتيجة بحث واحدة (مطابقة).
 *
 * تجمع بين موقع المطابقة (كتاب + صفحة) ومعلومات الكتاب لعرضها في الواجهة
 * دون استعلامات إضافية لاحقة.
 */
data class SearchResult(
    val bookId: Long,
    val pageNumber: Int,
    val originalPageNumber: String? = null,
    val matchedTerm: String,
    val matchPosition: Int = -1,
    val contextSnippet: String,
    /** بيانات وصفيّة من الكتاب — قد تكون شحيحة لو لم تُجمَّع كاملة وقت العرض. */
    val bookTitle: String? = null,
    val bookAuthor: String? = null,
    val bookCategory: String? = null,
    val bookYear: String? = null,
    /** درجة الأهمّيّة وفق Lucene BM25 — 0.0 إن لم تُحسَب. */
    val relevance: Float = 0f,
)

/**
 * نوع البحث المرغوب من المستخدم.
 *
 * هذه الأنواع مطابقة لما تقدّمه نسخة Python الحالية، ويجب أن يحفظ
 * الترحيل سلوكها ١٠٠٪ (انظر اختبارات التطابق في `tests/parity/`).
 */
enum class SearchMode {
    /** بحث كلمة مع تطبيع حروف عربيّة (الألف، الياء، التاء المربوطة). */
    WORD,

    /** بحث وزن صرفي يستهلك Map.db (مفعول، فاعل، استفعال…). */
    PATTERN,

    /** بحث جذر مع سـألتمونيها وتنويعات الهمزة/الواو/الياء. */
    DERIVATIVES,

    /** تعبير نمطي خام كما يكتبه المستخدم. */
    REGEX,
}

/**
 * خيارات البحث المشتركة بين كل الأنواع.
 */
data class SearchOptions(
    val respectDiacritics: Boolean = false,
    val matchWholeLetters: Boolean = false,
    val categories: List<String>? = null,
    val subcategories: List<String>? = null,
    val bookIds: List<Long>? = null,
    /**
     * فَلتَرة بِحَسَب المِحوَر الزَّمنيّ — الحَقل المُستهدَف يَتحدَّد بـ [timeMode]:
     *   - [TimeMode.DEATH_YEAR] → يُطابق `death_year` في الفَهرس
     *   - [TimeMode.USAGE_DATE] → يُطابق `year` في الفَهرس
     */
    val years: List<String>? = null,
    /** فلترة حسب دولة/إقليم المؤلّف (`country` المخزَّن في الفهرس). */
    val countries: List<String>? = null,
    val limit: Int = 1000,
    /** يُحدِّد على أيّ حَقلٍ تُطبَّق فَلتَرة [years]. الافتراضي سَنة الوَفاة. */
    val timeMode: TimeMode = TimeMode.DEATH_YEAR,
)
