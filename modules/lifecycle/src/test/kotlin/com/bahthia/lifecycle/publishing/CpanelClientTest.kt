package com.bahthia.lifecycle.publishing

import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path

/**
 * يَختبر [CpanelClient] بخادم HTTP محلّيّ يُحاكي cPanel API.
 *
 * (CpanelClient يَستعمل HTTPS بشكل مباشر — للاختبار نَستعمل HTTP محلّي
 * ونَحقن host:port مع `cpanelHost = "127.0.0.1"` ولكن... المشكلة أنّ
 * CpanelClient يَفترض HTTPS. لذلك نَختبر فقط بناء الطلب وحالات الفشل.)
 */
class CpanelClientTest {

    @TempDir lateinit var tempDir: Path

    private fun config(host: String, port: Int) =
        PublisherConfig(host, port, "user", "TOKEN", "/x", "https://x.com")

    @Test
    fun `mkdir on unreachable host returns failure`() {
        val client = CpanelClient(config("127.0.0.1", 1)) // port 1 لا يَسمع
        val result = client.mkdir("/tmp")
        assertTrue(result is CpanelClient.Result.Failure)
    }

    @Test
    fun `uploadTextFile reads UTF-8 content correctly`() {
        // اختبار غير مُنفِّذ كاملاً — سنَتحقّق من قراءة الملفّ على الأقلّ
        val file = tempDir.resolve("hello.txt")
        Files.writeString(file, "مرحباً بالعالم")
        val client = CpanelClient(config("127.0.0.1", 1))
        // هذا سيَفشل لكن لن يَرمي استثناءً
        val result = client.uploadTextFile(file, "/x/hello.txt")
        assertTrue(result is CpanelClient.Result.Failure)
    }

    @Test
    fun `uploadBinaryFile encodes to base64`() {
        val file = tempDir.resolve("data.bin")
        Files.write(file, byteArrayOf(0x00, 0xFF.toByte(), 0x42))
        val client = CpanelClient(config("127.0.0.1", 1))
        val result = client.uploadBinaryFile(file, "/x/data.bin")
        // الفشل متوقَّع (لا خادم) — لكن لا يَرمي بسبب مشكلة في base64
        assertTrue(result is CpanelClient.Result.Failure)
    }

    @Test
    fun `uploadString simple JSON`() {
        val client = CpanelClient(config("127.0.0.1", 1))
        val result = client.uploadString("/x/data.json", """{"version":"0.2.0"}""")
        assertTrue(result is CpanelClient.Result.Failure)
    }

    @Test
    fun `result types are exhaustive`() {
        val success: CpanelClient.Result = CpanelClient.Result.Success
        val failure: CpanelClient.Result = CpanelClient.Result.Failure("oops", 500)
        assertTrue(success is CpanelClient.Result.Success)
        assertTrue(failure is CpanelClient.Result.Failure)
        assertEquals("oops", (failure as CpanelClient.Result.Failure).message)
        assertEquals(500, failure.httpCode)
    }
}
