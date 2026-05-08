package com.bahthia.app.ui.screens.display

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bahthia.app.state.SearchViewModel
import com.bahthia.app.ui.components.BrownButton
import com.bahthia.app.ui.components.LazyColumnWithScrollbar
import com.bahthia.app.ui.components.ScrollableColumn
import com.bahthia.app.ui.components.bahthiaHorizontalSplitter
import com.bahthia.app.ui.screens.result.BookCardDialog
import com.bahthia.app.ui.screens.result.BookReaderDialog
import com.bahthia.app.ui.screens.result.CitationDialog
import com.bahthia.app.ui.screens.result.ExportDialog
import com.bahthia.app.ui.screens.result.PageReaderDialog
import com.bahthia.app.ui.theme.LocalBahthiaColors
import com.bahthia.domain.SearchResult

/**
 * لوحة العرض — مطابقة لـ Python `DisplayPanel.create`.
 *
 * البنية:
 *  1. ترويسة: 📄 (بطاقة الكتاب) + ★ (حفظ جلسة) + «» (اقتباس) + عنوان + ✕
 *  2. منطقة عرض النصّ المختار (٦٠٪)
 *  3. جدول النتائج بـ ٥ أعمدة (٤٠٪): مسلسل، السّياق، الكتاب، السنة، الصّفحة
 *  4. شريط أزرار سفلي: المجموعة التالية، عرض كلّ النتائج، عرض الكتاب، عرض الصفحة، التالية، السابقة، تصدير
 */
@Composable
fun DisplayPanel(
    viewModel: SearchViewModel,
    /**
     * يُستدعى عند نَقر زرّ ★ — يَحفظ الجَلسة الحاليّة في المفضّلة.
     * يُرجع `true` عند النَجاح، `false` عند الفَشل، `null` إن لم يَكن ثَمّة نَتائج لحفظها.
     */
    onSaveFavorite: (() -> Boolean)? = null,
) {
    var showCardFor by remember { mutableStateOf<Long?>(null) }
    var showCitationFor by remember { mutableStateOf<SearchResult?>(null) }
    var showBookFor by remember { mutableStateOf<SearchResult?>(null) }
    var showExport by remember { mutableStateOf(false) }
    var savedNotice by remember { mutableStateOf<String?>(null) }

    // Ctrl+E من Main.kt → يَزيد exportSignal فنفتح الحوار.
    LaunchedEffect(viewModel.exportSignal) {
        if (viewModel.exportSignal > 0 && viewModel.results.isNotEmpty()) {
            showExport = true
        }
    }

    val current: () -> SearchResult? =
        { viewModel.results.getOrNull(viewModel.selectedResultIndex) }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp)) {
        DisplayHeader(
            viewModel = viewModel,
            onShowCard = { current()?.let { showCardFor = it.bookId } },
            onSaveSession = {
                // ★ يَحفظ الحالة الكامِلة (الكلمة + المُرشّحات + النتائج) كَجَلسة في المفضّلة
                if (viewModel.results.isEmpty()) {
                    savedNotice = "ابحث أوّلاً ثمّ احفظ الجَلسة"
                } else if (onSaveFavorite == null) {
                    savedNotice = "ميزة الحفظ غير مُهيّأة"
                } else {
                    savedNotice = if (onSaveFavorite()) "تَمّ حفظ الجَلسة في المفضّلة"
                                  else                  "تَعذّر حفظ الجَلسة"
                }
            },
            onCite = { current()?.let { showCitationFor = it } },
        )
        Spacer(Modifier.height(5.dp))

        // فاصل أفقيّ قابل للسحب بين العَرض والجدول — يَتذكّر موقعه بين الجلسات
        @OptIn(org.jetbrains.compose.splitpane.ExperimentalSplitPaneApi::class)
        run {
            val prefs = com.bahthia.app.state.AppRuntime.preferences
            val vSplit = org.jetbrains.compose.splitpane.rememberSplitPaneState(prefs.splitDisplayVertical)
            com.bahthia.app.ui.components.ObserveSplitterPosition(vSplit) { prefs.splitDisplayVertical = it }
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                org.jetbrains.compose.splitpane.VerticalSplitPane(
                    splitPaneState = vSplit,
                ) {
                    first(minSize = 120.dp) { TextViewer(viewModel) }
                    second(minSize = 120.dp) { ResultsTable(viewModel) }
                    splitter { bahthiaHorizontalSplitter() }
                }
            }
        }

        Spacer(Modifier.height(5.dp))

        BottomToolbar(
            viewModel = viewModel,
            onShowBook  = { current()?.let { showBookFor = it } },
            onExport    = { if (viewModel.results.isNotEmpty()) showExport = true },
        )
    }

    // ─── الحوارات ───
    showCardFor?.let { id ->
        BookCardDialog(viewModel = viewModel, bookId = id, onClose = { showCardFor = null })
    }
    showCitationFor?.let { r ->
        CitationDialog(result = r, onClose = { showCitationFor = null })
    }
    showBookFor?.let { r ->
        BookReaderDialog(
            viewModel = viewModel,
            bookId = r.bookId,
            bookTitle = r.bookTitle,
            initialPageNumber = r.pageNumber,
            highlightTerm = r.matchedTerm.takeIf { it.isNotBlank() },
            onClose = { showBookFor = null },
        )
    }
    if (showExport) {
        ExportDialog(
            results = viewModel.results.toList(),
            query = viewModel.query,
            pageProvider = { bookId, page -> viewModel.fetchPage(bookId, page)?.content },
            onClose = { showExport = false },
        )
    }
    savedNotice?.let { msg ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { savedNotice = null },
            title = { Text("ملاحظة") },
            text = { Text(msg) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { savedNotice = null }) { Text("حسناً") }
            },
        )
    }
}

@Composable
private fun DisplayHeader(
    viewModel: SearchViewModel,
    onShowCard: () -> Unit,
    onSaveSession: () -> Unit,
    onCite: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(5.dp),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeaderIconButton(icon = Icons.Filled.Description, tooltip = com.bahthia.i18n.tr("display.header.bookCard.tooltip"), onClick = onShowCard)
            HeaderIconButton(icon = Icons.Filled.Star,        tooltip = com.bahthia.i18n.tr("display.header.saveSession.tooltip"), onClick = onSaveSession)
            HeaderIconButton(icon = Icons.Filled.FormatQuote, tooltip = com.bahthia.i18n.tr("display.header.cite.tooltip"), onClick = onCite)

            Spacer(Modifier.width(8.dp))

            Text(
                text = headerText(viewModel),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )

            Spacer(Modifier.width(8.dp))

            HeaderIconButton(icon = Icons.Filled.Close, tooltip = com.bahthia.i18n.tr("display.header.close.tooltip"), onClick = { viewModel.reset() })
        }
    }
}

@Composable
private fun HeaderIconButton(icon: ImageVector, tooltip: String, onClick: () -> Unit) {
    com.bahthia.app.ui.components.BahthiaTooltip(text = tooltip) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = Color.Transparent,
        modifier = Modifier
            .padding(horizontal = 2.dp)
            .clickable(onClick = onClick),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = tooltip,
            tint = Color.White,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp).size(20.dp),
        )
    }
    }
}

private fun headerText(viewModel: SearchViewModel): String {
    val total = viewModel.results.size
    if (total == 0) return com.bahthia.i18n.tr("display.header.title")
    val current = viewModel.selectedResultIndex + 1
    val visible = viewModel.visibleResultCount()
    return "${com.bahthia.i18n.tr("display.header.title")} — $current / $visible / $total"
}

@Composable
private fun TextViewer(viewModel: SearchViewModel) {
    val colors = LocalBahthiaColors.current
    val page = viewModel.displayedPage
    val selected = viewModel.results.getOrNull(viewModel.selectedResultIndex)

    val scrollState = androidx.compose.foundation.rememberScrollState()

    // عند تغيُّر الصفحة المعروضة (نتيجة جديدة، أو تنقّل) نُمرّر تلقائياً.
    androidx.compose.runtime.LaunchedEffect(viewModel.displayPageVersion) {
        if (page == null) return@LaunchedEffect
        if (viewModel.isBrowsingPages || selected == null) {
            // وضع تصفّح: نَبدأ من الأعلى
            scrollState.scrollTo(0)
        } else {
            // وضع نتيجة: نُمرّر إلى موضع المطابقة المختارة (تقريبيّاً)
            val pos = selected.matchPosition.coerceAtLeast(0)
            val total = page.content.length.coerceAtLeast(1)
            val fraction = pos.toFloat() / total
            // ننتظر إطاراً ليُحسب maxValue ثمّ نُمرّر
            kotlinx.coroutines.yield()
            val target = (scrollState.maxValue * fraction).toInt()
            scrollState.animateScrollTo(target.coerceIn(0, scrollState.maxValue))
        }
    }

    Surface(
        shape = RoundedCornerShape(5.dp),
        color = Color.White,
        border = BorderStroke(2.dp, Color(0xFFDDDDDD)),
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // شريط معلوماتي أعلى منطقة العرض
            BrowseBreadcrumb(viewModel)

            if (page == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = com.bahthia.i18n.tr("display.empty"),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    )
                }
                return@Column
            }

            val highlighted = if (viewModel.isBrowsingPages || selected == null) {
                AnnotatedString(page.content)
            } else {
                highlightAllOccurrences(page.content, selected.matchedTerm, colors.highlight)
            }

            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier.fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(start = 12.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
                ) {
                    Text(
                        text = highlighted,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                androidx.compose.foundation.VerticalScrollbar(
                    adapter = androidx.compose.foundation.rememberScrollbarAdapter(scrollState),
                    style = com.bahthia.app.ui.components.BahthiaScrollbarStyle,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxHeight()
                        .padding(vertical = 2.dp),
                )
            }
        }
    }
}

/** شريط معلوماتي صغير يُظهر الكتاب والصفحة، مع وسم "تصفّح" عند التنقّل. */
@Composable
private fun BrowseBreadcrumb(viewModel: SearchViewModel) {
    val page = viewModel.displayedPage ?: return
    val title = viewModel.displayedBookTitle ?: ""
    val pageNum = page.originalPageNumber ?: page.pageNumber.toString()
    val total = if (viewModel.displayedBookPagesCount > 0) "/${viewModel.displayedBookPagesCount}" else ""
    val mode = if (viewModel.isBrowsingPages) com.bahthia.i18n.tr("display.breadcrumb.browse")
               else com.bahthia.i18n.tr("display.breadcrumb.result")
    val pageLabel = com.bahthia.i18n.tr("display.breadcrumb.page")
    val text = "$mode — $pageLabel $pageNum$total" + (if (title.isNotBlank()) " · $title" else "")

    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

/**
 * يَأخذ snippet ويُرجِع نافذةً قصيرة حَول اللَفظ الشاهد:
 * ‎[wordsBefore كلمات] [اللَفظ الشاهد] [wordsAfter كلمات].
 *
 * إن لم يُعثَر على اللَفظ الشاهد (حالة نادرة في بَحث regex مَثلاً)
 * نَأخذ بِداية الـsnippet كـfallback.
 */
private fun windowAroundTerm(
    snippet: String,
    term: String,
    wordsBefore: Int,
    wordsAfter: Int,
): String {
    val flat = snippet.replace("\n", " ").trim()
    if (term.isBlank() || flat.isBlank()) return flat
    val pos = flat.indexOf(term)
    if (pos < 0) return flat
    val pre = flat.substring(0, pos).trim()
        .split(Regex("""\s+"""))
        .filter { it.isNotEmpty() }
        .takeLast(wordsBefore)
        .joinToString(" ")
    val post = flat.substring(pos + term.length).trim()
        .split(Regex("""\s+"""))
        .filter { it.isNotEmpty() }
        .take(wordsAfter)
        .joinToString(" ")
    return buildString {
        if (pre.isNotEmpty()) { append(pre); append(' ') }
        append(term)
        if (post.isNotEmpty()) { append(' '); append(post) }
    }
}

/** يُظلّل كلّ مطابقات [term] في النصّ — لا أوّل واحدة فقط. */
private fun highlightAllOccurrences(
    text: String,
    term: String,
    highlightColor: Color,
): AnnotatedString {
    if (term.isBlank()) return AnnotatedString(text)
    return buildAnnotatedString {
        var idx = 0
        while (idx < text.length) {
            val pos = text.indexOf(term, idx)
            if (pos < 0) {
                append(text.substring(idx))
                break
            }
            append(text.substring(idx, pos))
            val start = length
            append(term)
            addStyle(
                androidx.compose.ui.text.SpanStyle(background = highlightColor, fontWeight = FontWeight.Bold),
                start,
                length,
            )
            idx = pos + term.length
        }
    }
}

@Composable
private fun ResultsTable(viewModel: SearchViewModel) {
    Surface(
        shape = RoundedCornerShape(5.dp),
        color = Color.White,
        border = BorderStroke(2.dp, Color(0xFFDDDDDD)),
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TableHeader()
            HorizontalDivider(color = Color(0xFFF5F0EB), thickness = 1.dp)

            if (viewModel.isSearching) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "جارٍ البحث…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        )
                    }
                }
            } else if (viewModel.lastError != null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(16.dp),
                    ) {
                        Text(
                            text = com.bahthia.i18n.tr("table.error.title"),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = viewModel.lastError ?: "—",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        )
                    }
                }
            } else if (viewModel.results.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(16.dp),
                    ) {
                        val msg = if (viewModel.query.isBlank())
                            "اكتب كلمة في شريط البحث لتبدأ"
                        else
                            "لا توجد نتائج — جرّب تخفيف \"مراعاة التشكيل\" أو تقليل الفلاتر"
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        )
                    }
                }
            } else {
                val visible = viewModel.visibleResultCount()
                LazyColumnWithScrollbar {
                    items(visible) { i ->
                        TableRow(
                            index = i,
                            result = viewModel.results[i],
                            isSelected = i == viewModel.selectedResultIndex,
                            onClick = { viewModel.selectResult(i) },
                        )
                    }
                }
            }
        }
    }
}

// أَعمدة بِعَرض ثابِت: مُسلسل=60 / سَنة=72 / صَفحة=60
// المَساحة الباقية تُقسَم بَين السّياق والكتاب بِنِسبة 3.5:2.0
// (سَنة عَريضة 12dp إضافيّة على حِساب السياق)
private val SERIAL_COL_WIDTH = 60.dp
private val YEAR_COL_WIDTH   = 72.dp
private val PAGE_COL_WIDTH   = 60.dp
private const val CONTEXT_WEIGHT = 3.5f
private const val BOOK_WEIGHT    = 2.0f

@Composable
private fun TableHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primary)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TableCell(text = com.bahthia.i18n.tr("table.col.serial"),  modifier = Modifier.width(SERIAL_COL_WIDTH), isHeader = true)
        TableCell(text = com.bahthia.i18n.tr("table.col.context"), modifier = Modifier.weight(CONTEXT_WEIGHT),  isHeader = true)
        TableCell(text = com.bahthia.i18n.tr("table.col.book"),    modifier = Modifier.weight(BOOK_WEIGHT),     isHeader = true)
        TableCell(text = com.bahthia.i18n.tr("table.col.year"),    modifier = Modifier.width(YEAR_COL_WIDTH),   isHeader = true)
        TableCell(text = com.bahthia.i18n.tr("table.col.page"),    modifier = Modifier.width(PAGE_COL_WIDTH),   isHeader = true)
    }
}

@Composable
private fun TableRow(
    index: Int,
    result: SearchResult,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalBahthiaColors.current
    val bg = if (isSelected) Color(0xFFD4C4B0)
             else if (index % 2 == 0) Color.White
             else Color(0xFFFAFAFA)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TableCell(text = (index + 1).toString(), modifier = Modifier.width(SERIAL_COL_WIDTH))
        // عمود "السّياق" — ٧ كلمات قبل + اللَفظ الشاهد + ٧ كلمات بَعد، ظَلّ + Bold لِلمُطابَقة
        val windowedContext = remember(result.contextSnippet, result.matchedTerm) {
            windowAroundTerm(
                snippet = result.contextSnippet,
                term = result.matchedTerm,
                wordsBefore = 7,
                wordsAfter = 7,
            )
        }
        TableCellAnnotated(
            text = highlightAllOccurrences(
                text = windowedContext,
                term = result.matchedTerm,
                highlightColor = colors.highlight,
            ),
            modifier = Modifier.weight(CONTEXT_WEIGHT),
        )
        TableCell(text = result.bookTitle ?: "—", modifier = Modifier.weight(BOOK_WEIGHT))
        TableCell(text = result.bookYear ?: "—",  modifier = Modifier.width(YEAR_COL_WIDTH))
        TableCell(text = (result.originalPageNumber ?: result.pageNumber).toString(), modifier = Modifier.width(PAGE_COL_WIDTH))
    }
    HorizontalDivider(color = Color(0xFFF5F0EB), thickness = 1.dp)
}

@Composable
private fun TableCell(
    text: String,
    modifier: Modifier = Modifier,
    isHeader: Boolean = false,
) {
    Text(
        text = text,
        style = if (isHeader) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall,
        color = if (isHeader) Color.White else MaterialTheme.colorScheme.onSurface,
        fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
        textAlign = TextAlign.Center,
        // سَطر واحد فَقط — إن طال النَّصّ نَضع … بدل فَتح سَطر ثانٍ
        maxLines = 1,
        softWrap = false,
        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        modifier = modifier.padding(horizontal = 6.dp),
    )
}

/** نسخة من TableCell تَقبل AnnotatedString لإظهار التظليل + Bold للمطابقات. */
@Composable
private fun TableCellAnnotated(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        // سَطر واحد فَقط — إن تَجاوَز السّياق العَرض نَضع … عند الطَرف
        maxLines = 1,
        softWrap = false,
        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        modifier = modifier.padding(horizontal = 6.dp),
    )
}

private fun LazyListScope.items(count: Int, content: @Composable (Int) -> Unit) {
    items(count = count) { content(it) }
}

private typealias LazyListScope = androidx.compose.foundation.lazy.LazyListScope

@Composable
private fun BottomToolbar(
    viewModel: SearchViewModel,
    onShowBook: () -> Unit,
    onExport: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        ToolbarButton(
            modifier = Modifier.weight(1f),
            text = com.bahthia.i18n.tr("toolbar.prevPage"),
            tooltip = com.bahthia.i18n.tr("toolbar.prevPage.tooltip"),
            onClick = { viewModel.navigateBookPage(-1) },
            enabled = viewModel.canGoToPreviousPage(),
        )
        ToolbarButton(
            modifier = Modifier.weight(1f),
            text = com.bahthia.i18n.tr("toolbar.nextPage"),
            tooltip = com.bahthia.i18n.tr("toolbar.nextPage.tooltip"),
            onClick = { viewModel.navigateBookPage(+1) },
            enabled = viewModel.canGoToNextPage(),
        )
        ToolbarButton(
            modifier = Modifier.weight(1f),
            text = com.bahthia.i18n.tr("toolbar.nextGroup"),
            tooltip = com.bahthia.i18n.tr("toolbar.nextGroup.tooltip"),
            onClick = { viewModel.loadNextGroup() },
            enabled = viewModel.hasMoreToShow(),
        )
        ToolbarButton(
            modifier = Modifier.weight(1f),
            text = com.bahthia.i18n.tr("toolbar.fullBook"),
            tooltip = com.bahthia.i18n.tr("toolbar.fullBook.tooltip"),
            onClick = onShowBook,
            enabled = viewModel.results.isNotEmpty(),
        )
        ToolbarButton(
            modifier = Modifier.weight(1f),
            text = com.bahthia.i18n.tr("toolbar.nextResult"),
            tooltip = com.bahthia.i18n.tr("toolbar.nextResult.tooltip"),
            onClick = { viewModel.nextResult() },
            enabled = viewModel.results.isNotEmpty(),
        )
        ToolbarButton(
            modifier = Modifier.weight(1f),
            text = com.bahthia.i18n.tr("toolbar.prevResult"),
            tooltip = com.bahthia.i18n.tr("toolbar.prevResult.tooltip"),
            onClick = { viewModel.previousResult() },
            enabled = viewModel.results.isNotEmpty(),
        )
        ToolbarButton(
            modifier = Modifier.weight(1f),
            text = com.bahthia.i18n.tr("toolbar.export"),
            tooltip = com.bahthia.i18n.tr("toolbar.export.tooltip"),
            onClick = onExport,
            enabled = viewModel.results.isNotEmpty(),
        )
    }
}

/**
 * زرّ شريط الأدوات — Box(weight) خارج BahthiaTooltip لئلّا يَكسر TooltipBox
 * توزيع weight داخل Row. الزرّ نفسه يَملأ العرض المخصَّص له.
 */
@Composable
private fun ToolbarButton(
    modifier: Modifier,
    text: String,
    tooltip: String,
    onClick: () -> Unit,
    enabled: Boolean,
) {
    Box(modifier = modifier) {
        com.bahthia.app.ui.components.BahthiaTooltip(text = tooltip) {
            BrownButton(
                text = text,
                onClick = onClick,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun highlightSnippet(
    text: String,
    term: String,
    highlightColor: Color,
): AnnotatedString {
    if (term.isBlank()) return AnnotatedString(text)
    return buildAnnotatedString {
        var idx = 0
        while (idx < text.length) {
            val pos = text.indexOf(term, idx)
            if (pos < 0) {
                append(text.substring(idx))
                break
            }
            append(text.substring(idx, pos))
            val start = length
            append(term)
            addStyle(SpanStyle(background = highlightColor, fontWeight = FontWeight.Bold), start, length)
            idx = pos + term.length
        }
    }
}

