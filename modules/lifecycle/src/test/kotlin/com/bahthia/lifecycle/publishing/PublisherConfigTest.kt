package com.bahthia.lifecycle.publishing

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class PublisherConfigTest {

    @TempDir lateinit var tempDir: Path

    @Test
    fun `loadOrNull returns null when file is missing`() {
        val path = tempDir.resolve("nope.properties")
        assertNull(PublisherConfig.loadOrNull(path))
    }

    @Test
    fun `loadOrNull returns null when token is empty`() {
        val path = tempDir.resolve("p.properties")
        Files.writeString(path, """
            cpanel.host=h
            cpanel.user=u
            cpanel.token=
            cpanel.remote.dir=/x
        """.trimIndent())
        assertNull(PublisherConfig.loadOrNull(path))
    }

    @Test
    fun `loadOrNull returns config when complete`() {
        val path = tempDir.resolve("p.properties")
        Files.writeString(path, """
            cpanel.host=server.com
            cpanel.port=2083
            cpanel.user=aymannji
            cpanel.token=ABC123
            cpanel.remote.dir=/home/aymannji/public_html
            site.base.url=https://www.bahthia.com
        """.trimIndent())
        val cfg = PublisherConfig.loadOrNull(path)
        assertNotNull(cfg)
        assertEquals("server.com", cfg!!.cpanelHost)
        assertEquals(2083, cfg.cpanelPort)
        assertEquals("aymannji", cfg.cpanelUser)
        assertEquals("ABC123", cfg.cpanelToken)
        assertEquals("/home/aymannji/public_html", cfg.remoteDir)
        assertEquals("https://www.bahthia.com", cfg.siteBaseUrl)
    }

    @Test
    fun `loadOrNull defaults port to 2083`() {
        val path = tempDir.resolve("p.properties")
        Files.writeString(path, """
            cpanel.host=h
            cpanel.user=u
            cpanel.token=t
            cpanel.remote.dir=/x
        """.trimIndent())
        val cfg = PublisherConfig.loadOrNull(path)
        assertEquals(2083, cfg!!.cpanelPort)
    }

    @Test
    fun `loadOrNull trims whitespace`() {
        val path = tempDir.resolve("p.properties")
        Files.writeString(path, """
            cpanel.host=  h
            cpanel.user=u
            cpanel.token=  t
            cpanel.remote.dir=  /x
        """.trimIndent())
        val cfg = PublisherConfig.loadOrNull(path)
        assertEquals("h", cfg!!.cpanelHost)
        assertEquals("t", cfg.cpanelToken)
        assertEquals("/x", cfg.remoteDir)
    }

    @Test
    fun `writeTemplate creates a non-empty file with placeholders`() {
        val path = tempDir.resolve("template.properties")
        PublisherConfig.writeTemplate(path)
        assertTrue(Files.exists(path))
        val content = Files.readString(path)
        assertTrue(content.contains("cpanel.host="))
        assertTrue(content.contains("cpanel.token="))
        assertTrue(content.contains("YOUR_API_TOKEN_HERE"))
    }
}
