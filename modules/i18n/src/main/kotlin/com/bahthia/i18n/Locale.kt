package com.bahthia.i18n

/**
 * اللغات المدعومة في التطبيق.
 *
 * **العربيّة هي اللغة الأمّ** للنصوص — أيّ مفتاح غير موجود في لغة أخرى
 * يَرجع تلقائياً إلى النصّ العربيّ.
 */
enum class Locale(val code: String, val displayName: String, val rtl: Boolean) {
    AR("ar", "العربية",     rtl = true),
    EN("en", "English",      rtl = false),
    FR("fr", "Français",     rtl = false),
    DE("de", "Deutsch",      rtl = false),
    ES("es", "Español",      rtl = false),
    TR("tr", "Türkçe",       rtl = false),
    FA("fa", "فارسی",        rtl = true),
    UR("ur", "اردو",         rtl = true),
    MS("ms", "Bahasa Melayu", rtl = false),
    IT("it", "Italiano",     rtl = false),
    ZH("zh", "中文",          rtl = false),
    JA("ja", "日本語",        rtl = false),
    KO("ko", "한국어",        rtl = false),
    ;

    companion object {
        val DEFAULT = AR
        fun fromCode(code: String?): Locale =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: DEFAULT
    }
}
