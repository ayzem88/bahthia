package com.bahthia.lifecycle

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AutoUpdaterTest {

    // ─── Version comparison ───

    @Test fun `compareVersions equal`() {
        assertEquals(0, AutoUpdater.compareVersions("0.1.0", "0.1.0"))
    }

    @Test fun `compareVersions newer major`() {
        assertTrue(AutoUpdater.compareVersions("1.0.0", "0.9.9") > 0)
    }

    @Test fun `compareVersions older minor`() {
        assertTrue(AutoUpdater.compareVersions("0.1.0", "0.2.0") < 0)
    }

    @Test fun `compareVersions newer patch`() {
        assertTrue(AutoUpdater.compareVersions("0.1.5", "0.1.4") > 0)
    }

    @Test fun `compareVersions tolerates v prefix`() {
        assertEquals(0, AutoUpdater.compareVersions("v1.2.3", "1.2.3"))
    }

    @Test fun `compareVersions tolerates two-part version`() {
        // "0.2" ≡ "0.2.0"
        assertEquals(0, AutoUpdater.compareVersions("0.2", "0.2.0"))
        assertTrue(AutoUpdater.compareVersions("0.2", "0.1.9") > 0)
    }

    // ─── JSON parsing ───

    @Test fun `parseJson returns full UpdateInfo`() {
        val json = """
            {
              "version": "0.2.0",
              "release_date": "2026-06-01",
              "download_url": "https://www.bahthia.com/download/bahthia-0.2.0.msi",
              "notes": "تحسينات في البحث بالجذر"
            }
        """.trimIndent()
        val info = AutoUpdater.parseJson(json)
        assertNotNull(info)
        assertEquals("0.2.0", info!!.version)
        assertEquals("2026-06-01", info.releaseDate)
        assertEquals("https://www.bahthia.com/download/bahthia-0.2.0.msi", info.downloadUrl)
        assertTrue(info.notes.contains("الجذر"))
    }

    @Test fun `parseJson returns null on garbage`() {
        assertNull(AutoUpdater.parseJson("not json at all"))
    }

    @Test fun `parseJson returns null when version field missing`() {
        assertNull(AutoUpdater.parseJson("""{"release_date": "2026-06-01"}"""))
    }

    // ─── Integration: parseAndCompare ───

    @Test fun `update available when feed is newer`() {
        val updater = AutoUpdater("http://unused", currentVersion = "0.1.0")
        val info = updater.parseAndCompare("""{"version": "0.2.0"}""")
        assertNotNull(info)
        assertEquals("0.2.0", info!!.version)
    }

    @Test fun `no update when on latest`() {
        val updater = AutoUpdater("http://unused", currentVersion = "0.2.0")
        val info = updater.parseAndCompare("""{"version": "0.2.0"}""")
        assertNull(info)
    }

    @Test fun `no update when local is newer than feed`() {
        val updater = AutoUpdater("http://unused", currentVersion = "0.3.0")
        val info = updater.parseAndCompare("""{"version": "0.2.0"}""")
        assertNull(info)
    }
}
