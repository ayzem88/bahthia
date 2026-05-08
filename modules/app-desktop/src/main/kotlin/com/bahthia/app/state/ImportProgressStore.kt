package com.bahthia.app.state

import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path

/**
 * يَحفظ تقدّم مهمّة استيراد جارية إلى ملفّ على القرص، فلو انهار التطبيق
 * أثناء الاستيراد يَستطيع المستخدم الاستئناف من حيث توقّفنا.
 *
 * بنية الملفّ بسيطة (سطر لكلّ حقل):
 * ```
 * kind=PDF
 * total=250
 * completed=47
 * folder=E:\PDF Books\Hadith
 * file=صحيح_البخاري.pdf
 * timestamp=1746537600000
 * pending=path1
 * pending=path2
 * pending=path3
 * ```
 */
class ImportProgressStore(private val dataDir: Path) {

    private val logger = LoggerFactory.getLogger(ImportProgressStore::class.java)
    private val file: Path = dataDir.resolve("import_state.txt")

    data class Snapshot(
        val kind: String,                // "PDF" أو "TEXT"
        val total: Int,
        val completed: Int,
        val timestamp: Long,
        val pending: List<Path>,
    ) {
        val remaining: Int get() = total - completed
        val isStale: Boolean
            get() = System.currentTimeMillis() - timestamp > STALE_THRESHOLD_MS
    }

    /** يَكتب لقطة جديدة (يَحلّ مَحلّ السابقة). */
    fun save(snap: Snapshot) {
        try {
            Files.createDirectories(dataDir)
            val sb = StringBuilder()
            sb.appendLine("kind=${snap.kind}")
            sb.appendLine("total=${snap.total}")
            sb.appendLine("completed=${snap.completed}")
            sb.appendLine("timestamp=${snap.timestamp}")
            for (p in snap.pending) sb.appendLine("pending=$p")
            Files.writeString(file, sb.toString())
        } catch (e: Exception) {
            logger.warn("Failed to save import progress: {}", e.message)
        }
    }

    /** يَقرأ آخر لقطة محفوظة (إن وُجدت). */
    fun load(): Snapshot? {
        if (!Files.exists(file)) return null
        return try {
            val lines = Files.readAllLines(file)
            var kind = ""
            var total = 0
            var completed = 0
            var timestamp = 0L
            val pending = mutableListOf<Path>()
            for (line in lines) {
                val eq = line.indexOf('=')
                if (eq < 0) continue
                val k = line.substring(0, eq)
                val v = line.substring(eq + 1)
                when (k) {
                    "kind"      -> kind = v
                    "total"     -> total = v.toIntOrNull() ?: 0
                    "completed" -> completed = v.toIntOrNull() ?: 0
                    "timestamp" -> timestamp = v.toLongOrNull() ?: 0L
                    "pending"   -> runCatching { pending.add(Path.of(v)) }
                }
            }
            if (kind.isBlank()) null
            else Snapshot(kind, total, completed, timestamp, pending)
        } catch (e: Exception) {
            logger.warn("Failed to load import progress: {}", e.message)
            null
        }
    }

    /** يَحذف الملفّ — يُستدعى عند اكتمال المهمّة بنجاح. */
    fun clear() {
        try { Files.deleteIfExists(file) }
        catch (e: Exception) { logger.warn("Failed to clear import progress: {}", e.message) }
    }

    private companion object {
        /** بعد ٧ أيّام، نَعتبر اللقطة قديمة جدّاً ولا تَستحقّ الاستئناف. */
        const val STALE_THRESHOLD_MS = 7L * 24 * 60 * 60 * 1000
    }
}
