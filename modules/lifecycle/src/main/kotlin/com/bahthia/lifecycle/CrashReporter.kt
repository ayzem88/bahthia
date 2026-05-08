package com.bahthia.lifecycle

import org.slf4j.LoggerFactory
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * مُبلِّغ الأعطال.
 *
 * - يُسجّل [Thread.UncaughtExceptionHandler] افتراضيّاً لكلّ الخيوط
 * - يكتب الاستثناءات إلى `crashes/<timestamp>.log` تحت [crashesDir]
 * - يحتفظ بآخر [maxLogs] ملفّ ويحذف الأقدم تلقائياً
 *
 * الاستعمال:
 * ```kotlin
 * CrashReporter(dataDir.resolve("crashes")).install()
 * ```
 *
 * لا يَطلب موافقة المستخدم لأنّ السجلاّت محلّيّة فقط — لا تُرسل إلى أيّ خادم.
 */
class CrashReporter(
    private val crashesDir: Path,
    private val appVersion: String = "unknown",
    private val maxLogs: Int = 20,
) {

    private val logger = LoggerFactory.getLogger(CrashReporter::class.java)
    private var previousHandler: Thread.UncaughtExceptionHandler? = null
    private var installed = false

    /** يُسجّل المعالج. آمن للاستدعاء أكثر من مرّة (idempotent). */
    fun install() {
        if (installed) return
        Files.createDirectories(crashesDir)
        previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, ex ->
            try {
                writeCrashLog(thread, ex)
            } catch (e: Exception) {
                logger.error("Failed to write crash log", e)
            }
            // مرِّر للمعالج السابق إن وُجد (مثلاً معالج JVM الافتراضي)
            previousHandler?.uncaughtException(thread, ex)
        }
        rotateOldLogs()
        installed = true
        logger.info("CrashReporter installed at {}", crashesDir)
    }

    /** يفصل المعالج (للاختبار). */
    fun uninstall() {
        if (!installed) return
        Thread.setDefaultUncaughtExceptionHandler(previousHandler)
        previousHandler = null
        installed = false
    }

    /** يكتب سجلّ عُطْل يدويّاً (للأخطاء الملتقَطة لكنّها تستحقّ التسجيل). */
    fun reportException(ex: Throwable, context: String? = null): Path {
        Files.createDirectories(crashesDir)
        val ts = TIMESTAMP_FMT.format(LocalDateTime.now())
        val file = crashesDir.resolve("crash-$ts.log")

        val content = buildString {
            append("=".repeat(60)).append('\n')
            append("Bahthia Library — Crash Report\n")
            append("=".repeat(60)).append('\n')
            append("Time:    ").append(LocalDateTime.now()).append('\n')
            append("Version: ").append(appVersion).append('\n')
            append("OS:      ").append(System.getProperty("os.name"))
                .append(' ').append(System.getProperty("os.version")).append('\n')
            append("JVM:     ").append(System.getProperty("java.version")).append('\n')
            if (context != null) append("Context: ").append(context).append('\n')
            append("\nStack Trace:\n")
            append("-".repeat(60)).append('\n')
            append(stackTrace(ex))
        }
        Files.writeString(file, content, StandardOpenOption.CREATE, StandardOpenOption.WRITE)
        rotateOldLogs()
        return file
    }

    private fun writeCrashLog(thread: Thread, ex: Throwable) {
        reportException(ex, context = "Uncaught in thread '${thread.name}'")
    }

    private fun rotateOldLogs() {
        if (!Files.exists(crashesDir)) return
        val logs = Files.list(crashesDir).use { stream ->
            stream
                .filter { it.fileName.toString().startsWith("crash-") }
                .filter { it.fileName.toString().endsWith(".log") }
                .sorted(Comparator.reverseOrder()) // الأحدث أوّلاً
                .toList()
        }
        if (logs.size > maxLogs) {
            logs.drop(maxLogs).forEach { Files.deleteIfExists(it) }
        }
    }

    /** يَسرد سجلّات الأعطال الموجودة، مرتّبةً من الأحدث للأقدم. */
    fun listCrashLogs(): List<Path> {
        if (!Files.exists(crashesDir)) return emptyList()
        return Files.list(crashesDir).use { stream ->
            stream
                .filter { it.fileName.toString().startsWith("crash-") }
                .filter { it.fileName.toString().endsWith(".log") }
                .sorted(Comparator.reverseOrder())
                .toList()
        }
    }

    private fun stackTrace(ex: Throwable): String {
        val sw = StringWriter()
        ex.printStackTrace(PrintWriter(sw))
        return sw.toString()
    }

    companion object {
        private val TIMESTAMP_FMT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")
    }
}
