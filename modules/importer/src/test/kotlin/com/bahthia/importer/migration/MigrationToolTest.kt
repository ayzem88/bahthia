package com.bahthia.importer.migration

import com.bahthia.search.BahthiaSearcher
import com.bahthia.domain.SearchOptions
import com.bahthia.search.LibraryStats
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.sql.DriverManager

/**
 * اختبارات [MigrationTool].
 *
 * تنشئ شاردات SQLite وهميّة بنفس بنية Python القديمة، ثم تُهاجرها
 * وتتحقّق أن النتائج موجودة في فهرس Lucene الناتج.
 */
class MigrationToolTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun migratesSampleShardsToLucene() {
        val shardsDir = tempDir.resolve("shards").also { Files.createDirectories(it) }
        val outputDir = tempDir.resolve("index")

        // إنشاء شاردتين تجريبيّتين
        createSampleShard(shardsDir.resolve("shard_0000.db"), startId = 1)
        createSampleShard(shardsDir.resolve("shard_0001.db"), startId = 100)

        val stats = MigrationTool.migrate(shardsDir = shardsDir, outputIndexDir = outputDir)

        assertEquals(2, stats.shardCount)
        assertEquals(4L, stats.totalBooks)        // كتابان لكل شاردة
        assertEquals(8L, stats.totalPages)        // صفحتان لكل كتاب
        assertEquals(0L, stats.totalErrors)

        // تحقّق من قابلية البحث في الفهرس الجديد
        BahthiaSearcher(outputDir).use { searcher ->
            val results = searcher.searchWord("الله")
            assertTrue(results.isNotEmpty(), "Should find indexed pages")
        }
    }

    @Test
    fun migrationOverwritesExistingIndex() {
        val shardsDir = tempDir.resolve("shards").also { Files.createDirectories(it) }
        val outputDir = tempDir.resolve("index").also { Files.createDirectories(it) }
        // ضع ملفاً مزيّفاً ليثبت أنّ المُهاجِر يمسحه
        Files.writeString(outputDir.resolve("dummy.txt"), "old data")
        createSampleShard(shardsDir.resolve("shard_0000.db"), startId = 1)

        MigrationTool.migrate(shardsDir = shardsDir, outputIndexDir = outputDir)

        assertTrue(!Files.exists(outputDir.resolve("dummy.txt")), "Old files should be removed")
    }

    @Test
    fun migrationKeepsBooksUniqueWhenShardLocalIdsCollide() {
        val shardsDir = tempDir.resolve("shards").also { Files.createDirectories(it) }
        val outputDir = tempDir.resolve("index")

        createSampleShard(shardsDir.resolve("shard_0000.db"), startId = 1)
        createSampleShard(shardsDir.resolve("shard_0001.db"), startId = 1)
        appendSearchToken(shardsDir.resolve("shard_0000.db"))
        appendSearchToken(shardsDir.resolve("shard_0001.db"))

        val stats = MigrationTool.migrate(shardsDir = shardsDir, outputIndexDir = outputDir)

        assertEquals(2, stats.shardCount)
        assertEquals(4L, stats.totalBooks)
        assertEquals(8L, stats.totalPages)
        LibraryStats(outputDir).use { libraryStats ->
            assertEquals(4, libraryStats.totalBooks())
            assertEquals(4, libraryStats.allBooks().map { it.id }.toSet().size)
        }

        BahthiaSearcher(outputDir).use { searcher ->
            val shardZeroBookOne = searcher.searchWord(
                "uniquetoken",
                SearchOptions(bookIds = listOf(1L)),
            )
            assertEquals(2, shardZeroBookOne.size)
            assertTrue(shardZeroBookOne.all { it.bookId == 1L })
        }
    }

    @Test
    fun migrationIndexesExternalPageContentFromPythonStorage() {
        val shardsDir = tempDir.resolve("shards").also { Files.createDirectories(it) }
        val outputDir = tempDir.resolve("index")
        val shardPath = shardsDir.resolve("shard_0000.db")
        val relativeContentPath = Path.of("book-1", "page-1.txt")
        val externalContentPath = shardsDir.resolve("content").resolve(relativeContentPath)

        createSampleShard(shardPath, startId = 1)
        Files.createDirectories(externalContentPath.parent)
        Files.writeString(externalContentPath, "full externalrare content from legacy storage")
        DriverManager.getConnection("jdbc:sqlite:${shardPath.toAbsolutePath()}").use { conn ->
            conn.prepareStatement("UPDATE pages SET content = ?, content_path = ? WHERE book_id = ? AND page_num = ?")
                .use { ps ->
                    ps.setString(1, "preview only")
                    ps.setString(2, relativeContentPath.toString())
                    ps.setLong(3, 1L)
                    ps.setInt(4, 1)
                    ps.executeUpdate()
                }
        }

        MigrationTool.migrate(shardsDir = shardsDir, outputIndexDir = outputDir)

        BahthiaSearcher(outputDir).use { searcher ->
            val results = searcher.searchWord("externalrare")
            assertEquals(1, results.size)
            assertTrue(results.first().contextSnippet.contains("externalrare"))
        }
    }

    private fun appendSearchToken(dbPath: Path) {
        DriverManager.getConnection("jdbc:sqlite:${dbPath.toAbsolutePath()}").use { conn ->
            conn.createStatement().use { st ->
                st.executeUpdate("UPDATE pages SET content = content || ' uniquetoken'")
            }
        }
    }

    /**
     * يبني شاردة SQLite بنفس بنية Python:
     *   جدول `books` بـ id, title, author, category, year, ...
     *   جدول `pages` بـ book_id, page_num, content, original_page_number
     */
    private fun createSampleShard(dbPath: Path, startId: Long) {
        val url = "jdbc:sqlite:${dbPath.toAbsolutePath()}"
        DriverManager.getConnection(url).use { conn ->
            conn.createStatement().use { st ->
                st.executeUpdate(
                    """
                    CREATE TABLE books (
                        id INTEGER PRIMARY KEY,
                        title TEXT NOT NULL,
                        author TEXT,
                        category TEXT,
                        subcategory TEXT,
                        publish_year INTEGER,
                        year TEXT,
                        pages_count INTEGER,
                        word_count INTEGER,
                        publication_dates TEXT,
                        publication_details TEXT,
                        metadata TEXT,
                        dynamic_fields TEXT,
                        created_at TEXT,
                        updated_at TEXT,
                        investigator TEXT,
                        translator TEXT,
                        publisher TEXT,
                        country TEXT,
                        edition TEXT,
                        volumes_count TEXT,
                        pages_count_meta TEXT,
                        death_year TEXT
                    )
                    """.trimIndent()
                )
                st.executeUpdate(
                    """
                    CREATE TABLE pages (
                        id INTEGER PRIMARY KEY,
                        book_id INTEGER NOT NULL,
                        page_num INTEGER NOT NULL,
                        content TEXT NOT NULL,
                        content_path TEXT,
                        formatted_content TEXT,
                        original_page_number TEXT
                    )
                    """.trimIndent()
                )
            }

            // كتابان
            conn.prepareStatement("INSERT INTO books(id, title, author, category, year) VALUES (?,?,?,?,?)").use { ps ->
                ps.setLong(1, startId)
                ps.setString(2, "كتاب رقم $startId")
                ps.setString(3, "مؤلف $startId")
                ps.setString(4, "اللغة")
                ps.setString(5, "0500")
                ps.executeUpdate()

                ps.setLong(1, startId + 1)
                ps.setString(2, "كتاب رقم ${startId + 1}")
                ps.setString(3, "مؤلف ${startId + 1}")
                ps.setString(4, "الفقه")
                ps.setString(5, "0600")
                ps.executeUpdate()
            }

            // صفحتان لكل كتاب
            conn.prepareStatement("INSERT INTO pages(book_id, page_num, content, original_page_number) VALUES (?,?,?,?)").use { ps ->
                listOf(startId to 1, startId to 2, startId + 1 to 1, startId + 1 to 2).forEach { (bookId, page) ->
                    ps.setLong(1, bookId)
                    ps.setInt(2, page)
                    ps.setString(3, "بسم الله الرحمن الرحيم. هذه صفحة $page من كتاب $bookId.")
                    ps.setString(4, page.toString())
                    ps.executeUpdate()
                }
            }
        }
    }
}
