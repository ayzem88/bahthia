package com.bahthia.lifecycle

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class CrashReporterTest {

    @TempDir lateinit var tempDir: Path
    private lateinit var crashesDir: Path
    private lateinit var reporter: CrashReporter
    private var savedHandler: Thread.UncaughtExceptionHandler? = null

    @BeforeEach
    fun setup() {
        crashesDir = tempDir.resolve("crashes")
        savedHandler = Thread.getDefaultUncaughtExceptionHandler()
        reporter = CrashReporter(crashesDir, appVersion = "0.1.0-test", maxLogs = 5)
    }

    @AfterEach
    fun teardown() {
        reporter.uninstall()
        Thread.setDefaultUncaughtExceptionHandler(savedHandler)
    }

    @Test
    fun `install creates crashes directory`() {
        reporter.install()
        assertTrue(Files.exists(crashesDir))
    }

    @Test
    fun `install registers default uncaught exception handler`() {
        val before = Thread.getDefaultUncaughtExceptionHandler()
        reporter.install()
        val after = Thread.getDefaultUncaughtExceptionHandler()
        // الـ handler يجب أن يتغيّر (إلّا إن كان `before` نفسه هو ما سيُسجَّل صدفةً)
        if (before != null) {
            assertFalse(before === after, "uncaught handler should have changed after install")
        } else {
            assertNotNull(after)
        }
    }

    @Test
    fun `reportException writes a crash log file`() {
        val logFile = reporter.reportException(
            RuntimeException("Boom! اختبار العُطْل."),
            context = "unit test",
        )
        assertTrue(Files.exists(logFile))
        val content = Files.readString(logFile)
        assertTrue(content.contains("Bahthia Library — Crash Report"))
        assertTrue(content.contains("Version: 0.1.0-test"))
        assertTrue(content.contains("Context: unit test"))
        assertTrue(content.contains("Boom"))
        assertTrue(content.contains("RuntimeException"))
    }

    @Test
    fun `reportException increments listCrashLogs`() {
        assertEquals(0, reporter.listCrashLogs().size)
        reporter.reportException(IllegalStateException("first"))
        // ضمان فاصل زمني بين الأسماء (timestamp format بدقّة ms)
        Thread.sleep(5)
        reporter.reportException(IllegalStateException("second"))
        Thread.sleep(5)
        reporter.reportException(IllegalStateException("third"))
        val logs = reporter.listCrashLogs()
        assertEquals(3, logs.size)
        // الأحدث أوّلاً
        assertTrue(logs[0].fileName.toString() > logs[1].fileName.toString())
        assertTrue(logs[1].fileName.toString() > logs[2].fileName.toString())
    }

    @Test
    fun `rotation keeps only maxLogs newest`() {
        // maxLogs = 5 (من setup)، أنشئ 8
        repeat(8) { i ->
            reporter.reportException(IllegalStateException("e$i"))
            Thread.sleep(3) // ضمان timestamp مختلف
        }
        val logs = reporter.listCrashLogs()
        assertEquals(5, logs.size, "rotation should drop logs older than maxLogs")
    }

    @Test
    fun `listCrashLogs returns empty when dir does not exist`() {
        // أنشئ reporter بمجلّد غير موجود — لا تستدعِ install()
        val r = CrashReporter(tempDir.resolve("nope"), appVersion = "x")
        assertTrue(r.listCrashLogs().isEmpty())
    }

    @Test
    fun `install is idempotent`() {
        reporter.install()
        val handlerAfterFirst = Thread.getDefaultUncaughtExceptionHandler()
        reporter.install() // مرّة ثانية — يجب ألّا يُغيّر شيئاً
        val handlerAfterSecond = Thread.getDefaultUncaughtExceptionHandler()
        assertEquals(handlerAfterFirst, handlerAfterSecond)
    }

    @Test
    fun `uninstall restores previous handler`() {
        val original = Thread.getDefaultUncaughtExceptionHandler()
        reporter.install()
        assertNotEquals(original, Thread.getDefaultUncaughtExceptionHandler())
        reporter.uninstall()
        assertEquals(original, Thread.getDefaultUncaughtExceptionHandler())
    }

    private fun assertNotEquals(a: Any?, b: Any?) {
        assertFalse(a == b, "expected $a != $b")
    }
}
