package com.bahthia.importer.migration

import com.bahthia.domain.Page
import com.bahthia.search.indexer.BahthiaIndexer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.sql.Connection
import java.sql.DriverManager
import kotlin.system.exitProcess

/**
 * مُهاجِر بيانات نسخة Python القديمة إلى فهرس Lucene.
 *
 * يقرأ كل ملفات `data/shards/shard_*.db` (SQLite)، يستخرج
 *   جدول `books` و `pages`، ويُدخل كل صفحة في Lucene index
 *   مع كلّ الميتاداتا (العنوان، المؤلف، التصنيف، السنة).
 *
 * ## التشغيل
 * ```
 * ./gradlew :importer:migrate \
 *     --args="E:\البرامج والتطبيقات\المكتبة البحثية\data\shards %APPDATA%\Bahthia\lucene-index"
 * ```
 *
 * أو من Kotlin:
 * ```
 * MigrationTool.migrate(
 *     shardsDir = Paths.get("..."),
 *     outputIndexDir = Paths.get("...")
 * )
 * ```
 */
object MigrationTool {

    private val logger: Logger = LoggerFactory.getLogger(MigrationTool::class.java)
    private val shardFileRegex = Regex("""shard_(\d+)\.db""")
    private const val BOOK_ID_STRIDE = 1_000_000_000_000L

    /**
     * يُهاجر كل بيانات الشاردات إلى فهرس Lucene.
     *
     * @param shardsDir       مجلد فيه `shard_*.db`
     * @param outputIndexDir  مجلد الفهرس الجديد (يُمحى ويُعاد بناؤه)
     */
    fun migrate(
        shardsDir: Path,
        outputIndexDir: Path,
        progressCallback: (Progress) -> Unit = {},
    ): Stats {
        require(Files.isDirectory(shardsDir)) { "Shards directory not found: $shardsDir" }

        // اكتشاف الشاردات
        val shards = Files.list(shardsDir).use { stream ->
            stream
                .filter { it.fileName.toString().matches(Regex("shard_\\d+\\.db")) }
                .sorted()
                .toList()
        }
        require(shards.isNotEmpty()) { "No shard files found in $shardsDir" }
        logger.info("Found {} shards in {}", shards.size, shardsDir)

        // إعداد الفهرس الناتج
        if (Files.exists(outputIndexDir)) {
            logger.warn("Output index exists, will overwrite: {}", outputIndexDir)
            deleteRecursively(outputIndexDir)
        }
        Files.createDirectories(outputIndexDir)

        var totalBooks = 0L
        var totalPages = 0L
        var totalErrors = 0L

        BahthiaIndexer(outputIndexDir).use { indexer ->
            for ((shardIdx, shardPath) in shards.withIndex()) {
                logger.info("[{}/{}] Migrating {}", shardIdx + 1, shards.size, shardPath.fileName)
                val (booksInShard, pagesInShard, errorsInShard) = migrateShard(shardPath, indexer, progressCallback)
                totalBooks += booksInShard
                totalPages += pagesInShard
                totalErrors += errorsInShard
                logger.info("  done: {} books, {} pages, {} errors", booksInShard, pagesInShard, errorsInShard)
                indexer.commit()
            }
        }

        // تحقّق صارم: نقرأ الفهرس بعد الإغلاق لنتأكّد أنّ البيانات على القرص فعلاً
        val onDiskCount = countDocsOnDisk(outputIndexDir)
        if (onDiskCount.toLong() < totalPages) {
            logger.warn(
                "MIGRATION ANOMALY: indexed {} pages but on-disk count is {}",
                totalPages, onDiskCount,
            )
        } else {
            logger.info("Verified: {} documents persisted on disk at {}", onDiskCount, outputIndexDir)
        }

        return Stats(
            totalBooks = totalBooks,
            totalPages = totalPages,
            totalErrors = totalErrors,
            shardCount = shards.size,
            onDiskDocs = onDiskCount,
        )
    }

    private data class ShardStats(val books: Long, val pages: Long, val errors: Long)

    private fun migrateShard(
        shardPath: Path,
        indexer: BahthiaIndexer,
        progressCallback: (Progress) -> Unit,
    ): ShardStats {
        var books = 0L
        var pages = 0L
        var errors = 0L
        val shardNumber = shardNumber(shardPath)

        DriverManager.getConnection("jdbc:sqlite:${shardPath.toAbsolutePath()}").use { conn ->
            // اقرأ جدول الكتب — ميتاداتا فقط
            val booksMeta = mutableMapOf<Long, BookMeta>()
            conn.createStatement().use { st ->
                val rs = st.executeQuery(
                    """
                    SELECT id, title, author, category, year, death_year, word_count, pages_count,
                           publisher, country, edition, volumes_count, investigator, translator
                    FROM books
                    """.trimIndent()
                )
                while (rs.next()) {
                    val id = globalBookId(shardNumber, rs.getLong("id"))
                    booksMeta[id] = BookMeta(
                        title = rs.getString("title") ?: "بلا عنوان",
                        author = rs.getString("author"),
                        category = rs.getString("category"),
                        year = rs.getString("year"),
                        deathYear = rs.getString("death_year"),
                        country = rs.getString("country"),
                    )
                }
            }
            books = booksMeta.size.toLong()
            logger.debug("  books loaded: {}", books)

            // اقرأ الصفحات بحجم دفعة معقول
            val pageColumns = tableColumns(conn, "pages")
            val hasContentPath = "content_path" in pageColumns
            val hasOriginalPageNumber = "original_page_number" in pageColumns
            val contentPathSelect = if (hasContentPath) "content_path" else "NULL AS content_path"
            val originalPageNumberSelect =
                if (hasOriginalPageNumber) "original_page_number" else "NULL AS original_page_number"
            val contentWhere = if (hasContentPath) {
                "(content IS NOT NULL AND content != '') OR (content_path IS NOT NULL AND content_path != '')"
            } else {
                "content IS NOT NULL AND content != ''"
            }
            conn.createStatement().use { st ->
                st.fetchSize = 1000
                val rs = st.executeQuery(
                    """
                    SELECT book_id, page_num, content, $contentPathSelect, $originalPageNumberSelect
                    FROM pages
                    WHERE $contentWhere
                    ORDER BY book_id, page_num
                    """.trimIndent()
                )
                while (rs.next()) {
                    try {
                        val bookId = globalBookId(shardNumber, rs.getLong("book_id"))
                        val pageNum = rs.getInt("page_num")
                        val content = loadPageContent(
                            shardPath = shardPath,
                            inlineContent = rs.getString("content"),
                            contentPath = rs.getString("content_path"),
                        ) ?: continue
                        val orig = rs.getString("original_page_number")
                        val meta = booksMeta[bookId] ?: continue

                        indexer.addPage(
                            page = Page(
                                bookId = bookId,
                                pageNumber = pageNum,
                                originalPageNumber = orig,
                                content = content,
                            ),
                            bookTitle = meta.title,
                            author = meta.author,
                            category = meta.category,
                            year = meta.year,
                            deathYear = meta.deathYear,
                            country = meta.country,
                        )
                        pages++
                        if (pages % 1000L == 0L) {
                            progressCallback(Progress(shardPath.fileName.toString(), books, pages, errors))
                        }
                    } catch (e: Exception) {
                        errors++
                        if (errors <= 5) logger.warn("  page error: {}", e.message)
                    }
                }
            }
        }
        return ShardStats(books, pages, errors)
    }

    private data class BookMeta(
        val title: String,
        val author: String?,
        val category: String?,
        val year: String?,
        val deathYear: String?,
        val country: String?,
    )

    /** تقدّم الترحيل لكل دفعة. */
    data class Progress(
        val currentShard: String,
        val booksDone: Long,
        val pagesDone: Long,
        val errors: Long,
    )

    /** الإحصاءات النهائية. */
    data class Stats(
        val shardCount: Int,
        val totalBooks: Long,
        val totalPages: Long,
        val totalErrors: Long,
        val onDiskDocs: Int = -1,
    ) {
        override fun toString(): String = """
            === Migration Summary ===
            Shards processed : $shardCount
            Books migrated   : $totalBooks
            Pages migrated   : $totalPages
            Errors           : $totalErrors
            On-disk docs     : $onDiskDocs
            ========================
        """.trimIndent()
    }

    private fun shardNumber(shardPath: Path): Long {
        return shardFileRegex.find(shardPath.fileName.toString())
            ?.groupValues
            ?.getOrNull(1)
            ?.toLongOrNull()
            ?: 0L
    }

    private fun globalBookId(shardNumber: Long, localBookId: Long): Long {
        return shardNumber * BOOK_ID_STRIDE + localBookId
    }

    private fun tableColumns(conn: Connection, tableName: String): Set<String> {
        val columns = mutableSetOf<String>()
        conn.createStatement().use { st ->
            st.executeQuery("PRAGMA table_info($tableName)").use { rs ->
                while (rs.next()) columns += rs.getString("name")
            }
        }
        return columns
    }

    private fun loadPageContent(shardPath: Path, inlineContent: String?, contentPath: String?): String? {
        if (!contentPath.isNullOrBlank()) {
            readExternalContent(shardPath, contentPath)?.takeIf { it.isNotBlank() }?.let { return it }
        }
        return inlineContent?.takeIf { it.isNotBlank() }
    }

    private fun readExternalContent(shardPath: Path, contentPath: String): String? {
        val rawPath = Paths.get(contentPath)
        val candidates = if (rawPath.isAbsolute) {
            listOf(rawPath)
        } else {
            listOf(
                shardPath.parent.resolve("content").resolve(rawPath),
                shardPath.parent.resolve(rawPath),
            )
        }
        return candidates
            .map { it.normalize() }
            .firstOrNull { Files.isRegularFile(it) }
            ?.let { Files.readString(it) }
    }

    private fun countDocsOnDisk(indexDir: Path): Int {
        return try {
            org.apache.lucene.store.FSDirectory.open(indexDir).use { dir ->
                org.apache.lucene.index.DirectoryReader.open(dir).use { reader ->
                    reader.numDocs()
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to count docs on disk: {}", e.message)
            -1
        }
    }

    private fun deleteRecursively(path: Path) {
        if (!Files.exists(path)) return
        Files.walk(path).use { stream ->
            stream.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
        }
    }
}

/**
 * نقطة دخول CLI:
 *   ./gradlew :importer:migrate --args="<shardsDir> <outputIndexDir>"
 */
fun main(args: Array<String>) {
    System.setProperty("file.encoding", "UTF-8")
    if (args.size < 2) {
        System.err.println("Usage: MigrationTool <shardsDir> <outputIndexDir>")
        System.err.println("Example:")
        System.err.println("""  MigrationTool "E:\البرامج والتطبيقات\المكتبة البحثية\data\shards" "%APPDATA%\Bahthia\lucene-index"""")
        exitProcess(1)
    }
    val shardsDir = Paths.get(args[0])
    val outputIndexDir = Paths.get(args[1])

    println("Source : $shardsDir")
    println("Target : $outputIndexDir")
    println()

    val started = System.currentTimeMillis()
    val stats = MigrationTool.migrate(
        shardsDir = shardsDir,
        outputIndexDir = outputIndexDir,
        progressCallback = { p ->
            println("  [${p.currentShard}] pages: ${p.pagesDone}, errors: ${p.errors}")
        },
    )
    val elapsed = (System.currentTimeMillis() - started) / 1000
    println()
    println(stats.toString())
    println("Elapsed: ${elapsed}s")
}
