package com.bahthia.domain

/**
 * مَعيار البَحث الزَّمني — يُحدِّد أيُّ حَقلٍ يُمَثِّل المِحوَر الزَّمنيّ
 * في عَمود "الحَقل الزَّمني" وفي فَلتَرة البَحث بالسَّنة.
 *
 *   - [DEATH_YEAR] — سَنة وَفاة المُؤلِّف (`death_year` في الفَهرس)
 *   - [USAGE_DATE] — تاريخ الاستِعمال أو النَشر (`year` في الفَهرس)
 */
enum class TimeMode { DEATH_YEAR, USAGE_DATE }
