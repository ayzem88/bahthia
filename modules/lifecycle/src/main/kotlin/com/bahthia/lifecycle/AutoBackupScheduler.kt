package com.bahthia.lifecycle

import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant

/**
 * مُجدوِل النسخ الاحتياطي الأسبوعيّ.
 *
 * عند بدء التطبيق:
 *   - إن كان `auto_backup_enabled` مُفعَّلاً
 *   - وإن مرّ ٧ أيّام أو أكثر منذ آخر نسخة
 *   → يُنشئ نسخة جديدة في `defaultBackupDir` بصمت
 *
 * الاستعمال:
 * ```kotlin
 * val result = AutoBackupScheduler(prefs, backupManager).runIfDue(Instant.now())
 * when (result) {
 *     is Result.Created -> logger.info("Auto-backup created: ${result.file}")
 *     Result.Disabled, Result.NotDue -> Unit
 *     is Result.Failed  -> logger.warn("Auto-backup failed: ${result.error}")
 * }
 * ```
 *
 * يَفصِل المُجدوِل عن `BackupManager` لتسهيل الاختبار.
 */
class AutoBackupScheduler(
    private val preferences: UserPreferences,
    private val backupManager: BackupManager,
    private val intervalDays: Long = 7L,
) {

    private val logger = LoggerFactory.getLogger(AutoBackupScheduler::class.java)

    sealed class Result {
        data class Created(val file: java.nio.file.Path, val sizeBytes: Long) : Result()
        data class Failed(val error: String) : Result()
        object Disabled : Result()
        object NotDue  : Result()
    }

    /**
     * يُنفِّذ نسخة احتياطيّة إن كان الوقت قد حان.
     * @param now اللحظة الحاليّة (قابلة للتحقّن في الاختبار)
     */
    fun runIfDue(now: Instant = Instant.now()): Result {
        if (!preferences.autoBackupEnabled) {
            return Result.Disabled
        }
        val last = preferences.lastBackupAtEpoch
        if (last > 0L) {
            val elapsed = Duration.between(Instant.ofEpochSecond(last), now)
            if (elapsed.toDays() < intervalDays) {
                logger.debug(
                    "Auto-backup not due yet (last={}d ago, interval={}d)",
                    elapsed.toDays(), intervalDays,
                )
                return Result.NotDue
            }
        }

        return try {
            val res = backupManager.createBackup()
            preferences.lastBackupAtEpoch = now.epochSecond
            logger.info("Auto-backup created: {}", res.backupFile)
            Result.Created(res.backupFile, res.sizeBytes)
        } catch (e: Exception) {
            logger.warn("Auto-backup failed: {}", e.message)
            Result.Failed(e.message ?: "خطأ غير معروف")
        }
    }
}
