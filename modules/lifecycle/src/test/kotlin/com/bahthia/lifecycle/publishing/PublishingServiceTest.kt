package com.bahthia.lifecycle.publishing

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class PublishingServiceTest {

    @TempDir lateinit var tempDir: Path

    private fun makeConfig() = PublisherConfig(
        "host.example", 2083, "user", "TOKEN", "/home/user/public_html",
        "https://www.bahthia.com",
    )

    /** عميل وهميّ يلتقط الاستدعاءات بدون شبكة. */
    private class FakeClient(
        var failOn: String? = null,
        val capturedUploads: MutableList<Triple<String, String, ByteArray?>> = mutableListOf(),
    ) : CpanelClient(
        // ندخل config وهمياً — العميل لن يَستعمله ما دمنا نَطغى على كلّ الدوالّ
        PublisherConfig("h", 2083, "u", "t", "/x", "https://x")
    ) {
        override fun mkdir(remotePath: String): Result {
            if (failOn == "mkdir") return Result.Failure("mkdir fail")
            return Result.Success
        }
        override fun uploadBinaryFile(localFile: Path, remotePath: String): Result {
            if (failOn == "upload-msi") return Result.Failure("upload fail")
            capturedUploads.add(Triple(remotePath, "binary", Files.readAllBytes(localFile)))
            return Result.Success
        }
        override fun uploadString(remotePath: String, content: String): Result {
            if (failOn == "version-json") return Result.Failure("version json fail")
            capturedUploads.add(Triple(remotePath, content, null))
            return Result.Success
        }
        override fun uploadTextFile(localFile: Path, remotePath: String): Result {
            return uploadString(remotePath, Files.readString(localFile))
        }
    }

    @Test
    fun `publish fails when MSI does not exist`() {
        val service = PublishingService(makeConfig(), FakeClient())
        val result = service.publish(tempDir.resolve("nope.msi"), "0.2.0", "notes")
        assertTrue(result is PublishingService.Result.Failure)
        assertEquals("validate", (result as PublishingService.Result.Failure).stage)
    }

    @Test
    fun `publish fails when version is blank`() {
        val msi = tempDir.resolve("a.msi").also { Files.writeString(it, "x") }
        val service = PublishingService(makeConfig(), FakeClient())
        val result = service.publish(msi, "  ", "notes")
        assertTrue(result is PublishingService.Result.Failure)
    }

    @Test
    fun `publish uploads MSI and version json on success`() {
        val msi = tempDir.resolve("bahthia-0.2.0.msi").also { Files.writeString(it, "fake-msi-content") }
        val client = FakeClient()
        val service = PublishingService(makeConfig(), client)
        val result = service.publish(msi, "0.2.0", "أوّل تحديث رسميّ")
        assertTrue(result is PublishingService.Result.Success, "expected Success, got $result")
        val ok = result as PublishingService.Result.Success
        assertEquals("0.2.0", ok.version)
        assertTrue(ok.msiUrl.endsWith("/downloads/bahthia-0.2.0.msi"))
        assertTrue(ok.versionJsonUrl.endsWith("/api/version.json"))
        assertTrue(ok.sizeBytes > 0)
        assertEquals(64, ok.sha256.length, "SHA-256 should be 64 hex chars")

        // 2 uploads: MSI ثمّ version.json
        val msiUpload = client.capturedUploads.find { it.first.endsWith(".msi") }
        assertNotNull(msiUpload)
        val jsonUpload = client.capturedUploads.find { it.first.endsWith("version.json") }
        assertNotNull(jsonUpload)
        // version.json يَحتوي على الإصدار والـ SHA
        assertTrue(jsonUpload!!.second.contains("\"version\": \"0.2.0\""))
        assertTrue(jsonUpload.second.contains("\"download_sha256\": \"${ok.sha256}\""))
    }

    @Test
    fun `publish stops if MSI upload fails`() {
        val msi = tempDir.resolve("bahthia-0.2.0.msi").also { Files.writeString(it, "x") }
        val client = FakeClient(failOn = "upload-msi")
        val service = PublishingService(makeConfig(), client)
        val result = service.publish(msi, "0.2.0", "notes")
        assertTrue(result is PublishingService.Result.Failure)
        assertEquals("upload-msi", (result as PublishingService.Result.Failure).stage)
        // يجب ألّا يَرفع version.json
        assertTrue(client.capturedUploads.none { it.first.endsWith("version.json") })
    }

    @Test
    fun `publish reports failure if version json upload fails`() {
        val msi = tempDir.resolve("bahthia-0.2.0.msi").also { Files.writeString(it, "x") }
        val client = FakeClient(failOn = "version-json")
        val service = PublishingService(makeConfig(), client)
        val result = service.publish(msi, "0.2.0", "notes")
        assertTrue(result is PublishingService.Result.Failure)
        assertEquals("upload-version-json", (result as PublishingService.Result.Failure).stage)
    }

    @Test
    fun `publish progress callback fires`() {
        val msi = tempDir.resolve("bahthia-0.2.0.msi").also { Files.writeString(it, "x") }
        val service = PublishingService(makeConfig(), FakeClient())
        val stages = mutableListOf<String>()
        service.publish(msi, "0.2.0", "notes") { p ->
            if (p is PublishingService.Progress.Stage) stages.add(p.name)
        }
        assertTrue(stages.isNotEmpty())
        assertTrue(stages.any { it.contains("MSI") || it.contains("رفع") })
        assertTrue(stages.any { it.contains("نُشر") || it.contains("✓") })
    }

    @Test
    fun `buildVersionJson escapes quotes and newlines`() {
        val service = PublishingService(makeConfig(), FakeClient())
        val json = service.buildVersionJson(
            version = "0.2.0",
            downloadUrl = "https://x/y.msi",
            sizeBytes = 1024,
            sha256 = "deadbeef",
            notes = "خط أوّل\nخط \"ثانٍ\"",
        )
        assertTrue(json.contains("\\n"))
        assertTrue(json.contains("\\\""))
        assertTrue(json.contains("\"version\": \"0.2.0\""))
        assertTrue(json.contains("\"download_size_bytes\": 1024"))
    }
}
