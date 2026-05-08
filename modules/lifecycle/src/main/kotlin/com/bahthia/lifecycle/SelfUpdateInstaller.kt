package com.bahthia.lifecycle

import org.slf4j.LoggerFactory
import java.io.BufferedInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/**
 * مُنزِّل ومُشغِّل التحديثات الذاتيّة.
 *
 * عند الباحث (المستخدم النهائيّ):
 *   1. ينزّل MSI من رابط `download_url` المُعطى من `AutoUpdater`
 *   2. (اختياريّ) يَتحقّق من SHA-256 إن مُعطى
 *   3. يُشغِّل msiexec بصيغة Windows ثمّ يُغلق التطبيق
 *
 * **ملاحظة Windows**: لا تستطيع استبدال `Bahthia Library.exe` وهو يَعمل،
 * لذا الـ msiexec يَنتظر إغلاق العمليّة. حلّنا: نُشغِّل msiexec ثمّ
 * نَخرج فوراً عبر `exitProcess(0)`.
 *
 * يَدعم Windows فقط في هذه المرحلة (MSI). سيُضاف DMG/DEB لاحقاً.
 */
class SelfUpdateInstaller {

    private val logger = LoggerFactory.getLogger(SelfUpdateInstaller::class.java)

    sealed class Progress {
        data class Downloading(val bytesRead: Long, val totalBytes: Long) : Progress() {
            val fraction: Float get() = if (totalBytes > 0) bytesRead.toFloat() / totalBytes else 0f
        }
        object Verifying : Progress()
        object Launching : Progress()
        data class Failed(val message: String) : Progress()
    }

    /**
     * ينزّل ويُشغِّل المُثبِّت.
     *
     * @param downloadUrl    الرابط من version.json
     * @param expectedSha256 (اختياريّ) للتحقّق من سلامة التنزيل
     * @param onProgress     callback للتقدّم
     * @param launchInstaller إن `false`، يُنزّل ولا يُشغِّل (مفيد للاختبار)
     * @return المسار إلى MSI المُنزَّل، أو null عند الفشل
     */
    fun downloadAndLaunch(
        downloadUrl: String,
        expectedSha256: String? = null,
        onProgress: (Progress) -> Unit = {},
        launchInstaller: Boolean = true,
    ): Path? {
        // 1. التنزيل
        val tempDir = Files.createTempDirectory("bahthia-update-")
        tempDir.toFile().deleteOnExit()
        val msiName = downloadUrl.substringAfterLast('/').ifBlank { "update.msi" }
        val target = tempDir.resolve(msiName)

        try {
            val conn = (URL(downloadUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 30_000
                readTimeout = 60_000
                requestMethod = "GET"
            }
            val contentLength = conn.contentLengthLong
            BufferedInputStream(conn.inputStream).use { input ->
                FileOutputStream(target.toFile()).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var totalRead = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n <= 0) break
                        output.write(buffer, 0, n)
                        totalRead += n
                        onProgress(Progress.Downloading(totalRead, contentLength))
                    }
                }
            }
        } catch (e: Exception) {
            onProgress(Progress.Failed("تعذّر التنزيل: ${e.message}"))
            return null
        }

        // 2. التحقّق من SHA-256
        if (!expectedSha256.isNullOrBlank()) {
            onProgress(Progress.Verifying)
            val actual = computeSha256(target)
            if (!actual.equals(expectedSha256, ignoreCase = true)) {
                onProgress(Progress.Failed(
                    "البصمة غير متطابقة — قد يكون الملفّ تالفاً.\n" +
                    "متوقَّع: ${expectedSha256.take(16)}…\nفعليّ: ${actual.take(16)}…"
                ))
                return null
            }
        }

        // 3. التشغيل
        if (launchInstaller) {
            onProgress(Progress.Launching)
            val launched = launchMsi(target)
            if (!launched) {
                onProgress(Progress.Failed("تعذّر تشغيل المُثبِّت — الملفّ في: $target"))
                return target
            }
        }
        return target
    }

    /** يُشغِّل MSI ثمّ يَخرج من التطبيق. */
    fun launchMsi(msiPath: Path): Boolean {
        val os = System.getProperty("os.name", "").lowercase()
        return try {
            when {
                os.contains("win") -> {
                    // msiexec /i path.msi — الافتراض: واجهة كاملة
                    ProcessBuilder("msiexec", "/i", msiPath.toString())
                        .inheritIO()
                        .start()
                    logger.info("Launched MSI installer: {}", msiPath)
                    true
                }
                else -> {
                    // macOS/Linux — لاحقاً
                    logger.warn("Self-update on {} not supported yet", os)
                    false
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to launch installer: {}", e.message, e)
            false
        }
    }

    private fun computeSha256(file: Path): String {
        val md = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(file).use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
