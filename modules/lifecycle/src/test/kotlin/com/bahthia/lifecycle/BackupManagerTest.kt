package com.bahthia.lifecycle

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class BackupManagerTest {

    @TempDir lateinit var tempDir: Path
    private lateinit var dataDir: Path
    private lateinit var backupDir: Path
    private lateinit var manager: BackupManager

    @BeforeEach
    fun setup() {
        dataDir = tempDir.resolve("data").also { Files.createDirectories(it) }
        backupDir = tempDir.resolve("backups").also { Files.createDirectories(it) }
        // إنشاء بنية وهميّة كأنّها فهرس Lucene + تفضيلات
        val indexDir = dataDir.resolve("lucene-index").also { Files.createDirectories(it) }
        Files.writeString(indexDir.resolve("segments_1"), "fake segment data")
        Files.writeString(indexDir.resolve("_0.fdt"), "fake field data")
        Files.writeString(dataDir.resolve("preferences.properties"), "theme=earthy\nmistral_api_key=secret")
        manager = BackupManager(dataDir)
    }

    @Test
    fun `createBackup produces a zip file with index and preferences`() {
        val result = manager.createBackup(backupDir)
        assertTrue(Files.exists(result.backupFile))
        assertTrue(result.backupFile.fileName.toString().startsWith("bahthia-backup-"))
        assertTrue(result.backupFile.fileName.toString().endsWith(".zip"))
        assertTrue(result.sizeBytes > 0)
        // 2 ملفّات فهرس + 1 تفضيلات = 3
        assertEquals(3, result.filesIncluded)
    }

    @Test
    fun `createBackup creates destination dir if missing`() {
        val nested = tempDir.resolve("a/b/c/backups")
        assertFalse(Files.exists(nested))
        manager.createBackup(nested)
        assertTrue(Files.exists(nested))
        assertTrue(manager.listBackups(nested).isNotEmpty())
    }

    @Test
    fun `restoreBackup recreates index and preferences`() {
        val backup = manager.createBackup(backupDir)
        // أتلف dataDir
        Files.delete(dataDir.resolve("preferences.properties"))
        val indexDir = dataDir.resolve("lucene-index")
        Files.list(indexDir).use { stream -> stream.forEach { Files.delete(it) } }
        Files.delete(indexDir)

        val restored = manager.restoreBackup(backup.backupFile)
        assertTrue(restored >= 3)
        assertTrue(Files.exists(dataDir.resolve("preferences.properties")))
        assertTrue(Files.exists(dataDir.resolve("lucene-index/segments_1")))
        assertEquals("theme=earthy\nmistral_api_key=secret",
                     Files.readString(dataDir.resolve("preferences.properties")))
    }

    @Test
    fun `restoreBackup rejects non-zip file`() {
        val txt = tempDir.resolve("not-a-backup.txt").also { Files.writeString(it, "hi") }
        assertThrows(IllegalArgumentException::class.java) { manager.restoreBackup(txt) }
    }

    @Test
    fun `restoreBackup rejects missing file`() {
        assertThrows(IllegalArgumentException::class.java) {
            manager.restoreBackup(tempDir.resolve("nope.zip"))
        }
    }

    @Test
    fun `restoreBackup rejects zip without lucene-index entry`() {
        // أنشئ zip فيه ملفّ عشوائي فقط
        val invalidZip = tempDir.resolve("invalid.zip")
        java.util.zip.ZipOutputStream(Files.newOutputStream(invalidZip)).use { out ->
            out.putNextEntry(java.util.zip.ZipEntry("readme.txt"))
            out.write("hello".toByteArray())
            out.closeEntry()
        }
        assertThrows(IllegalArgumentException::class.java) { manager.restoreBackup(invalidZip) }
    }

    @Test
    fun `listBackups returns sorted newest-first`() {
        val a = manager.createBackup(backupDir)
        // ملفّات وهميّة بتواريخ صريحة في الاسم — لا حاجة لانتظار
        val older = backupDir.resolve("bahthia-backup-20200101-1200.zip")
        Files.writeString(older, "x")
        val newer = backupDir.resolve("bahthia-backup-20990101-1200.zip")
        Files.writeString(newer, "y")
        val list = manager.listBackups(backupDir)
        assertTrue(list.contains(a.backupFile))
        assertTrue(list.contains(older))
        assertTrue(list.contains(newer))
        // الترتيب من الأحدث للأقدم
        assertEquals(newer, list.first())
        assertEquals(older, list.last())
    }

    @Test
    fun `listBackups returns empty when dir does not exist`() {
        val noDir = tempDir.resolve("does-not-exist")
        assertTrue(manager.listBackups(noDir).isEmpty())
    }

    @Test
    fun `listBackups ignores non-bahthia files`() {
        Files.writeString(backupDir.resolve("random.zip"), "x")
        Files.writeString(backupDir.resolve("notes.txt"), "y")
        val list = manager.listBackups(backupDir)
        assertTrue(list.none { it.fileName.toString() == "random.zip" })
        assertTrue(list.none { it.fileName.toString() == "notes.txt" })
    }
}
