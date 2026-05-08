package com.bahthia.lifecycle.publishing

import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64

/**
 * عميل بسيط لـ cPanel REST API.
 *
 * يُغلِّف ٣ عمليّات أساسيّة:
 *  - `mkdir`            — إنشاء مجلد بعيد
 *  - `uploadTextFile`   — رفع ملفّ نصّيّ (UTF-8، بدون base64)
 *  - `uploadBinaryFile` — رفع ملفّ ثنائيّ (base64-encoded)
 *
 * يُحاكي السلوك في `رفع_نهائي.py` ولكن بـ Kotlin خالص.
 *
 * @param config الإعدادات (host, port, user, token, ...)
 */
open class CpanelClient(private val config: PublisherConfig) {

    private val logger = LoggerFactory.getLogger(CpanelClient::class.java)

    sealed class Result {
        object Success : Result()
        data class Failure(val message: String, val httpCode: Int? = null) : Result()
    }

    /** يُنشئ مجلداً بعيداً (idempotent — لا يَفشل إن كان موجوداً). */
    open fun mkdir(remotePath: String): Result {
        val params = mapOf(
            "path" to remotePath,
            "permissions" to "0755",
        )
        return execute("Fileman/mkdir", params)
    }

    /** يَرفع ملفّاً نصّياً بصيغة UTF-8. */
    open fun uploadTextFile(localFile: Path, remotePath: String): Result {
        val content = Files.readString(localFile, StandardCharsets.UTF_8)
        return uploadInline(remotePath, content, encoding = null)
    }

    /** يَرفع ملفّاً ثنائيّاً بـ base64. مناسب لـ MSI/PNG/PDF/ZIP. */
    open fun uploadBinaryFile(localFile: Path, remotePath: String): Result {
        val bytes = Files.readAllBytes(localFile)
        val b64 = Base64.getEncoder().encodeToString(bytes)
        return uploadInline(remotePath, b64, encoding = "base64")
    }

    /** يَرفع نصّاً مباشرةً (دون قراءة ملفّ — مفيد لـ JSON الصغير). */
    open fun uploadString(remotePath: String, content: String): Result {
        return uploadInline(remotePath, content, encoding = null)
    }

    // ─── داخليّ ───

    private fun uploadInline(remotePath: String, content: String, encoding: String?): Result {
        val dir = remotePath.substringBeforeLast('/').ifBlank { config.remoteDir }
        val name = remotePath.substringAfterLast('/')

        val params = buildMap {
            put("dir", dir)
            put("file", name)
            put("content", content)
            if (encoding != null) put("encoding", encoding)
        }

        // أوّلاً: تأكَّد أنّ المجلد موجود
        mkdir(dir)
        return execute("Fileman/save_file_content", params)
    }

    private fun execute(endpoint: String, params: Map<String, String>): Result {
        val urlStr = "https://${config.cpanelHost}:${config.cpanelPort}/execute/$endpoint"

        // cPanel API يَقبل المعاملات إمّا في query string (GET) أو body (POST).
        // نَستعمل POST لتجنّب حدود طول URL مع المحتوى الكبير.
        val body = params.entries.joinToString("&") { (k, v) ->
            "${urlEncode(k)}=${urlEncode(v)}"
        }

        return try {
            val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Authorization", "cpanel ${config.cpanelUser}:${config.cpanelToken}")
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                doOutput = true
                connectTimeout = 30_000
                readTimeout = 120_000
            }
            conn.outputStream.use { out ->
                out.write(body.toByteArray(StandardCharsets.UTF_8))
            }
            val code = conn.responseCode
            if (code !in 200..299) {
                val errBody = conn.errorStream?.let {
                    BufferedReader(InputStreamReader(it, StandardCharsets.UTF_8)).readText()
                } ?: ""
                logger.warn("cPanel API HTTP {}: {} → {}", code, endpoint, errBody.take(200))
                return Result.Failure("HTTP $code", code)
            }
            val response = BufferedReader(InputStreamReader(conn.inputStream, StandardCharsets.UTF_8))
                .use { it.readText() }
            // cPanel يُرجع `{"status": 1, ...}` للنجاح
            if (response.contains("\"status\":1") || response.contains("\"status\": 1")) {
                Result.Success
            } else {
                Result.Failure(extractError(response))
            }
        } catch (e: Exception) {
            logger.warn("cPanel API exception on {}: {}", endpoint, e.message)
            Result.Failure(e.message ?: "خطأ شبكة غير محدّد")
        }
    }

    private fun extractError(jsonResponse: String): String {
        // محاولة استخراج رسالة الخطأ من `errors` array
        val errorsIdx = jsonResponse.indexOf("\"errors\"")
        if (errorsIdx < 0) return "API failure"
        val start = jsonResponse.indexOf('"', errorsIdx + 8 + 1) // بعد "errors":[
        if (start < 0) return jsonResponse.take(200)
        val end = jsonResponse.indexOf('"', start + 1)
        if (end < 0) return jsonResponse.take(200)
        return jsonResponse.substring(start + 1, end).take(200)
    }

    private fun urlEncode(s: String): String =
        URLEncoder.encode(s, StandardCharsets.UTF_8)
}
