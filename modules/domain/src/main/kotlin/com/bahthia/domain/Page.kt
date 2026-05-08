package com.bahthia.domain

/**
 * صفحة من كتاب.
 *
 * @property bookId               معرّف الكتاب الذي تنتمي إليه.
 * @property pageNumber           ترقيم تسلسلي داخلي (يبدأ من 1).
 * @property originalPageNumber   رقم الصفحة الأصلي إن وُجدت علامة `==== N ====`.
 *                                `null` يعني أنّ الصفحة قُسِّمت آلياً (٣٠٠ كلمة).
 * @property content              النصّ الكامل للصفحة (يُفهرس في Lucene).
 */
data class Page(
    val bookId: Long,
    val pageNumber: Int,
    val originalPageNumber: String? = null,
    val content: String,
)
