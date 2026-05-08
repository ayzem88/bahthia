package com.bahthia.app.ui.screens.result

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bahthia.app.ui.components.BrownButton
import com.bahthia.domain.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.poi.xwpf.usermodel.ParagraphAlignment
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFParagraph
import org.apache.poi.xwpf.usermodel.XWPFRun
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * تصدير نتائج البحث إلى ملفّ. ٣ صيغ: TXT، CSV، DOCX.
 *
 * @param pageProvider دالّة تَجلب نصّ الصّفحة الكامل من الفهرس (لِبناء شاهد ٣٠+٣٠ كلمة).
 *                     قد تُعيد `null` لو تَعذَّر — حينئذٍ نَستعمل `contextSnippet` المُختصَر.
 */
@Composable
fun ExportDialog(
    results: List<SearchResult>,
    query: String,
    pageProvider: (bookId: Long, pageNumber: Int) -> String? = { _, _ -> null },
    onClose: () -> Unit,
) {
    var format by remember { mutableStateOf(ExportFormat.DOCX) }
    var working by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf<Path?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Dialog(
        onDismissRequest = { if (!working) onClose() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.widthIn(min = 420.dp, max = 600.dp).padding(16.dp),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    "تصدير النتائج",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "سيُحفظ ملفّ بـ ${results.size} نتيجة على سطح المكتب.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))

                Text("الصيغة:", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                ExportFormat.entries.forEach { fmt ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = format == fmt, onClick = { format = fmt })
                        Text(text = fmt.label, modifier = Modifier.padding(start = 4.dp))
                    }
                }

                Spacer(Modifier.height(16.dp))

                if (working) {
                    Text("جارٍ التصدير…", color = MaterialTheme.colorScheme.primary)
                }
                done?.let {
                    Text("✅ حُفظ في:\n$it", style = MaterialTheme.typography.bodySmall)
                }
                error?.let {
                    Text("❌ $it", color = MaterialTheme.colorScheme.error)
                }

                Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    BrownButton(text = "إغلاق", onClick = onClose, enabled = !working)
                    Spacer(Modifier.height(0.dp))
                    if (done == null) {
                        BrownButton(
                            text = "تصدير",
                            onClick = {
                                working = true
                                error = null
                                scope.launch {
                                    try {
                                        val path = withContext(Dispatchers.IO) {
                                            performExport(results, query, format, pageProvider)
                                        }
                                        done = path
                                    } catch (e: Exception) {
                                        error = e.message ?: "خطأ غير معروف"
                                    } finally {
                                        working = false
                                    }
                                }
                            },
                            enabled = !working && results.isNotEmpty(),
                        )
                    }
                }
            }
        }
    }
}

enum class ExportFormat(val label: String, val ext: String) {
    DOCX("مستند Word (.docx)", "docx"),
    TXT("نصّ عاديّ (.txt)", "txt"),
    CSV("جدول (.csv)", "csv"),
}

// ─────────────────────────── ثوابت التّنسيق ───────────────────────────

/** اسم الخطّ — مطابق لِما يَستعمله القارئ في التّطبيق. */
private const val FONT_NAME = "Sakkal Majalla"

/** حجم الخطّ بِالنّقاط (pt) — كَما طلب المستخدم. */
private const val FONT_SIZE_PT = 14

/** عدد الكلمات قبل/بعد الكلمة المبحوث عنها في الشّاهد المُصدَّر. */
private const val EXPORT_WORDS_BEFORE = 30
private const val EXPORT_WORDS_AFTER  = 30

// ─────────────────────────── منطق التّصدير ───────────────────────────

private fun performExport(
    results: List<SearchResult>,
    query: String,
    fmt: ExportFormat,
    pageProvider: (Long, Int) -> String?,
): Path {
    val desktop = Path.of(System.getProperty("user.home"), "Desktop")
    Files.createDirectories(desktop)
    val ts = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now())
    val safeQuery = query.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(40)
    val filename = "بحث_${safeQuery}_$ts.${fmt.ext}"
    val target = desktop.resolve(filename)

    when (fmt) {
        ExportFormat.TXT  -> writeTxt(target, results, query, pageProvider)
        ExportFormat.CSV  -> writeCsv(target, results, pageProvider)
        ExportFormat.DOCX -> writeDocx(target, results, query, pageProvider)
    }
    return target
}

/**
 * يَبني شاهداً موسَّعاً (٣٠ كلمة قبل + الكلمة + ٣٠ كلمة بعد) من نَصّ الصّفحة الكامل.
 * يَعود إلى `fallbackSnippet` لو تَعذَّر جَلب الصّفحة أو إيجاد المطابقة.
 */
private fun buildExportSnippet(
    fullPage: String?,
    matchedTerm: String,
    fallbackSnippet: String,
): String {
    if (fullPage.isNullOrBlank() || matchedTerm.isBlank()) return fallbackSnippet
    val idx = findMatchIndex(fullPage, matchedTerm)
    if (idx < 0) return fallbackSnippet

    // نَفصل النّصّ إلى كلمات مع الاحتفاظ بِمواقعها لِنَعرف أيّ كلمة تَحتوي المطابقة.
    val words = wordSpans(fullPage)
    if (words.isEmpty()) return fallbackSnippet
    val matchWordIndex = words.indexOfFirst { (s, e) -> idx in s until e }
        .let { if (it >= 0) it else words.indexOfFirst { (s, _) -> s >= idx } }
    if (matchWordIndex < 0) return fallbackSnippet

    val from = (matchWordIndex - EXPORT_WORDS_BEFORE).coerceAtLeast(0)
    val to   = (matchWordIndex + EXPORT_WORDS_AFTER).coerceAtMost(words.lastIndex)
    val sliceStart = words[from].first
    val sliceEnd   = words[to].second
    val core = fullPage.substring(sliceStart, sliceEnd).trim()

    val prefix = if (from > 0) "… " else ""
    val suffix = if (to < words.lastIndex) " …" else ""
    return prefix + core + suffix
}

/** يُعيد قائمة (start, end) لِكلّ كلمة في النّصّ — تَفصلها مَسافات/علامات ترقيم. */
private fun wordSpans(text: String): List<Pair<Int, Int>> {
    val out = ArrayList<Pair<Int, Int>>()
    var i = 0
    while (i < text.length) {
        // تَخطّى الفَواصل
        while (i < text.length && !text[i].isLetterOrDigit()) i++
        if (i >= text.length) break
        val start = i
        while (i < text.length && text[i].isLetterOrDigit()) i++
        out += start to i
    }
    return out
}

/** يَجد موقع المطابقة الأوّلى — يُحاول regex أوّلاً، ثُمّ بَحث نَصّيّ بسيط. */
private fun findMatchIndex(text: String, term: String): Int {
    // قد تَكون matchedTerm كلمة عاديّة أو نَمط مع `|` للتّقارب — نَأخذ أوّل
    // جزء قبل أيّ فاصل لِنَجد كلمة فِعليّة في النّصّ.
    val firstWord = term.split('|', '+', ' ').map { it.trim() }.firstOrNull { it.isNotBlank() }
        ?: return -1
    val direct = text.indexOf(firstWord)
    if (direct >= 0) return direct
    // مُحاولة أَخيرة: بِدون تَشكيل
    val stripped = stripDiacritics(firstWord)
    val strippedText = stripDiacritics(text)
    val pos = strippedText.indexOf(stripped)
    return pos
}

private val DIACRITICS = Regex("[ً-ْٰـ]")
private fun stripDiacritics(s: String): String = DIACRITICS.replace(s, "")

// ─────────────────────────── كَتابة TXT/CSV ───────────────────────────

private fun writeTxt(
    target: Path,
    results: List<SearchResult>,
    query: String,
    pageProvider: (Long, Int) -> String?,
) {
    val sb = StringBuilder()
    sb.appendLine("نتائج البحث عن: $query")
    sb.appendLine("عدد النتائج: ${results.size}")
    sb.appendLine("تاريخ التصدير: ${LocalDateTime.now()}")
    sb.appendLine("=".repeat(60))
    sb.appendLine()
    results.forEachIndexed { i, r ->
        sb.appendLine("[${i + 1}] ${r.bookTitle ?: "—"} — صفحة ${r.originalPageNumber ?: r.pageNumber}")
        if (!r.bookAuthor.isNullOrBlank()) sb.appendLine("    المؤلّف: ${r.bookAuthor}")
        if (!r.bookYear.isNullOrBlank())   sb.appendLine("    السنة: ${r.bookYear}")
        sb.appendLine()
        val snippet = buildExportSnippet(pageProvider(r.bookId, r.pageNumber), r.matchedTerm, r.contextSnippet)
        sb.appendLine(snippet)
        sb.appendLine()
        sb.appendLine("-".repeat(60))
    }
    Files.writeString(target, sb.toString(), Charsets.UTF_8,
        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
}

private fun writeCsv(target: Path, results: List<SearchResult>, pageProvider: (Long, Int) -> String?) {
    val sb = StringBuilder()
    sb.append("﻿")
    sb.appendLine("المسلسل,الكتاب,المؤلّف,الفنّ,السنة,الصفحة,المطابقة,السياق")
    results.forEachIndexed { i, r ->
        val snippet = buildExportSnippet(pageProvider(r.bookId, r.pageNumber), r.matchedTerm, r.contextSnippet)
        sb.appendLine(
            listOf(
                (i + 1).toString(),
                csvEscape(r.bookTitle),
                csvEscape(r.bookAuthor),
                csvEscape(r.bookCategory),
                csvEscape(r.bookYear),
                (r.originalPageNumber ?: r.pageNumber.toString()),
                csvEscape(r.matchedTerm),
                csvEscape(snippet),
            ).joinToString(",")
        )
    }
    Files.writeString(target, sb.toString(), Charsets.UTF_8,
        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
}

private fun csvEscape(s: String?): String {
    val v = s ?: ""
    return if (v.contains(',') || v.contains('"') || v.contains('\n'))
        "\"" + v.replace("\"", "\"\"") + "\""
    else v
}

// ─────────────────────────── كَتابة DOCX ───────────────────────────

/**
 * يُنتج مستند Word مُنسَّقاً عربيّاً (RTL) بِخَطّ Sakkal Majalla 14pt،
 * مع تَلوين الكلمة المبحوث عنها بِالأحمر أينما ظَهرت في الشّاهد.
 */
private fun writeDocx(
    target: Path,
    results: List<SearchResult>,
    query: String,
    pageProvider: (Long, Int) -> String?,
) {
    XWPFDocument().use { doc ->
        // ─── الترويسة ───
        rtlParagraph(doc).also { p ->
            arabicRun(p, "نتائج البحث عن: ").apply { isBold = true }
            arabicRun(p, query).apply { isBold = true; color = "C00000" }
        }
        rtlParagraph(doc).also { p ->
            arabicRun(p, "عدد النتائج: ${results.size}")
        }
        rtlParagraph(doc).also { p ->
            arabicRun(p, "تاريخ التصدير: " + DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                .format(LocalDateTime.now()))
        }
        rtlParagraph(doc).also { p ->
            arabicRun(p, "―".repeat(30))
        }

        // ─── النّتائج ───
        results.forEachIndexed { i, r ->
            // عنوان الكتاب + الصّفحة
            rtlParagraph(doc).also { p ->
                arabicRun(p, "[${i + 1}] ").apply { isBold = true }
                arabicRun(p, r.bookTitle ?: "—").apply { isBold = true }
                arabicRun(p, " — صفحة ${r.originalPageNumber ?: r.pageNumber}").apply { isBold = true }
            }
            if (!r.bookAuthor.isNullOrBlank()) {
                rtlParagraph(doc).also { p -> arabicRun(p, "المؤلّف: ${r.bookAuthor}") }
            }
            if (!r.bookYear.isNullOrBlank()) {
                rtlParagraph(doc).also { p -> arabicRun(p, "السنة: ${r.bookYear}") }
            }

            // الشّاهد ٣٠+٣٠ مع تَلوين الكلمة المبحوث عنها بِالأحمر
            val snippet = buildExportSnippet(pageProvider(r.bookId, r.pageNumber), r.matchedTerm, r.contextSnippet)
            rtlParagraph(doc).also { p ->
                writeHighlightedSnippet(p, snippet, r.matchedTerm)
            }

            // فاصل
            doc.createParagraph()
        }

        FileOutputStream(target.toFile()).use { doc.write(it) }
    }
}

/** يُنشِئ فقرة جَديدة بِاتّجاه RTL ومُحاذاة يَمينيّة. */
private fun rtlParagraph(doc: XWPFDocument): XWPFParagraph {
    val p = doc.createParagraph()
    p.alignment = ParagraphAlignment.RIGHT
    // Bidi (اتّجاه RTL) — السّمة العامّة لِلفقرة
    try {
        val pPr = p.ctp.pPr ?: p.ctp.addNewPPr()
        if (!pPr.isSetBidi) pPr.addNewBidi()
    } catch (_: Throwable) { /* المُحاذاة وَحدها كَافية إن فَشل ضَبط bidi */ }
    return p
}

/** يُنشِئ run بِخَطّ Sakkal Majalla 14pt مع وسم complex-script (rtl + cs). */
private fun arabicRun(p: XWPFParagraph, text: String): XWPFRun {
    val r = p.createRun()
    r.fontFamily = FONT_NAME
    // POI يَضبط الخَطّ لِكُلّ النّطاقات — بِما فيها cs (complex-script) المُهمّة لِلعَرَبيّة
    try {
        r.setFontFamily(FONT_NAME, XWPFRun.FontCharRange.cs)
    } catch (_: Throwable) { /* ignore */ }
    r.fontSize = FONT_SIZE_PT
    // حَجم النّصّ المعقَّد (szCs) — Word يَستعمله لِلعَرَبيّة. نَضبطه يَدويّاً
    // عَبر CTRPr لِأنّ setFontSize لا يَلمسه. (النّصف-نِقاط: 14pt = 28)
    try {
        val rPr = r.ctr.rPr ?: r.ctr.addNewRPr()
        val szCs = if (rPr.sizeOfSzCsArray() > 0) rPr.getSzCsArray(0) else rPr.addNewSzCs()
        szCs.`val` = java.math.BigInteger.valueOf((FONT_SIZE_PT * 2).toLong())
        if (rPr.sizeOfRtlArray() == 0) rPr.addNewRtl()
    } catch (_: Throwable) { /* ignore — sz العَاديّ يَكفي لِلعَرض */ }
    r.setText(text)
    return r
}

/**
 * يَكتب الشّاهد إلى الفقرة، ويُلوِّن كلّ ظهور لِلكلمة المبحوث عنها بِالأحمر — Bold.
 * الكلمة قد تَأتي مُشكَّلةً في النّصّ بَينما العَين بِدون تَشكيل (أو العَكس)؛ نُطابق
 * بَعد إِزالة التّشكيل.
 */
private fun writeHighlightedSnippet(p: XWPFParagraph, snippet: String, matchedTerm: String) {
    if (matchedTerm.isBlank()) {
        arabicRun(p, snippet)
        return
    }
    // نَأخذ كَلمات البحث (قد تَكون مفصولة بـ + أو | أو مَسافة)
    val terms = matchedTerm.split('|', '+', ' ')
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
    if (terms.isEmpty()) {
        arabicRun(p, snippet)
        return
    }

    val ranges = findAllRanges(snippet, terms)
    if (ranges.isEmpty()) {
        arabicRun(p, snippet)
        return
    }

    var cursor = 0
    for ((start, end) in ranges) {
        if (start > cursor) {
            arabicRun(p, snippet.substring(cursor, start))
        }
        arabicRun(p, snippet.substring(start, end)).apply {
            color = "C00000"   // أحمر داكن
            isBold = true
        }
        cursor = end
    }
    if (cursor < snippet.length) {
        arabicRun(p, snippet.substring(cursor))
    }
}

/** يَجد كلّ مَواقع المطابقة (مع تَجاهل التّشكيل) ويُعيدها مُرتَّبة بِدون تَداخل — كَأزواج (start, end). */
private fun findAllRanges(text: String, terms: List<String>): List<Pair<Int, Int>> {
    if (text.isEmpty()) return emptyList()
    val (stripped, mapping) = stripDiacriticsWithMap(text)
    val out = ArrayList<Pair<Int, Int>>()
    for (term in terms) {
        val (sTerm, _) = stripDiacriticsWithMap(term)
        if (sTerm.isEmpty()) continue
        var from = 0
        while (true) {
            val idx = stripped.indexOf(sTerm, from)
            if (idx < 0) break
            val origStart = mapping.getOrNull(idx) ?: break
            val origEnd   = mapping.getOrNull(idx + sTerm.length - 1)?.let { it + 1 } ?: break
            out += origStart to origEnd
            from = idx + sTerm.length
        }
    }
    if (out.isEmpty()) return emptyList()
    out.sortBy { it.first }
    val merged = ArrayList<Pair<Int, Int>>()
    var curStart = out[0].first
    var curEnd = out[0].second
    for (i in 1 until out.size) {
        val s = out[i].first
        val e = out[i].second
        if (s <= curEnd) curEnd = maxOf(curEnd, e)
        else {
            merged += curStart to curEnd
            curStart = s
            curEnd = e
        }
    }
    merged += curStart to curEnd
    return merged
}

/** يُعيد (نَصّ بِدون تَشكيل، خَريطة index بِدون تَشكيل → index في الأصل). */
private fun stripDiacriticsWithMap(text: String): Pair<String, IntArray> {
    val sb = StringBuilder(text.length)
    val map = IntArray(text.length)
    var j = 0
    for (i in text.indices) {
        val c = text[i]
        // نَتجاهل التّشكيل + التّطويل
        if ((c in 'ً'..'ْ') || c == 'ٰ' || c == 'ـ') continue
        sb.append(c)
        if (j < map.size) map[j] = i
        j++
    }
    val finalMap = IntArray(j)
    System.arraycopy(map, 0, finalMap, 0, j)
    return sb.toString() to finalMap
}
