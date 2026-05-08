package com.bahthia.search.normalization

/**
 * تحضير النصّ للبحث بـ regex — مطابق لـ Python `shared/regex_utils.py`:
 *   - توحيد نهايات الأسطر إلى `\n`
 *   - حذف المحارف الخفيّة (BOM, ZWJ, ZWNJ, ZWS, RLM/LRM)
 *   - استبدال NBSP بمسافة عاديّة
 */
object RegexTextPreparation {

    /** يوحّد نهايات الأسطر إلى `\n`. مكافئ لـ `normalize_eols`. */
    fun normalizeEols(text: String): String {
        if (text.isEmpty()) return text
        return text
            .replace(" ", "\n")  // LINE SEPARATOR
            .replace(" ", "\n")  // PARAGRAPH SEPARATOR
            .replace("\r\n", "\n")
            .replace("\r", "\n")
    }

    /** يحذف المحارف الخفيّة. مكافئ لـ `clean_invisible_chars`. */
    fun cleanInvisibleChars(text: String, replaceNbspWithSpace: Boolean = true): String {
        if (text.isEmpty()) return text
        val sb = StringBuilder(text.length)
        for (c in text) {
            when (c) {
                '﻿' -> {} // BOM
                '​', '‌', '‍' -> {} // ZWS, ZWNJ, ZWJ
                '‎', '‏' -> {} // LRM, RLM
                ' ' -> if (replaceNbspWithSpace) sb.append(' ')
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    /** الإعداد الكامل للنصّ قبل البحث بـ regex. مكافئ لـ `prepare_text_for_regex`. */
    fun prepare(text: String): String = cleanInvisibleChars(normalizeEols(text))

    /**
     * تجميع regex من المستخدم مع فرض MULTILINE ومنع DOTALL.
     * مكافئ لـ `compile_user_regex` في Python.
     *
     * @throws IllegalArgumentException إن استعمل المستخدم `(?s:...)` أو `(?s)` صراحةً.
     */
    fun compileUserRegex(pattern: String): Regex {
        if (pattern.isEmpty()) return Regex("(?!x)x") // نمط لا يطابق شيئاً
        // رفض DOTALL inline
        if (Regex("\\(\\?s:|(?<!\\()\\?-?s[:\\)]").containsMatchIn(pattern)) {
            throw IllegalArgumentException("وضع DOTALL غير مسموح: يُمنع (?s:...) أو تبديل s داخل النمط.")
        }
        // تنظيف (?s) و (?-s) العامّة
        var p = pattern.replace("(?s)", "").replace("(?-s)", "")
        // فرض MULTILINE وإلغاء DOTALL
        return Regex("(?m)(?-s)$p")
    }
}
