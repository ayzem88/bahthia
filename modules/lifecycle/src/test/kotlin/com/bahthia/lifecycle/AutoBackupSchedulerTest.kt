package com.bahthia.lifecycle

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

class AutoBackupSchedulerTest {

    @TempDir lateinit var tempDir: Path
    private lateinit var dataDir: Path
    private lateinit var backupDir: Path
    private lateinit var prefs: UserPreferences
    private lateinit var manager: BackupManager
    private lateinit var scheduler: AutoBackupScheduler

    @BeforeEach
    fun setup() {
        dataDir = tempDir.resolve("data").also { Files.createDirectories(it) }
        backupDir = tempDir.resolve("backups").also { Files.createDirectories(it) }

        // فهرس وهميّ كي يكون هناك ما يُنسَخ
        val indexDir = dataDir.resolve("lucene-index").also { Files.createDirectories(it) }
        Files.writeString(indexDir.resolve("segments_1"), "fake segment")
        Files.writeString(dataDir.resolve("preferences.properties"), "theme=earthy")

        prefs = UserPreferences(dataDir)
        // BackupManager يستعمل defaultBackupDir = ~/Documents — نَستبدله بمحاكاة
        // عبر إنشاء BackupManager مع مجلد بيانات مؤقّت ونمرّر backupDir صراحةً للاختبار.
        manager = TestBackupManager(dataDir, fixedDestination = backupDir)
        scheduler = AutoBackupScheduler(prefs, manager, intervalDays = 7L)
    }

    @Test
    fun `disabled when autoBackupEnabled is false`() {
        prefs.autoBackupEnabled = false
        val result = scheduler.runIfDue(Instant.now())
        assertEquals(AutoBackupScheduler.Result.Disabled, result)
    }

    @Test
    fun `creates first backup when no previous backup exists`() {
        prefs.autoBackupEnabled = true
        prefs.lastBackupAtEpoch = 0L
        val result = scheduler.runIfDue(Instant.now())
        assertTrue(result is AutoBackupScheduler.Result.Created, "expected Created, got $result")
        val created = result as AutoBackupScheduler.Result.Created
        assertTrue(Files.exists(created.file))
        assertTrue(prefs.lastBackupAtEpoch > 0L)
    }

    @Test
    fun `does not create when last backup was less than interval ago`() {
        prefs.autoBackupEnabled = true
        val sixDaysAgo = Instant.now().minusSeconds(6 * 24 * 3600).epochSecond
        prefs.lastBackupAtEpoch = sixDaysAgo
        val result = scheduler.runIfDue(Instant.now())
        assertEquals(AutoBackupScheduler.Result.NotDue, result)
        // المؤشّر لم يتغيّر
        assertEquals(sixDaysAgo, prefs.lastBackupAtEpoch)
    }

    @Test
    fun `creates new backup when last was more than interval ago`() {
        prefs.autoBackupEnabled = true
        val eightDaysAgo = Instant.now().minusSeconds(8 * 24 * 3600).epochSecond
        prefs.lastBackupAtEpoch = eightDaysAgo
        val result = scheduler.runIfDue(Instant.now())
        assertTrue(result is AutoBackupScheduler.Result.Created)
        // المؤشّر تحدّث
        assertTrue(prefs.lastBackupAtEpoch > eightDaysAgo)
    }

    @Test
    fun `creates backup exactly at interval boundary`() {
        prefs.autoBackupEnabled = true
        // ٧ أيام بالضبط = 7 * 86400 ثانية + ١ لتجاوز "أقلّ من ٧"
        val sevenDaysAgo = Instant.now().minusSeconds(7L * 24 * 3600 + 1).epochSecond
        prefs.lastBackupAtEpoch = sevenDaysAgo
        val result = scheduler.runIfDue(Instant.now())
        assertTrue(result is AutoBackupScheduler.Result.Created, "should run after exactly $intervalDays days")
    }

    @Test
    fun `failed backup is reported but does not throw`() {
        prefs.autoBackupEnabled = true
        prefs.lastBackupAtEpoch = 0L
        val failingManager = object : BackupManager(dataDir) {
            override fun createBackup(destinationDir: Path): BackupResult {
                throw RuntimeException("disk full")
            }
        }
        val s = AutoBackupScheduler(prefs, failingManager)
        val result = s.runIfDue(Instant.now())
        assertTrue(result is AutoBackupScheduler.Result.Failed)
        assertEquals("disk full", (result as AutoBackupScheduler.Result.Failed).error)
        // عند الفشل: لا يُحدِّث lastBackupAtEpoch
        assertEquals(0L, prefs.lastBackupAtEpoch)
    }

    @Test
    fun `custom interval respected`() {
        prefs.autoBackupEnabled = true
        val twoDaysAgo = Instant.now().minusSeconds(2 * 24 * 3600).epochSecond
        prefs.lastBackupAtEpoch = twoDaysAgo
        val dailyScheduler = AutoBackupScheduler(prefs, manager, intervalDays = 1L)
        val result = dailyScheduler.runIfDue(Instant.now())
        assertTrue(result is AutoBackupScheduler.Result.Created, "1-day interval should fire after 2 days")
    }

    private val intervalDays = 7L

    /** مُحاكاة BackupManager توجِّه `createBackup()` إلى مجلد ثابت بدلاً من ~/Documents. */
    private class TestBackupManager(
        dataDir: Path,
        private val fixedDestination: Path,
    ) : BackupManager(dataDir) {
        override fun createBackup(destinationDir: Path): BackupResult {
            return super.createBackup(fixedDestination)
        }
    }
}
