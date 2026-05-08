package com.bahthia.lifecycle.publishing

import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.LocalDate

/**
 * خدمة النشر — تُنسِّق:
 *   1. التحقّق من وجود ملفّ MSI مبنيّ
 *   2. حساب SHA-256
 *   3. رفع MSI إلى `/downloads/`
 *   4. تحديث `/api/version.json`
 *
 * **لا تَبني MSI بنفسها** — يجب أن يُنفَّذ `gradlew :app-desktop:packageMsi`
 * أوّلاً عبر طبقة الواجهة. هذا يُسهِّل الاختبار ويَفصل المسؤوليّات.
 *
 * @param config         إعدادات الناشر (cPanel)
 * @param client         عميل cPanel (قابل للحقن لاختبارات)
 */
class PublishingService(
    private val config: PublisherConfig,
    private val client: CpanelClient = CpanelClient(config),
) {

    private val logger = LoggerFactory.getLogger(PublishingService::class.java)

    sealed class Result {
        data class Success(
            val version: String,
            val msiUrl: String,
            val versionJsonUrl: String,
            val sizeBytes: Long,
            val sha256: String,
        ) : Result()

        data class Failure(val stage: String, val message: String) : Result()
    }

    /** أحداث التقدّم — للعرض في واجهة المطوّر. */
    sealed class Progress {
        data class Stage(val name: String, val fraction: Float) : Progress()
        data class Log(val message: String, val level: Level = Level.INFO) : Progress()
        enum class Level { INFO, WARN, ERROR }
    }

    /**
     * يَنشر إصداراً جديداً.
     *
     * @param msiFile      مسار MSI المبنيّ (يجب أن يكون موجوداً)
     * @param version      الإصدار (مثل "0.2.0") — يَدخل في اسم الملفّ على الخادم
     * @param releaseNotes ملاحظات الإصدار
     * @param onProgress   callback للأحداث (يعمل على الـ thread الحاليّ)
     */
    fun publish(
        msiFile: Path,
        version: String,
        releaseNotes: String,
        onProgress: (Progress) -> Unit = {},
    ): Result {
        // 1. تحقّقات
        if (!Files.exists(msiFile)) {
            return Result.Failure("validate", "ملفّ MSI غير موجود: $msiFile")
        }
        if (version.isBlank()) {
            return Result.Failure("validate", "الإصدار فارغ")
        }
        onProgress(Progress.Stage("التحقّق", 0.05f))

        // 2. حساب الحجم والـ SHA-256
        onProgress(Progress.Stage("حساب البصمة (SHA-256)", 0.15f))
        val sizeBytes = Files.size(msiFile)
        val sha256 = computeSha256(msiFile)
        onProgress(Progress.Log("الحجم: ${humanSize(sizeBytes)}, SHA-256: ${sha256.take(16)}…"))

        // 3. رفع MSI
        val msiName = "bahthia-$version.msi"
        val downloadsDir = "${config.remoteDir}/downloads"
        val msiRemotePath = "$downloadsDir/$msiName"
        onProgress(Progress.Stage("رفع MSI ($msiName)", 0.25f))
        when (val r = client.uploadBinaryFile(msiFile, msiRemotePath)) {
            is CpanelClient.Result.Failure -> return Result.Failure("upload-msi",
                "فشل رفع MSI: ${r.message}")
            CpanelClient.Result.Success -> Unit
        }
        onProgress(Progress.Stage("MSI مرفوع", 0.85f))

        // 4. تحديث version.json
        val msiUrl = "${config.siteBaseUrl}/downloads/$msiName"
        val versionJson = buildVersionJson(version, msiUrl, sizeBytes, sha256, releaseNotes)
        val apiDir = "${config.remoteDir}/api"
        val versionJsonRemotePath = "$apiDir/version.json"
        onProgress(Progress.Stage("تحديث version.json", 0.92f))
        when (val r = client.uploadString(versionJsonRemotePath, versionJson)) {
            is CpanelClient.Result.Failure -> return Result.Failure("upload-version-json",
                "فشل تحديث version.json: ${r.message}")
            CpanelClient.Result.Success -> Unit
        }

        // 5. النجاح
        val versionJsonUrl = "${config.siteBaseUrl}/api/version.json"
        onProgress(Progress.Stage("نُشر بنجاح ✓", 1.0f))
        onProgress(Progress.Log("MSI: $msiUrl"))
        onProgress(Progress.Log("API: $versionJsonUrl"))
        logger.info("Published version {} ({} bytes) — {}", version, sizeBytes, msiUrl)

        return Result.Success(
            version = version,
            msiUrl = msiUrl,
            versionJsonUrl = versionJsonUrl,
            sizeBytes = sizeBytes,
            sha256 = sha256,
        )
    }

    // ─── داخليّ ───

    /** يَبني JSON version بنفس الصيغة التي يقرأها `AutoUpdater`. */
    internal fun buildVersionJson(
        version: String,
        downloadUrl: String,
        sizeBytes: Long,
        sha256: String,
        notes: String,
    ): String {
        val date = LocalDate.now().toString()
        val notesEscaped = notes.replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\n", "\\n").replace("\r", "")
        return """{
  "version": "$version",
  "release_date": "$date",
  "download_url": "$downloadUrl",
  "download_size_bytes": $sizeBytes,
  "download_sha256": "$sha256",
  "min_supported_version": "0.0.1",
  "notes": "$notesEscaped"
}
"""
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

    private fun humanSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024L * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
        else -> "${bytes / (1024L * 1024 * 1024)} GB"
    }
}
