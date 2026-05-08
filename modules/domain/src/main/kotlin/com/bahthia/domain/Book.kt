package com.bahthia.domain

/**
 * كتاب في المكتبة البحثيّة.
 *
 * تمثّل البيانات الميتاداتيّة المخزَّنة في SQLite. المحتوى الفعلي للصفحات
 * يُخزَّن في فهرس Lucene منفصل ويُستعلم عنه عبر [com.bahthia.search.BahthiaSearcher].
 */
data class Book(
    val id: Long,
    val title: String,
    val author: String? = null,
    val category: String? = null,
    val subcategory: String? = null,
    val year: String? = null,
    val deathYear: String? = null,
    val wordCount: Int? = null,
    val pagesCount: Int? = null,
    val publisher: String? = null,
    val country: String? = null,
    val edition: String? = null,
    val volumesCount: String? = null,
    val investigator: String? = null,
    val translator: String? = null,
    val sourceFile: String? = null,
    /**
     * حقول إضافيّة من الترويسات لم يكن لها عمود مخصّص.
     * مكافئ لـ `dynamic_fields` في النسخة Python.
     */
    val dynamicFields: Map<String, String> = emptyMap(),
)
