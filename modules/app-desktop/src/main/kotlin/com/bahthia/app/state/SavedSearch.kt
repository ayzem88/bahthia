package com.bahthia.app.state

import com.bahthia.domain.SearchMode
import com.bahthia.domain.SearchResult
import com.bahthia.domain.TimeMode

/**
 * جَلسة بَحث مَحفوظة — تَلتقط حالة البَحث الكاملة + النتائج،
 * بِحيث يُمكن استرجاعها لاحقاً وعَرض نَفس النتائج بدون إعادة بَحث.
 *
 * تُخزَّن في `%APPDATA%\Bahthia\favorites\<id>.properties` بصيغة Java Properties.
 * انظر [SavedSearchStore].
 */
data class SavedSearch(
    /** مُعرّف فَريد ثابِت (UUID) — يُستعمل اسماً للملفّ. */
    val id: String,
    /** وَقت الحِفظ بـ epoch millis. */
    val timestamp: Long,
    /** اسم وَصفي للجَلسة — يُولَّد تَلقائياً، يُمكن تَعديله. */
    val name: String,

    // ─── حالة شريط البَحث ───
    val query: String,
    val mode: SearchMode,
    val respectDiacritics: Boolean,
    val matchWholeLetters: Boolean,
    val resultsLimit: Int,

    // ─── المُرشّحات (نَصّ خام كما يَختاره المُستخدم) ───
    val selectedCategories: Set<String>,
    val selectedYears: Set<String>,
    val selectedCountries: Set<String>,
    val selectedBookIds: Set<Long>,

    // ─── حالة عمود "الحَقل" (CATEGORIES / YEARS / REGIONS) ───
    val viewMode: LibraryViewModel.ViewMode,
    val timeMode: TimeMode,

    // ─── النتائج (لِضمان عَرض نَفس النتائج عند الاسترجاع) ───
    val results: List<SearchResult>,
)
