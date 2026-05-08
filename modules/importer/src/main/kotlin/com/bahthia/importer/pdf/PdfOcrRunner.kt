package com.bahthia.importer.pdf

import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * مُشغِّل Mistral OCR.
 *
 * يستدعي سكربت Python المُضمَّن في موارد الموديول
 * (`resources/pdf_ocr/run_ocr.py`) كـ subprocess،
 * ويتلقّى أحداث التقدّم على stdout بصيغة JSON-line.
 *
 * يتطلّب:
 *   - Python 3 مثبَّتاً على نظام المستخدم
 *   - حزم: mistralai, pypdf
 */
class PdfOcrRunner(
    private val apiKey: String,
    private val pythonExecutable: String = detectPython(),
) {

    private val logger = LoggerFactory.getLogger(PdfOcrRunner::class.java)

    /** يُشير إلى عمليّة Python النشطة (لو وُجدت) — لإلغائها من خارج الـ thread. */
    @Volatile private var activeProcess: Process? = null

    /** إلغاء فوريّ — يَقتل عمليّة Python ويَجعل [convert] يُرجع. */
    fun cancel() {
        activeProcess?.let { p ->
            runCatching { p.destroyForcibly() }
            logger.info("PdfOcrRunner cancelled — Python process killed.")
        }
    }

    /** أحداث التقدّم. */
    sealed class Event {
        data class BatchStart(val total: Int) : Event()
        data class FileStart(val file: String) : Event()
        data class Progress(val file: String, val message: String, val fraction: Float) : Event()
        data class FileDone(val file: String, val txt: String, val pages: Int, val elapsed: Double) : Event()
        data class FileError(val file: String, val error: String) : Event()
        data class Warning(val message: String) : Event()
        data class Summary(val ok: Int, val fail: Int, val total: Int, val elapsed: Double) : Event()
        data class Fatal(val error: String) : Event()
    }

    /**
     * يُحوّل ملف PDF أو مجلد PDFs إلى نصوص.
     *
     * @param input    مسار PDF أو مجلد فيه PDFs
     * @param onEvent  callback لكلّ حدث (يعمل على thread أخرى — استعمل Dispatchers.Main عند تحديث الـ UI)
     * @return         قائمة المسارات الـ TXT التي أُنتجت
     */
    fun convert(input: Path, onEvent: (Event) -> Unit): List<Path> {
        if (apiKey.isBlank()) {
            onEvent(Event.Fatal("مفتاح Mistral API فارغ — اضبطه من إعدادات التطبيق"))
            return emptyList()
        }
        val script = extractScript()
        val cmd = listOf(
            pythonExecutable,
            "-X", "utf8",
            "-u",
            script.toString(),
            "--api-key", apiKey,
            "--input", input.toString(),
        )
        logger.info("Running OCR: {}", cmd.joinToString(" ") {
            if (it == apiKey) "<API_KEY>" else it
        })

        val producedTxts = mutableListOf<Path>()
        val process = try {
            ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .start()
        } catch (e: Exception) {
            onEvent(Event.Fatal("فشل تشغيل Python: ${e.message}. تأكّد من تثبيت Python على نظامك."))
            return emptyList()
        }
        activeProcess = process

        try {
            BufferedReader(InputStreamReader(process.inputStream, StandardCharsets.UTF_8)).use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    val event = parseEvent(line)
                    if (event == null) {
                        logger.debug("Non-JSON OCR line: {}", line)
                        continue
                    }
                    onEvent(event)
                    if (event is Event.FileDone) producedTxts.add(Path.of(event.txt))
                }
            }
            process.waitFor()
        } finally {
            activeProcess = null
        }
        return producedTxts
    }

    private fun parseEvent(line: String): Event? {
        val trimmed = line.trim()
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) return null
        return try {
            val json = SimpleJson.parse(trimmed) ?: return null
            when (json["event"] as? String) {
                "batch_start" -> Event.BatchStart(toInt(json["total"]))
                "start"       -> Event.FileStart(json["file"] as? String ?: "")
                "progress"    -> Event.Progress(
                    file = json["file"] as? String ?: "",
                    message = json["message"] as? String ?: "",
                    fraction = toFloat(json["fraction"]),
                )
                "done" -> Event.FileDone(
                    file = json["file"] as? String ?: "",
                    txt = json["txt"] as? String ?: "",
                    pages = toInt(json["pages"]),
                    elapsed = toDouble(json["elapsed"]),
                )
                "error"   -> Event.FileError(json["file"] as? String ?: "", json["error"] as? String ?: "")
                "warning" -> Event.Warning(json["message"] as? String ?: "")
                "summary" -> Event.Summary(
                    ok = toInt(json["ok"]),
                    fail = toInt(json["fail"]),
                    total = toInt(json["total"]),
                    elapsed = toDouble(json["elapsed"]),
                )
                "fatal" -> Event.Fatal(json["error"] as? String ?: "خطأ غير محدّد")
                else -> null
            }
        } catch (e: Exception) {
            logger.warn("Bad JSON event: {}", e.message)
            null
        }
    }

    /** يستخرج script Python و page_numbers_marker إلى مجلد مؤقّت ويُعيد مسار run_ocr.py. */
    private fun extractScript(): Path {
        val tmpDir = Files.createTempDirectory("bahthia-ocr-")
        tmpDir.toFile().deleteOnExit()

        for (resource in listOf("run_ocr.py", "page_numbers_marker.py")) {
            val target = tmpDir.resolve(resource)
            javaClass.getResourceAsStream("/pdf_ocr/$resource")?.use { input ->
                Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING)
            } ?: error("Missing bundled resource: pdf_ocr/$resource")
            target.toFile().deleteOnExit()
        }
        return tmpDir.resolve("run_ocr.py")
    }

    companion object {
        /** يحاول إيجاد Python على النظام. */
        fun detectPython(): String {
            val candidates = listOf("python", "python3", "py")
            for (cmd in candidates) {
                try {
                    val result = ProcessBuilder(cmd, "--version").redirectErrorStream(true).start()
                    if (result.waitFor() == 0) return cmd
                } catch (_: Exception) { /* continue */ }
            }
            return "python" // افتراضيّ — لو فشل نُبلّغ المستخدم لاحقاً
        }
    }
}

private fun toInt(v: Any?): Int = (v as? Number)?.toInt() ?: 0
private fun toFloat(v: Any?): Float = (v as? Number)?.toFloat() ?: 0f
private fun toDouble(v: Any?): Double = (v as? Number)?.toDouble() ?: 0.0

/** Tiny JSON parser لتجنّب اعتماد إضافي. */
private object SimpleJson {
    fun parse(s: String): Map<String, Any?>? {
        return try {
            // استعمال المنشور JSON مع GSON غير متاح هنا، لذا نستخدم نظام Java المدمج
            val mapper = javax.script.ScriptEngineManager().getEngineByName("nashorn")
            if (mapper != null) {
                @Suppress("UNCHECKED_CAST")
                mapper.eval("Java.asJSONCompatible($s)") as? Map<String, Any?>
            } else {
                // مُحلّل بسيط جدّاً (يكفي لرسائلنا)
                naiveParse(s)
            }
        } catch (_: Exception) {
            naiveParse(s)
        }
    }

    /** يُحلّل JSON بسيط مسطّح (لا يدعم arrays متداخلة). */
    private fun naiveParse(s: String): Map<String, Any?>? {
        val out = mutableMapOf<String, Any?>()
        var i = 1 // skip {
        while (i < s.length) {
            // skip whitespace
            while (i < s.length && s[i].isWhitespace()) i++
            if (i >= s.length || s[i] == '}') break
            if (s[i] == ',') { i++; continue }

            // key
            if (s[i] != '"') return null
            val keyEnd = s.indexOf('"', i + 1)
            if (keyEnd < 0) return null
            val key = s.substring(i + 1, keyEnd)
            i = keyEnd + 1

            // colon
            while (i < s.length && s[i] != ':') i++
            i++ // skip :
            while (i < s.length && s[i].isWhitespace()) i++

            // value
            if (i >= s.length) return null
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
                    i++ // skip closing "
                }
                't' -> { out[key] = true; i += 4 }
                'f' -> { out[key] = false; i += 5 }
                'n' -> { out[key] = null; i += 4 }
                else -> {
                    // number
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
