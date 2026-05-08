package com.bahthia.lifecycle

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class TelemetryServiceTest {

    @TempDir lateinit var tempDir: Path
    private lateinit var storeFile: Path
    private var enabled = true
    private lateinit var telemetry: TelemetryService

    @BeforeEach
    fun setup() {
        storeFile = tempDir.resolve("telemetry.json")
        enabled = true
        telemetry = TelemetryService(
            storeFile = storeFile,
            appVersion = "0.1.0-test",
            isEnabled = { enabled },
        )
    }

    @Test
    fun `recordSearch increments total and per-mode`() {
        telemetry.recordSearch("word")
        telemetry.recordSearch("word")
        telemetry.recordSearch("derivatives")
        val s = telemetry.snapshot()
        assertEquals(3, s.totalSearches)
        assertEquals(2, s.searchesByMode["word"])
        assertEquals(1, s.searchesByMode["derivatives"])
    }

    @Test
    fun `recordFeatureUsage increments feature counter`() {
        telemetry.recordFeatureUsage("import.pdf")
        telemetry.recordFeatureUsage("import.pdf")
        telemetry.recordFeatureUsage("export.csv")
        val s = telemetry.snapshot()
        assertEquals(2, s.featureUsage["import.pdf"])
        assertEquals(1, s.featureUsage["export.csv"])
    }

    @Test
    fun `disabled service ignores all records`() {
        enabled = false
        telemetry.recordSearch("word")
        telemetry.recordFeatureUsage("import.pdf")
        val s = telemetry.snapshot()
        assertEquals(0, s.totalSearches)
        assertTrue(s.searchesByMode.isEmpty())
        assertTrue(s.featureUsage.isEmpty())
    }

    @Test
    fun `disabled service does not write to disk`() {
        enabled = false
        telemetry.recordSearch("word")
        telemetry.flush()
        assertFalse(Files.exists(storeFile), "should not create file when telemetry is disabled")
    }

    @Test
    fun `flush writes JSON file with expected fields`() {
        telemetry.recordSearch("word")
        telemetry.recordSearch("derivatives")
        telemetry.recordFeatureUsage("import.pdf")
        telemetry.flush()

        assertTrue(Files.exists(storeFile))
        val text = Files.readString(storeFile)
        assertTrue(text.contains("\"app_version\""))
        assertTrue(text.contains("\"total_searches\": 2"))
        assertTrue(text.contains("\"searches_by_mode\""))
        assertTrue(text.contains("\"feature_usage\""))
        assertTrue(text.contains("\"word\""))
        assertTrue(text.contains("\"import.pdf\""))
    }

    @Test
    fun `reset clears counters and removes file`() {
        telemetry.recordSearch("word")
        telemetry.recordFeatureUsage("import.pdf")
        telemetry.flush()
        assertTrue(Files.exists(storeFile))

        telemetry.reset()
        assertFalse(Files.exists(storeFile))
        val s = telemetry.snapshot()
        assertEquals(0, s.totalSearches)
        assertTrue(s.searchesByMode.isEmpty())
        assertTrue(s.featureUsage.isEmpty())
    }

    @Test
    fun `load preserves total_searches across restarts`() {
        telemetry.recordSearch("word")
        telemetry.recordSearch("word")
        telemetry.flush()

        // محاكاة إعادة التشغيل: خدمة جديدة تقرأ الملفّ نفسه
        val reborn = TelemetryService(
            storeFile = storeFile,
            appVersion = "0.1.0-test",
            isEnabled = { true },
        )
        assertEquals(2, reborn.snapshot().totalSearches)
    }

    @Test
    fun `concurrent records are not lost`() {
        val threads = (1..10).map { Thread { repeat(100) { telemetry.recordSearch("word") } } }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        assertEquals(1000, telemetry.snapshot().totalSearches)
        assertEquals(1000, telemetry.snapshot().searchesByMode["word"])
    }

    @Test
    fun `snapshot includes app version`() {
        val s = telemetry.snapshot()
        assertEquals("0.1.0-test", s.appVersion)
    }

    @Test
    fun `flush with empty data still writes valid JSON`() {
        telemetry.flush()
        assertTrue(Files.exists(storeFile))
        val text = Files.readString(storeFile)
        assertTrue(text.startsWith("{"))
        assertTrue(text.trim().endsWith("}"))
        assertTrue(text.contains("\"total_searches\": 0"))
    }
}
