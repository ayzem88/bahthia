package com.bahthia.domain

/**
 * بيانات عامّة عن التطبيق — تُستعمل في شاشة "حول"، شاشة الترحيب،
 * وفي رسائل البريد الإلكتروني، وفي تقارير الأعطال.
 */
object AppMetadata {
    const val NAME           = "المكتبة البحثيّة"
    const val NAME_EN        = "Bahthia Library"
    const val VERSION        = "1.0.0"
    /** وَسم مَرحلة الإِصدار — يُعرض بَين قَوسَين بَعد الاسم في شَريط العُنوان وشاشة "حول". */
    const val RELEASE_LABEL  = "إصدار تجريبيّ"
    /** الاسم الكامِل لِلعَرض — يَجمع الاسم مع وَسم المَرحلة. */
    const val DISPLAY_NAME   = "$NAME ($RELEASE_LABEL)"
    const val AUTHOR         = "أيمن الطيّب بن نجي"
    const val AUTHOR_EN      = "Ayman Atieb Ben Nji"
    const val EMAIL          = "aymen.nji@gmail.com"
    const val WEBSITE        = "https://www.bahthia.com"
    const val UPDATE_FEED    = "https://www.bahthia.com/api/version.json"
    const val LICENSE        = "خالص لوجه الله تعالى — استعمال حرّ"
}
