package com.bahthia.lifecycle

import org.slf4j.LoggerFactory
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * مدير النسخ الاحتياطي.
 *
 * - **التصدير**: يضغط `lucene-index/` و`preferences.properties` في ملفّ `.zip`
 *   باسم `bahthia-backup-YYYYMMDD-HHmm.zip` ضمن المجلد المُختار.
 * - **الاستعادة**: يفكّ ضغط النسخة فوق `dataDir` (يَستبدل الموجود).
 *
 * يستعمل `java.util.zip` فقط — لا اعتمادات خارجيّة.
 *
 * @param dataDir  مجلد البيانات (`%APPDATA%/Bahthia` على Windows)
 *
 * تنبيه: قبل **الاستعادة**، يجب إغلاق `BahthiaSearcher` من المتّصل،
 *         وإلّا يفشل استبدال ملفّات الفهرس على Windows (ملفّات مفتوحة).
 */
open class BackupManager(private val dataDir: Path) {

    private val logger = LoggerFactory.getLogger(BackupManager::class.java)

    /** المجلد الافتراضي: `Documents/Bahthia Backups` على Windows. */
    val defaultBackupDir: Path = run {
        val home = System.getProperty("user.home")
        val docs = Path.of(home, "Documents", "Bahthia Backups")
        docs
    }

    data class BackupResult(
        val backupFile: Path,
        val sizeBytes: Long,
        val filesIncluded: Int,
    )

    /**
     * يُنشئ نسخة احتياطية في [destinationDir].
     * إن لم يكن المجلد موجوداً، يُنشئه.
     */
    open fun createBackup(destinationDir: Path = defaultBackupDir): BackupResult {
        Files.createDirectories(destinationDir)
        val ts = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm").format(LocalDateTime.now())
        val backupFile = destinationDir.resolve("bahthia-backup-$ts.zip")

        var fileCount = 0
        ZipOutputStream(BufferedOutputStream(Files.newOutputStream(backupFile))).use { zip ->
            // الفهرس
            val indexDir = dataDir.resolve("lucene-index")
            if (Files.exists(indexDir)) {
                fileCount += zipDirectory(indexDir, "lucene-index", zip)
            }
            // التفضيلات
            val prefs = dataDir.resolve("preferences.properties")
            if (Files.exists(prefs)) {
                zipFile(prefs, "preferences.properties", zip)
                fileCount++
            }
            // التيليمتري (لو وُجد)
            val telemetry = dataDir.resolve("telemetry.json")
            if (Files.exists(telemetry)) {
                zipFile(telemetry, "telemetry.json", zip)
                fileCount++
            }
        }
        val size = Files.size(backupFile)
        logger.info("Backup created: {} ({} bytes, {} files)", backupFile, size, fileCount)
        return BackupResult(backupFile, size, fileCount)
    }

    /**
     * يستعيد نسخة احتياطية من [zipFile].
     * يَستبدل الفهرس والتفضيلات الحاليَّين.
     *
     * @return عدد الملفّات المُستعادة
     * @throws IllegalArgumentException إن لم يكن الملفّ نسخةً صالحة
     */
    fun restoreBackup(zipFile: Path): Int {
        require(Files.exists(zipFile)) { "ملفّ النسخة غير موجود: $zipFile" }
        require(zipFile.toString().endsWith(".zip")) { "الملفّ ليس .zip: $zipFile" }

        // تحقّق سريع من صلاحيّة الـ zip
        var hasIndex = false
        ZipInputStream(BufferedInputStream(Files.newInputStream(zipFile))).use { zin ->
            var e: ZipEntry?
            while (zin.nextEntry.also { e = it } != null) {
                if (e!!.name.startsWith("lucene-index/")) { hasIndex = true; break }
            }
        }
        require(hasIndex) { "الملفّ لا يحتوي على فهرس Lucene — ليس نسخةً صالحةً." }

        // مسح الموجود ثمّ فكّ الضغط
        val indexDir = dataDir.resolve("lucene-index")
        if (Files.exists(indexDir)) {
            Files.walk(indexDir).use { stream ->
                stream.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
            }
        }

        var restored = 0
        ZipInputStream(BufferedInputStream(Files.newInputStream(zipFile))).use { zin ->
            var entry: ZipEntry?
            while (zin.nextEntry.also { entry = it } != null) {
                val e = entry!!
                val target = dataDir.resolve(e.name).normalize()
                require(target.startsWith(dataDir)) {
                    "محاولة كتابة خارج dataDir (zip slip): ${e.name}"
                }
                if (e.isDirectory) {
                    Files.createDirectories(target)
                } else {
                    Files.createDirectories(target.parent)
                    Files.copy(zin, target, StandardCopyOption.REPLACE_EXISTING)
                    restored++
                }
            }
        }
        logger.info("Restored {} files from backup {}", restored, zipFile)
        return restored
    }

    /** يَسرد النسخ الاحتياطية الموجودة في [dir]، مرتّبةً من الأحدث للأقدم. */
    fun listBackups(dir: Path = defaultBackupDir): List<Path> {
        if (!Files.exists(dir)) return emptyList()
        return Files.list(dir).use { stream ->
            stream
                .filter { it.fileName.toString().startsWith("bahthia-backup-") }
                .filter { it.fileName.toString().endsWith(".zip") }
                .sorted(Comparator.reverseOrder())
                .toList()
        }
    }

    private fun zipDirectory(root: Path, basePathInZip: String, zip: ZipOutputStream): Int {
        var count = 0
        Files.walk(root).use { stream ->
            stream.filter { Files.isRegularFile(it) }.forEach { file ->
                val rel = root.relativize(file).toString().replace('\\', '/')
                zip.putNextEntry(ZipEntry("$basePathInZip/$rel"))
                Files.copy(file, zip)
                zip.closeEntry()
                count++
            }
        }
        return count
    }

    private fun zipFile(file: Path, nameInZip: String, zip: ZipOutputStream) {
        zip.putNextEntry(ZipEntry(nameInZip))
        Files.copy(file, zip)
        zip.closeEntry()
    }
}
