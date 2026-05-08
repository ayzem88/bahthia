package com.bahthia.app.state

import com.bahthia.domain.SearchMode
import com.bahthia.domain.SearchResult
import com.bahthia.domain.TimeMode
import org.apache.poi.xwpf.usermodel.ParagraphAlignment
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFParagraph
import org.apache.poi.xwpf.usermodel.XWPFRun
import org.slf4j.LoggerFactory
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.chrono.HijrahDate
import java.time.format.DateTimeFormatter
import java.util.Properties
import java.util.UUID

/**
 * مَخزن جَلسات البَحث المحفوظة (المفضّلة) في مُجلَّد `favorites/`.
 *
 * كلّ جَلسة في ملفّ Properties مُستقلّ يَحمل اسم الـ`id`. هذا يُسهّل الإِضافة
 * والحَذف بدون قَفل عَلى ملفّ مَركزيّ.
 *
 * **الحدّ الأقصى: ٥٠ جَلسة** — عند تَجاوزه يُحذف الأَقدَم تَلقائيّاً.
 */
class SavedSearchStore(baseDir: Path) {

    private val logger = LoggerFactory.getLogger(SavedSearchStore::class.java)
    private val dir: Path = baseDir.resolve("favorites")

    init {
        try { Files.createDirectories(dir) } catch (_: Exception) { /* سَنُحاوِل عند الكِتابة */ }
    }

    /**
     * يُضيف جَلسة جَديدة. إن تَجاوز العَدد ٥٠ يُحذف الأَقدَم.
     * يُرجع `true` عند النَجاح.
     */
    fun add(saved: SavedSearch): Boolean {
        return try {
            Files.createDirectories(dir)
            writeFile(saved)
            enforceCap()
            true
        } catch (e: Exception) {
            logger.error("Failed to save favorite ${saved.id}", e)
            false
        }
    }

    /** قائمة كلّ الجَلسات مُرتَّبة من الأَحدث إلى الأَقدَم. */
    fun list(): List<SavedSearch> {
        if (!Files.exists(dir)) return emptyList()
        val files = try {
            Files.list(dir).use { it.toList() }
        } catch (e: Exception) {
            logger.warn("Failed to list favorites dir: ${e.message}")
            return emptyList()
        }
        return files
            .filter { it.fileName.toString().endsWith(".properties") }
            .mapNotNull { readFile(it) }
            .sortedByDescending { it.timestamp }
    }

    /** يَحذف جَلسة وَاحِدة بالـ id. يُرجع `true` إن وُجِدَت وحُذِفت. */
    fun delete(id: String): Boolean {
        return try {
            Files.deleteIfExists(filePathFor(id))
        } catch (e: Exception) {
            logger.warn("Failed to delete favorite $id: ${e.message}")
            false
        }
    }

    /** يَحذف كلّ الجَلسات. يُرجع عَدد المَحذوفات. */
    fun clear(): Int {
        if (!Files.exists(dir)) return 0
        var count = 0
        try {
            Files.list(dir).use { stream ->
                stream.toList().forEach { p ->
                    if (Files.deleteIfExists(p)) count++
                }
            }
        } catch (e: Exception) {
            logger.warn("Failed to clear favorites: ${e.message}")
        }
        return count
    }

    /** يُصدّر كلّ الجَلسات إلى ملفّ Word في [target]. */
    fun exportAllToDocx(target: Path): Boolean {
        return try {
            val items = list()
            writeDocx(target, items)
            true
        } catch (e: Exception) {
            logger.error("Failed to export favorites to docx", e)
            false
        }
    }

    // ─────────────────────────────────────────────────────────────
    // التَخزين على القُرص (Properties لكلّ جَلسة)
    // ─────────────────────────────────────────────────────────────

    private fun filePathFor(id: String): Path = dir.resolve("$id.properties")

    private fun writeFile(s: SavedSearch) {
        val props = Properties()
        props["id"] = s.id
        props["timestamp"] = s.timestamp.toString()
        props["name"] = s.name
        props["query"] = s.query
        props["mode"] = s.mode.name
        props["respectDiacritics"] = s.respectDiacritics.toString()
        props["matchWholeLetters"] = s.matchWholeLetters.toString()
        props["resultsLimit"] = s.resultsLimit.toString()
        props["selectedCategories"] = encodeStringSet(s.selectedCategories)
        props["selectedYears"] = encodeStringSet(s.selectedYears)
        props["selectedCountries"] = encodeStringSet(s.selectedCountries)
        props["selectedBookIds"] = s.selectedBookIds.joinToString(",")
        props["viewMode"] = s.viewMode.name
        props["timeMode"] = s.timeMode.name
        props["results.count"] = s.results.size.toString()
        s.results.forEachIndexed { i, r ->
            val k = "result.$i"
            props["$k.bookId"] = r.bookId.toString()
            props["$k.pageNumber"] = r.pageNumber.toString()
            props["$k.originalPageNumber"] = r.originalPageNumber ?: ""
            props["$k.matchedTerm"] = r.matchedTerm
            props["$k.matchPosition"] = r.matchPosition.toString()
            props["$k.contextSnippet"] = r.contextSnippet
            props["$k.bookTitle"] = r.bookTitle ?: ""
            props["$k.bookAuthor"] = r.bookAuthor ?: ""
            props["$k.bookCategory"] = r.bookCategory ?: ""
            props["$k.bookYear"] = r.bookYear ?: ""
            props["$k.relevance"] = r.relevance.toString()
        }

        Files.newOutputStream(filePathFor(s.id)).use { out ->
            props.store(out, "Bahthia saved search ${s.id}")
        }
    }

    private fun readFile(path: Path): SavedSearch? {
        return try {
            val props = Properties()
            Files.newInputStream(path).use { props.load(it) }

            val id = props.getProperty("id") ?: return null
            val timestamp = props.getProperty("timestamp")?.toLongOrNull() ?: return null
            val name = props.getProperty("name") ?: id
            val query = props.getProperty("query") ?: ""
            val mode = runCatching { SearchMode.valueOf(props.getProperty("mode", "WORD")) }
                .getOrDefault(SearchMode.WORD)
            val respectDiacritics = props.getProperty("respectDiacritics", "false").toBoolean()
            val matchWholeLetters = props.getProperty("matchWholeLetters", "false").toBoolean()
            val resultsLimit = props.getProperty("resultsLimit", "1000").toIntOrNull() ?: 1000
            val selectedCategories = decodeStringSet(props.getProperty("selectedCategories", ""))
            val selectedYears = decodeStringSet(props.getProperty("selectedYears", ""))
            val selectedCountries = decodeStringSet(props.getProperty("selectedCountries", ""))
            val selectedBookIds = props.getProperty("selectedBookIds", "")
                .split(",").filter { it.isNotBlank() }.mapNotNull { it.toLongOrNull() }.toSet()
            val viewMode = runCatching {
                LibraryViewModel.ViewMode.valueOf(props.getProperty("viewMode", "CATEGORIES"))
            }.getOrDefault(LibraryViewModel.ViewMode.CATEGORIES)
            val timeMode = runCatching {
                TimeMode.valueOf(props.getProperty("timeMode", "DEATH_YEAR"))
            }.getOrDefault(TimeMode.DEATH_YEAR)

            val resultsCount = props.getProperty("results.count", "0").toIntOrNull() ?: 0
            val results = (0 until resultsCount).mapNotNull { i ->
                val k = "result.$i"
                val bookId = props.getProperty("$k.bookId")?.toLongOrNull() ?: return@mapNotNull null
                val pageNumber = props.getProperty("$k.pageNumber")?.toIntOrNull() ?: return@mapNotNull null
                SearchResult(
                    bookId = bookId,
                    pageNumber = pageNumber,
                    originalPageNumber = props.getProperty("$k.originalPageNumber", "").ifBlank { null },
                    matchedTerm = props.getProperty("$k.matchedTerm", ""),
                    matchPosition = props.getProperty("$k.matchPosition", "-1").toIntOrNull() ?: -1,
                    contextSnippet = props.getProperty("$k.contextSnippet", ""),
                    bookTitle = props.getProperty("$k.bookTitle", "").ifBlank { null },
                    bookAuthor = props.getProperty("$k.bookAuthor", "").ifBlank { null },
                    bookCategory = props.getProperty("$k.bookCategory", "").ifBlank { null },
                    bookYear = props.getProperty("$k.bookYear", "").ifBlank { null },
                    relevance = props.getProperty("$k.relevance", "0")?.toFloatOrNull() ?: 0f,
                )
            }

            SavedSearch(
                id = id, timestamp = timestamp, name = name,
                query = query, mode = mode,
                respectDiacritics = respectDiacritics,
                matchWholeLetters = matchWholeLetters,
                resultsLimit = resultsLimit,
                selectedCategories = selectedCategories,
                selectedYears = selectedYears,
                selectedCountries = selectedCountries,
                selectedBookIds = selectedBookIds,
                viewMode = viewMode,
                timeMode = timeMode,
                results = results,
            )
        } catch (e: Exception) {
            logger.warn("Failed to read favorite ${path.fileName}: ${e.message}")
            null
        }
    }

    /** يَحذف الأَقدَم حتى يَبقى عَدد الجَلسات ≤ [MAX_FAVORITES]. */
    private fun enforceCap() {
        val all = list()
        if (all.size <= MAX_FAVORITES) return
        // list() مُرتَّبة من الأَحدث إلى الأَقدَم — نَحذف الذيل
        all.drop(MAX_FAVORITES).forEach { delete(it.id) }
    }

    // ─────────────────────────────────────────────────────────────
    // تَرميز Sets كَنَصّ (تَجنّباً لتَصادمات الفواصِل في القِيَم)
    // ─────────────────────────────────────────────────────────────

    private fun encodeStringSet(set: Set<String>): String =
        set.joinToString(SEP) { it.replace(SEP, " ") }

    private fun decodeStringSet(s: String): Set<String> =
        if (s.isBlank()) emptySet() else s.split(SEP).filter { it.isNotBlank() }.toSet()

    // ─────────────────────────────────────────────────────────────
    // تَصدير DOCX
    // ─────────────────────────────────────────────────────────────

    private fun writeDocx(target: Path, items: List<SavedSearch>) {
        XWPFDocument().use { doc ->
            // الترويسة الرئيسيّة
            rtlParagraph(doc).also { p ->
                arabicRun(p, "جَلسات البَحث المحفوظة").apply {
                    isBold = true; fontSize = 18
                }
            }
            rtlParagraph(doc).also { p ->
                arabicRun(p, "العَدد: ${items.size}")
            }
            rtlParagraph(doc).also { p ->
                arabicRun(p, "تاريخ التَصدير: " +
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").format(LocalDateTime.now()))
            }
            rtlParagraph(doc).also { arabicRun(it, "―".repeat(30)) }

            items.forEachIndexed { i, s ->
                rtlParagraph(doc).also { p ->
                    arabicRun(p, "[${i + 1}] ${s.name}").apply {
                        isBold = true; fontSize = 14; color = "C00000"
                    }
                }
                rtlParagraph(doc).also { p ->
                    arabicRun(p, "تاريخ الحِفظ: ${formatDateBoth(s.timestamp)}")
                }
                rtlParagraph(doc).also { p ->
                    arabicRun(p, "الكلمة: ").apply { isBold = true }
                    arabicRun(p, s.query)
                    arabicRun(p, "    نوع البحث: ").apply { isBold = true }
                    arabicRun(p, modeNameAr(s.mode))
                }
                if (s.selectedCategories.isNotEmpty()) {
                    rtlParagraph(doc).also { p ->
                        arabicRun(p, "الفئات: ").apply { isBold = true }
                        arabicRun(p, s.selectedCategories.joinToString("، "))
                    }
                }
                if (s.selectedYears.isNotEmpty()) {
                    rtlParagraph(doc).also { p ->
                        arabicRun(p, "السنوات: ").apply { isBold = true }
                        arabicRun(p, s.selectedYears.joinToString("، "))
                    }
                }
                if (s.selectedCountries.isNotEmpty()) {
                    rtlParagraph(doc).also { p ->
                        arabicRun(p, "الدُّول: ").apply { isBold = true }
                        arabicRun(p, s.selectedCountries.joinToString("، "))
                    }
                }
                rtlParagraph(doc).also { p ->
                    arabicRun(p, "عَدد النتائج: ${s.results.size}")
                }
                // أَوّل ١٠ نتائج كَمَلخّص
                s.results.take(10).forEachIndexed { ri, r ->
                    rtlParagraph(doc).also { p ->
                        arabicRun(p, "  ${ri + 1}. ").apply { isBold = true }
                        arabicRun(p, r.bookTitle ?: "—").apply { isBold = true }
                        arabicRun(p, " — ص ${r.originalPageNumber ?: r.pageNumber}")
                    }
                    if (r.contextSnippet.isNotBlank()) {
                        rtlParagraph(doc).also { p ->
                            arabicRun(p, "      ${r.contextSnippet.take(140)}")
                        }
                    }
                }
                if (s.results.size > 10) {
                    rtlParagraph(doc).also { p ->
                        arabicRun(p, "  … و ${s.results.size - 10} نَتيجة أُخرى")
                    }
                }
                rtlParagraph(doc).also { arabicRun(it, "―".repeat(30)) }
            }

            FileOutputStream(target.toFile()).use { doc.write(it) }
        }
    }

    private fun rtlParagraph(doc: XWPFDocument): XWPFParagraph {
        val p = doc.createParagraph()
        p.alignment = ParagraphAlignment.RIGHT
        try {
            val pPr = p.ctp.pPr ?: p.ctp.addNewPPr()
            if (!pPr.isSetBidi) pPr.addNewBidi()
        } catch (_: Throwable) { /* المُحاذاة وَحدها كَافية */ }
        return p
    }

    private fun arabicRun(p: XWPFParagraph, text: String): XWPFRun {
        val r = p.createRun()
        r.fontFamily = FONT_NAME
        try { r.setFontFamily(FONT_NAME, XWPFRun.FontCharRange.cs) } catch (_: Throwable) {}
        r.fontSize = FONT_SIZE_PT
        try {
            val rPr = r.ctr.rPr ?: r.ctr.addNewRPr()
            val szCs = if (rPr.sizeOfSzCsArray() > 0) rPr.getSzCsArray(0) else rPr.addNewSzCs()
            szCs.`val` = java.math.BigInteger.valueOf((FONT_SIZE_PT * 2).toLong())
            if (rPr.sizeOfRtlArray() == 0) rPr.addNewRtl()
        } catch (_: Throwable) {}
        r.setText(text)
        return r
    }

    private fun modeNameAr(mode: SearchMode): String = when (mode) {
        SearchMode.WORD -> "كلمة"
        SearchMode.PATTERN -> "وَزن صَرفيّ"
        SearchMode.DERIVATIVES -> "جَذر"
        SearchMode.REGEX -> "تَعبير نَمطيّ"
    }

    companion object {
        const val MAX_FAVORITES = 50

        // فاصِل غير شائع في النَصّ العَرَبيّ — حَرف Unit Separator (U+001F)
        private const val SEP = ""

        // إعدادات الخَطّ في DOCX (مُطابقة لِـ ExportDialog)
        private const val FONT_NAME = "Sakkal Majalla"
        private const val FONT_SIZE_PT = 14

        /**
         * يُولّد عُنواناً افتراضيّاً للجَلسة بِصيغة:
         * "البحث عن '<query>' — <hijri> هـ / <gregorian> م"
         */
        fun defaultName(query: String, timestamp: Long = System.currentTimeMillis()): String {
            val q = query.takeIf { it.isNotBlank() } ?: "(بدون كلمة)"
            return "البحث عن \"$q\" — ${formatDateBoth(timestamp)}"
        }

        /** "1447/11/02 هـ — 2026-05-08 م" */
        fun formatDateBoth(timestamp: Long): String {
            val instant = Instant.ofEpochMilli(timestamp)
            val zone = ZoneId.systemDefault()
            val gregorian = LocalDateTime.ofInstant(instant, zone).toLocalDate()
            val gregStr = DateTimeFormatter.ofPattern("yyyy-MM-dd").format(gregorian)
            val hijriStr = try {
                val hd = HijrahDate.from(gregorian)
                "%04d/%02d/%02d".format(hd.get(java.time.temporal.ChronoField.YEAR),
                    hd.get(java.time.temporal.ChronoField.MONTH_OF_YEAR),
                    hd.get(java.time.temporal.ChronoField.DAY_OF_MONTH))
            } catch (_: Throwable) { "؟" }
            return "$hijriStr هـ / $gregStr م"
        }

        fun newId(): String = UUID.randomUUID().toString()
    }
}
