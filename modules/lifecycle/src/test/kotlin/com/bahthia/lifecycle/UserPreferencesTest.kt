package com.bahthia.lifecycle

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class UserPreferencesTest {

    @TempDir lateinit var tempDir: Path

    @Test
    fun `defaults are sensible`() {
        val p = UserPreferences(tempDir)
        assertEquals("", p.mistralApiKey)
        assertEquals("earthy", p.theme)
        assertEquals("M", p.fontSize)
        assertEquals("Sakkal Majalla", p.fontFamily)
        assertFalse(p.telemetryEnabled)
        assertEquals(1000, p.resultsLimit)
        assertFalse(p.autoBackupEnabled)
        assertEquals(0L, p.lastBackupAtEpoch)
    }

    @Test
    fun `set and persist mistralApiKey across instances`() {
        val p1 = UserPreferences(tempDir)
        p1.mistralApiKey = "secret-12345"
        // نسخة جديدة تَقرأ القرص
        val p2 = UserPreferences(tempDir)
        assertEquals("secret-12345", p2.mistralApiKey)
    }

    @Test
    fun `set and persist autoBackupEnabled`() {
        val p1 = UserPreferences(tempDir)
        p1.autoBackupEnabled = true
        val p2 = UserPreferences(tempDir)
        assertTrue(p2.autoBackupEnabled)
    }

    @Test
    fun `set and persist lastBackupAtEpoch`() {
        val p1 = UserPreferences(tempDir)
        val ts = 1700000000L
        p1.lastBackupAtEpoch = ts
        val p2 = UserPreferences(tempDir)
        assertEquals(ts, p2.lastBackupAtEpoch)
    }

    @Test
    fun `set telemetryEnabled persists`() {
        val p1 = UserPreferences(tempDir)
        p1.telemetryEnabled = true
        val p2 = UserPreferences(tempDir)
        assertTrue(p2.telemetryEnabled)
    }

    @Test
    fun `resultsLimit accepts custom values`() {
        val p1 = UserPreferences(tempDir)
        p1.resultsLimit = 5000
        val p2 = UserPreferences(tempDir)
        assertEquals(5000, p2.resultsLimit)
    }

    @Test
    fun `corrupted last_backup_at_epoch falls back to zero`() {
        // اكتب قيمة غير صالحة يدوياً
        val file = tempDir.resolve("preferences.properties")
        java.nio.file.Files.writeString(file, "last_backup_at_epoch=not-a-number\n")
        val p = UserPreferences(tempDir)
        assertEquals(0L, p.lastBackupAtEpoch)
    }
}
