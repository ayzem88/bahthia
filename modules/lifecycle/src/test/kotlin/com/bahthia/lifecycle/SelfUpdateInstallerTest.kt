package com.bahthia.lifecycle

import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

/**
 * يَختبر [SelfUpdateInstaller] بخادم HTTP محلّيّ يَمنع تشغيل msiexec
 * عبر `launchInstaller = false`.
 */
class SelfUpdateInstallerTest {

    @TempDir lateinit var tempDir: Path
    private lateinit var server: HttpServer
    private var port = 0
    private lateinit var fileBytes: ByteArray
    private lateinit var fileSha: String

    @BeforeEach
    fun setup() {
        // محتوى وهميّ يُحاكي MSI
        fileBytes = "FAKE-MSI-CONTENT-${System.currentTimeMillis()}".toByteArray()
        fileSha = sha256(fileBytes)

        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        port = server.address.port
        server.createContext("/bahthia.msi") { exchange ->
            exchange.responseHeaders.set("Content-Type", "application/x-msi")
            exchange.sendResponseHeaders(200, fileBytes.size.toLong())
            exchange.responseBody.use { it.write(fileBytes) }
        }
        server.createContext("/404.msi") { exchange ->
            exchange.sendResponseHeaders(404, -1)
            exchange.close()
        }
        server.start()
    }

    @AfterEach
    fun teardown() {
        server.stop(0)
    }

    @Test
    fun `download succeeds and writes file to temp`() {
        val installer = SelfUpdateInstaller()
        val result = installer.downloadAndLaunch(
            downloadUrl = "http://127.0.0.1:$port/bahthia.msi",
            launchInstaller = false,
        )
        assertNotNull(result)
        assertTrue(Files.exists(result!!))
        assertEquals(fileBytes.size.toLong(), Files.size(result))
    }

    @Test
    fun `download verifies SHA-256 when provided`() {
        val installer = SelfUpdateInstaller()
        val result = installer.downloadAndLaunch(
            downloadUrl = "http://127.0.0.1:$port/bahthia.msi",
            expectedSha256 = fileSha,
            launchInstaller = false,
        )
        assertNotNull(result, "valid SHA should pass verification")
    }

    @Test
    fun `download fails verification when SHA mismatches`() {
        val installer = SelfUpdateInstaller()
        val result = installer.downloadAndLaunch(
            downloadUrl = "http://127.0.0.1:$port/bahthia.msi",
            expectedSha256 = "0".repeat(64),
            launchInstaller = false,
        )
        assertNull(result, "wrong SHA should cause null return")
    }

    @Test
    fun `download fails on 404`() {
        val installer = SelfUpdateInstaller()
        val result = installer.downloadAndLaunch(
            downloadUrl = "http://127.0.0.1:$port/404.msi",
            launchInstaller = false,
        )
        // 404 يَنتج عنه IOException → null
        assertNull(result)
    }

    @Test
    fun `progress callback fires`() {
        val installer = SelfUpdateInstaller()
        val events = mutableListOf<SelfUpdateInstaller.Progress>()
        installer.downloadAndLaunch(
            downloadUrl = "http://127.0.0.1:$port/bahthia.msi",
            expectedSha256 = fileSha,
            onProgress = { events.add(it) },
            launchInstaller = false,
        )
        // على الأقلّ حدث Downloading واحد و Verifying
        assertTrue(events.any { it is SelfUpdateInstaller.Progress.Downloading })
        assertTrue(events.any { it is SelfUpdateInstaller.Progress.Verifying })
    }

    private fun sha256(bytes: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
