package com.bahthia.lifecycle

import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

/**
 * مُتحقّق من التحديثات.
 *
 * يجلب JSON من [feedUrl] بصيغة:
 * ```json
 * {
 *   "version": "0.2.0",
 *   "release_date": "2026-06-01",
 *   "download_url": "https://www.bahthia.com/download/bahthia-0.2.0.msi",
 *   "notes": "تحسينات في البحث بالجذر..."
 * }
 * ```
 * ثمّ يقارن بالإصدار الحالي [currentVersion] (SemVer) ويُرجع [UpdateInfo]
 * إن كان ثمّة إصدار أحدث، أو `null` إن كان المستعمل على آخر إصدار.
 *
 * يَفشل بصمت إن لم يكن الإنترنت متاحاً (يُرجع `null` ويُسجّل تحذيراً).
 *
 * @param feedUrl        رابط ملفّ JSON على خادم bahthia.com
 * @param currentVersion إصدار التطبيق الحالي ("0.1.0")
 * @param timeoutMs      المهلة الزمنيّة (افتراضياً ٥ ثوانٍ)
 */
class AutoUpdater(
    private val feedUrl: String,
    private val currentVersion: String,
    private val timeoutMs: Int = 5000,
) {

    private val logger = LoggerFactory.getLogger(AutoUpdater::class.java)

    data class UpdateInfo(
        val version: String,
        val releaseDate: String,
        val downloadUrl: String,
        val notes: String,
    )

    /** يُرجع `UpdateInfo` إن كان ثمّة تحديث، أو `null`. */
    fun checkForUpdates(): UpdateInfo? {
        val json = fetch() ?: return null
        return parseAndCompare(json)
    }

    /** نسخة قابلة للاختبار: تأخذ الـ JSON مباشرةً. */
    internal fun parseAndCompare(json: String): UpdateInfo? {
        val info = parseJson(json) ?: return null
        return if (compareVersions(info.version, currentVersion) > 0) info else null
    }

    private fun fetch(): String? {
        return try {
            val conn = (URL(feedUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "Bahthia-Library/$currentVersion")
            }
            if (conn.responseCode !in 200..299) {
                logger.warn("Update feed returned HTTP {}", conn.responseCode)
                return null
            }
            BufferedReader(InputStreamReader(conn.inputStream, StandardCharsets.UTF_8))
                .use { it.readText() }
        } catch (e: Exception) {
            logger.warn("Failed to fetch update feed: {}", e.message)
            null
        }
    }

    companion object {
        /**
         * يقارن إصدارَين بصيغة SemVer ("a.b.c") ويعود:
         * - موجباً إن كان [a] أحدث من [b]
         * - سالباً إن كان أقدم
         * - صفراً إن متساويَين
         *
         * يتسامح مع الإصدارات ذات الجزأين فقط ("0.2") ويُكمِلها بأصفار.
         */
        fun compareVersions(a: String, b: String): Int {
            val pa = parts(a)
            val pb = parts(b)
            val n = maxOf(pa.size, pb.size)
            for (i in 0 until n) {
                val xa = pa.getOrElse(i) { 0 }
                val xb = pb.getOrElse(i) { 0 }
                if (xa != xb) return xa - xb
            }
            return 0
        }

        private fun parts(v: String): List<Int> =
            v.trim().removePrefix("v").split('.', '-').mapNotNull { it.toIntOrNull() }

        /** يُحلّل JSON مسطّحاً للحصول على حقول التحديث. */
        internal fun parseJson(s: String): UpdateInfo? {
            val map = NaiveJson.parse(s) ?: return null
            val version = map["version"] as? String ?: return null
            return UpdateInfo(
                version = version,
                releaseDate = map["release_date"] as? String ?: "",
                downloadUrl = map["download_url"] as? String ?: "",
                notes = map["notes"] as? String ?: "",
            )
        }
    }
}

/** مُحلّل JSON مُبسَّط — يكفي لرسائل التحديث المسطّحة. */
internal object NaiveJson {
    fun parse(s: String): Map<String, Any?>? {
        val trimmed = s.trim()
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) return null
        return runCatching { naiveParse(trimmed) }.getOrNull()
    }

    private fun naiveParse(s: String): Map<String, Any?> {
        val out = mutableMapOf<String, Any?>()
        var i = 1
        while (i < s.length) {
            while (i < s.length && s[i].isWhitespace()) i++
            if (i >= s.length || s[i] == '}') break
            if (s[i] == ',') { i++; continue }

            // المفتاح
            require(s[i] == '"') { "expected '\"' at $i" }
            val keyEnd = s.indexOf('"', i + 1)
            require(keyEnd > 0) { "unterminated key" }
            val key = s.substring(i + 1, keyEnd)
            i = keyEnd + 1

            // النقطتان
            while (i < s.length && s[i] != ':') i++
            i++
            while (i < s.length && s[i].isWhitespace()) i++

            // القيمة
            require(i < s.length) { "missing value" }
            when (s[i]) {
                '"' -> {
                    val sb = StringBuilder()
                    i++
                    while (i < s.length && s[i] != '"') {
                        if (s[i] == '\\' && i + 1 < s.length) {
                            when (s[i + 1]) {
                                'n' -> sb.append('\n')
                                't' -> sb.append('\t')
                                'r' -> sb.append('\r')
                                '\\' -> sb.append('\\')
                                '"' -> sb.append('"')
                                '/' -> sb.append('/')
                                else -> sb.append(s[i + 1])
                            }
                            i += 2
                        } else {
                            sb.append(s[i]); i++
                        }
                    }
                    out[key] = sb.toString()
                    i++
                }
                't' -> { out[key] = true; i += 4 }
                'f' -> { out[key] = false; i += 5 }
                'n' -> { out[key] = null; i += 4 }
                '{', '[' -> {
                    // كائن أو مصفوفة متداخلة — نتجاوزها (لا ندعمها كقيم محمّلة)
                    val opener = s[i]
                    val closer = if (opener == '{') '}' else ']'
                    var depth = 1
                    i++
                    var inString = false
                    while (i < s.length && depth > 0) {
                        val c = s[i]
                        if (c == '"' && s.getOrNull(i - 1) != '\\') inString = !inString
                        if (!inString) {
                            if (c == opener) depth++
                            else if (c == closer) depth--
                        }
                        i++
                    }
                    out[key] = null // غير مُحمَّلة
                }
                else -> {
                    val start = i
                    while (i < s.length && (s[i].isDigit() || s[i] in ".-+eE")) i++
                    val num = s.substring(start, i)
                    out[key] = num.toDoubleOrNull() ?: num
                }
            }
        }
        return out
    }
}
