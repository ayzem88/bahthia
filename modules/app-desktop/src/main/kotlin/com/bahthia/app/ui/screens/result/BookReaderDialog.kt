package com.bahthia.app.ui.screens.result

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bahthia.app.state.AppRuntime
import com.bahthia.app.state.SearchViewModel
import com.bahthia.app.ui.components.BahthiaTooltip
import com.bahthia.app.ui.theme.LocalBahthiaColors
import com.bahthia.domain.Page
import com.bahthia.domain.SearchResult
import java.awt.Frame
import java.awt.Rectangle
import java.awt.Robot
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import javax.swing.JFileChooser

/**
 * عرض كتاب كاملاً مع شريط علويّ غنيّ بأدوات القراءة:
 *   - بحث محلّيّ + تنقّل بين المطابقات
 *   - تنقّل بين الصفحات + قفز
 *   - أدوات: صورة، قصّ، اقتباس، إشارة، تصدير
 *
 * تَخطيط الشريط (٤ مجموعات مفصولة بفاصل):
 *   [بحث محلّي + تنقّل]  │  [صفحة سابقة/تالية + قفز]  │  [أدوات: 📷 ✂ « » 🔖 ⬇]
 */
@Composable
fun BookReaderDialog(
    viewModel: SearchViewModel,
    bookId: Long,
    bookTitle: String?,
    initialPageNumber: Int,
    highlightTerm: String?,
    onClose: () -> Unit,
) {
    var pages by remember(bookId) { mutableStateOf<List<Page>>(emptyList()) }
    var loading by remember(bookId) { mutableStateOf(true) }
    var jumpInput by remember { mutableStateOf("") }
    // مَلخّص الكِتاب (مُؤلِّف، تَصنيف، سَنة) — يُستعمَل في الاقتباس الأَكاديميّ
    var bookSummary by remember(bookId) { mutableStateOf<com.bahthia.search.BahthiaSearcher.BookSummary?>(null) }
    // حالة عَرض حِوار الاقتباس
    var showCitation by remember { mutableStateOf<com.bahthia.domain.SearchResult?>(null) }

    // البحث المحلّي — يَبحث عَبر **كلّ** صَفحات الكتاب
    var localQuery by remember { mutableStateOf("") }
    var matchCursor by remember { mutableStateOf(0) }

    // حَجم الخَطّ (pt). الافتراضيّ ١٦. التَّنقّل بَين 12, 14, 16, 18, 20 بِأَزرار + و −
    var fontSizePt by remember { mutableStateOf(DEFAULT_FONT_SIZE) }

    val colors = LocalBahthiaColors.current
    val coroutineScope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()

    LaunchedEffect(bookId) {
        loading = true
        pages = viewModel.fetchBookPages(bookId)
        bookSummary = viewModel.fetchBookSummary(bookId)
        loading = false
    }

    // الصَّفحة الأَولى المَرئيّة في الـLazyColumn — تُحدِّث عَدّاد "صفحة X / Y" أَعلى الحِوار
    val currentVisibleIndex by remember {
        derivedStateOf { lazyListState.firstVisibleItemIndex }
    }
    val current = pages.getOrNull(currentVisibleIndex)

    // التَّمرير الابتدائيّ إلى الصَّفحة المَطلوبة (initialPageNumber)
    LaunchedEffect(pages.isNotEmpty()) {
        if (pages.isEmpty()) return@LaunchedEffect
        val target = pages.indexOfFirst { it.pageNumber == initialPageNumber }
        if (target >= 0) lazyListState.scrollToItem(target)
    }

    // كلّ المُطابَقات في الكِتاب كَامِلاً (pageIndex, charPos)
    val allMatches = remember(localQuery, pages) {
        if (localQuery.isBlank() || pages.isEmpty()) emptyList()
        else pages.flatMapIndexed { pageIdx, page ->
            findAllMatches(page.content, localQuery).map { GlobalMatch(pageIdx, it) }
        }
    }

    // إعادة ضَبط المُؤشّر عند تَغيير الاستِعلام
    LaunchedEffect(localQuery) { matchCursor = 0 }

    // التَّمرير إلى الصَّفحة الّتي تَحوي المُطابَقة النَّشطة
    LaunchedEffect(matchCursor, allMatches) {
        if (allMatches.isEmpty()) return@LaunchedEffect
        val match = allMatches.getOrNull(matchCursor) ?: return@LaunchedEffect
        lazyListState.animateScrollToItem(match.pageIndex)
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(0.92f).fillMaxHeight(0.92f).padding(16.dp),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // ─── الشريط العلويّ الرئيسيّ ───
                ReaderTopBar(
                    bookTitle = bookTitle ?: "—",
                    pageLabel = if (current != null)
                        "${current.originalPageNumber ?: current.pageNumber} / ${pages.size}"
                    else "—",
                    onClose = onClose,
                )

                // ─── شريط الأدوات ───
                ReaderToolbar(
                    localQuery = localQuery,
                    onLocalQueryChange = { localQuery = it },
                    matchCount = allMatches.size,
                    matchCursor = matchCursor,
                    onPrevMatch = {
                        if (allMatches.isNotEmpty()) {
                            matchCursor = (matchCursor - 1 + allMatches.size) % allMatches.size
                        }
                    },
                    onNextMatch = {
                        if (allMatches.isNotEmpty()) {
                            matchCursor = (matchCursor + 1) % allMatches.size
                        }
                    },
                    canPrevPage = currentVisibleIndex > 0 && !loading,
                    canNextPage = currentVisibleIndex < pages.size - 1 && !loading,
                    onPrevPage = {
                        coroutineScope.launch {
                            val target = (currentVisibleIndex - 1).coerceAtLeast(0)
                            lazyListState.animateScrollToItem(target)
                        }
                    },
                    onNextPage = {
                        coroutineScope.launch {
                            val target = (currentVisibleIndex + 1).coerceAtMost(pages.size - 1)
                            lazyListState.animateScrollToItem(target)
                        }
                    },
                    jumpInput = jumpInput,
                    onJumpInputChange = { jumpInput = it.filter { c -> c.isDigit() }.take(7) },
                    onJump = {
                        val target = jumpInput.toIntOrNull() ?: return@ReaderToolbar
                        val found = pages.indexOfFirst {
                            it.originalPageNumber?.toIntOrNull() == target || it.pageNumber == target
                        }
                        if (found >= 0) {
                            coroutineScope.launch { lazyListState.animateScrollToItem(found) }
                        }
                    },
                    onScreenshot = { saveWindowScreenshot(bookTitle, current?.pageNumber ?: 0) },
                    onScreenshotSelection = {
                        captureSelectionAsImage(
                            bookTitle = bookTitle,
                            pageLabel = current?.originalPageNumber ?: current?.pageNumber?.toString() ?: "",
                        )
                    },
                    onCite = {
                        // نَبني SearchResult مُؤقَّتاً يَعكس الصَّفحة الحاليّة + ميتاداتا الكِتاب
                        val cur = current
                        if (cur != null) {
                            showCitation = com.bahthia.domain.SearchResult(
                                bookId = bookId,
                                pageNumber = cur.pageNumber,
                                originalPageNumber = cur.originalPageNumber,
                                matchedTerm = "",
                                matchPosition = -1,
                                contextSnippet = "",
                                bookTitle = bookTitle ?: bookSummary?.title,
                                bookAuthor = bookSummary?.author,
                                bookCategory = bookSummary?.category,
                                bookYear = bookSummary?.year,
                            )
                        }
                    },
                    onSaveBookmark = {
                        saveBookmark(bookId, current?.pageNumber ?: 0)
                    },
                    onExport = { exportBookToFile(bookTitle, pages, BookExportFmt.TXT) },
                    fontSizePt = fontSizePt,
                    onIncreaseFont = { fontSizePt = nextFontSize(fontSizePt) },
                    onDecreaseFont = { fontSizePt = prevFontSize(fontSizePt) },
                )

                HorizontalDivider(color = colors.divider)

                // ─── مُحتوى الكتاب كاملاً — LazyColumn يَنزل عَبر كلّ الصَّفحات ───
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when {
                        loading -> CenterText("جارٍ تحميل الكتاب…")
                        pages.isEmpty() -> CenterText("لا توجد صفحات")
                        else -> {
                            val activeColor = MaterialTheme.colorScheme.primary
                            LazyColumn(
                                state = lazyListState,
                                modifier = Modifier.fillMaxSize().padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                            ) {
                                items(pages.size, key = { i -> pages[i].pageNumber }) { i ->
                                    val page = pages[i]
                                    // مُطابَقات هذه الصَّفحة فَقط
                                    val matchesInPage = allMatches.withIndex()
                                        .filter { it.value.pageIndex == i }
                                    val activeWithinPage = matchesInPage
                                        .indexOfFirst { it.index == matchCursor }
                                        .let { if (it >= 0) it else -1 }
                                    PageBlock(
                                        page = page,
                                        matchPositions = matchesInPage.map { it.value.charPos },
                                        activeMatchInPage = activeWithinPage,
                                        highlightTerm = highlightTerm,
                                        localQuery = localQuery,
                                        highlightColor = colors.highlight,
                                        activeMatchColor = activeColor,
                                        fontSizePt = fontSizePt,
                                    )
                                }
                            }
                            androidx.compose.foundation.VerticalScrollbar(
                                adapter = androidx.compose.foundation.rememberScrollbarAdapter(lazyListState),
                                style = com.bahthia.app.ui.components.BahthiaScrollbarStyle,
                                modifier = Modifier.align(Alignment.CenterStart).fillMaxHeight(),
                            )
                        }
                    }
                }
            }
        }
    }

    // ─── حِوار الاقتباس الأَكاديميّ — يَفتح عند نَقر «» في شَريط الأَدوات ───
    showCitation?.let { result ->
        CitationDialog(result = result, onClose = { showCitation = null })
    }
}

// ─── شريط العنوان ─────────────────────────────────────────────

@Composable
private fun ReaderTopBar(bookTitle: String, pageLabel: String, onClose: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BahthiaTooltip(text = "إغلاق القارئ (Esc)") {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "إغلاق",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp).clickable(onClick = onClose).padding(2.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = bookTitle,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            Text(
                text = "صفحة $pageLabel",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.85f),
            )
        }
    }
}

// ─── شريط الأدوات (٤ مجموعات) ────────────────────────────────

@Composable
private fun ReaderToolbar(
    localQuery: String,
    onLocalQueryChange: (String) -> Unit,
    matchCount: Int,
    matchCursor: Int,
    onPrevMatch: () -> Unit,
    onNextMatch: () -> Unit,
    canPrevPage: Boolean,
    canNextPage: Boolean,
    onPrevPage: () -> Unit,
    onNextPage: () -> Unit,
    jumpInput: String,
    onJumpInputChange: (String) -> Unit,
    onJump: () -> Unit,
    onScreenshot: () -> Unit,
    onScreenshotSelection: () -> Unit,
    onCite: () -> Unit,
    onSaveBookmark: () -> Unit,
    onExport: () -> Unit,
    fontSizePt: Int,
    onIncreaseFont: () -> Unit,
    onDecreaseFont: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // المجموعة ١ — صَندوق البَحث المُوحَّد (نَفس تَصميم البَحث الرَّئيسيّ)
            com.bahthia.app.ui.components.BahthiaSearchField(
                value = localQuery,
                onValueChange = onLocalQueryChange,
                onSearchAction = { onNextMatch() },
                modifier = Modifier.width(280.dp),
            )
            if (localQuery.isNotBlank()) {
                Text(
                    text = if (matchCount > 0) "${matchCursor + 1} / $matchCount" else "٠",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            BahthiaTooltip(text = "المطابقة السابقة") {
                ToolbarIcon(
                    icon = Icons.Filled.KeyboardArrowUp,
                    onClick = onPrevMatch,
                    enabled = matchCount > 0,
                )
            }
            BahthiaTooltip(text = "المطابقة التالية") {
                ToolbarIcon(
                    icon = Icons.Filled.KeyboardArrowDown,
                    onClick = onNextMatch,
                    enabled = matchCount > 0,
                )
            }

            ToolbarDivider()

            // المجموعة ٢ — تنقّل الصفحات + قفز
            BahthiaTooltip(text = "الصفحة السابقة") {
                ToolbarIcon(
                    icon = Icons.Filled.KeyboardArrowUp,
                    onClick = onPrevPage,
                    enabled = canPrevPage,
                    accent = true,
                )
            }
            BahthiaTooltip(text = "الصفحة التالية") {
                ToolbarIcon(
                    icon = Icons.Filled.KeyboardArrowDown,
                    onClick = onNextPage,
                    enabled = canNextPage,
                    accent = true,
                )
            }
            // صُندوق القَفز إلى رَقم صَفحة — بِنَفس تَصميم BahthiaSearchField
            com.bahthia.app.ui.components.BahthiaSearchField(
                value = jumpInput,
                onValueChange = onJumpInputChange,
                onSearchAction = { onJump() },
                leadingIcon = Icons.Outlined.FormatListNumbered,
                placeholder = "رقم الصَّفحة",
                imeAction = ImeAction.Go,
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                modifier = Modifier.width(160.dp),
            )

            ToolbarDivider()

            // المجموعة ٣ — حَجم الخَطّ (+ / −)
            BahthiaTooltip(text = "تَكبير حَجم النَّصّ") {
                ToolbarIcon(
                    icon = Icons.Filled.Add,
                    onClick = onIncreaseFont,
                    enabled = fontSizePt < FONT_SIZE_LADDER.last(),
                )
            }
            BahthiaTooltip(text = "تَصغير حَجم النَّصّ") {
                ToolbarIcon(
                    icon = Icons.Filled.Remove,
                    onClick = onDecreaseFont,
                    enabled = fontSizePt > FONT_SIZE_LADDER.first(),
                )
            }

            ToolbarDivider()

            // المجموعة ٤ — أدوات
            BahthiaTooltip(text = "صورة لواجهة الكتاب (يَسأل عن المسار)") {
                ToolbarIcon(icon = Icons.Filled.PhotoCamera, onClick = onScreenshot)
            }
            BahthiaTooltip(text = "صورة لاقتباس مُحدَّد (يَنسخ من الحافظة)") {
                ToolbarIcon(icon = Icons.Filled.ContentCut, onClick = onScreenshotSelection)
            }
            BahthiaTooltip(text = "للاقتباس الأكاديميّ") {
                ToolbarIcon(icon = Icons.Filled.FormatQuote, onClick = onCite)
            }
            BahthiaTooltip(text = "حفظ آخر صفحة قراءة") {
                ToolbarIcon(icon = Icons.Filled.Bookmark, onClick = onSaveBookmark)
            }
            BahthiaTooltip(text = "تصدير الكتاب إلى ملفّ نصّي") {
                ToolbarIcon(icon = Icons.Filled.FileDownload, onClick = onExport)
            }

            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun ToolbarIcon(
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true,
    accent: Boolean = false,
) {
    val tint = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
        accent -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
    }
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (accent && enabled) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                else Color.Transparent,
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.padding(8.dp).size(18.dp),
        )
    }
}

@Composable
private fun ToolbarDivider() {
    Box(
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .width(1.dp)
            .height(24.dp)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
    )
}

// ─── كُتلة صَفحة واحِدة في الـLazyColumn ────────────────────────

/** عُنوان مُطابَقة في الكِتاب كلّه — صَفحة + مَوضع الحَرف داخلها. */
private data class GlobalMatch(val pageIndex: Int, val charPos: Int)

@Composable
private fun PageBlock(
    page: Page,
    matchPositions: List<Int>,
    activeMatchInPage: Int,
    highlightTerm: String?,
    localQuery: String,
    highlightColor: Color,
    activeMatchColor: Color,
    fontSizePt: Int,
) {
    val annotated = remember(
        page.content, highlightTerm, localQuery, matchPositions, activeMatchInPage,
    ) {
        buildPageAnnotated(
            text = page.content,
            highlightTerm = highlightTerm,
            localQuery = localQuery,
            matchPositions = matchPositions,
            activeMatch = activeMatchInPage,
            highlightColor = highlightColor,
            activeMatchColor = activeMatchColor,
        )
    }
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        // فاصِل الصَّفحة — رَقم الصَّفحة وَسط شَريط رَفيع
        PageSeparator(label = "صفحة ${page.originalPageNumber ?: page.pageNumber}")
        Spacer(Modifier.height(8.dp))
        SelectionContainer {
            Text(
                text = annotated,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = fontSizePt.sp),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun PageSeparator(label: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
        )
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
            modifier = Modifier.padding(horizontal = 12.dp),
        ) {
            Text(
                text = label,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
        }
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
        )
    }
}

// ─── ثَوابِت ──────────────────────────────────────────────────

/** سُلَّم أَحجام الخَطّ المُتاحة في القارئ — التَّنقّل بِأَزرار + و −. */
private val FONT_SIZE_LADDER = listOf(12, 14, 16, 18, 20)
/** الحَجم الافتراضيّ — مَوقع المُؤشِّر يَبدأ مِنه. */
private const val DEFAULT_FONT_SIZE = 16

/** يُعيد الحَجم التّالي في السُّلَّم (أو نَفسه إن كَان آخِرَه). */
private fun nextFontSize(current: Int): Int {
    val idx = FONT_SIZE_LADDER.indexOf(current).coerceAtLeast(0)
    return FONT_SIZE_LADDER.getOrElse(idx + 1) { FONT_SIZE_LADDER.last() }
}

/** يُعيد الحَجم السَّابِق في السُّلَّم (أو نَفسه إن كَان أَوّلَه). */
private fun prevFontSize(current: Int): Int {
    val idx = FONT_SIZE_LADDER.indexOf(current).coerceAtLeast(0)
    return FONT_SIZE_LADDER.getOrElse(idx - 1) { FONT_SIZE_LADDER.first() }
}

// ─── أدوات داخليّة ─────────────────────────────────────────────

private fun findAllMatches(text: String, query: String): List<Int> {
    if (query.isBlank()) return emptyList()
    val out = mutableListOf<Int>()
    var idx = 0
    while (idx < text.length) {
        val pos = text.indexOf(query, idx, ignoreCase = true)
        if (pos < 0) break
        out.add(pos)
        idx = pos + query.length
    }
    return out
}

private fun buildPageAnnotated(
    text: String,
    highlightTerm: String?,
    localQuery: String,
    matchPositions: List<Int>,
    activeMatch: Int,
    highlightColor: Color,
    activeMatchColor: Color,
): AnnotatedString = buildAnnotatedString {
    append(text)

    // ١. تظليل مصطلح البحث الأصليّ (من الجدول)
    if (!highlightTerm.isNullOrBlank()) {
        var idx = 0
        while (idx < text.length) {
            val pos = text.indexOf(highlightTerm, idx)
            if (pos < 0) break
            addStyle(
                SpanStyle(background = highlightColor, fontWeight = FontWeight.Bold),
                pos, pos + highlightTerm.length,
            )
            idx = pos + highlightTerm.length
        }
    }

    // ٢. تظليل البحث المحلّي
    if (localQuery.isNotBlank() && matchPositions.isNotEmpty()) {
        for ((i, pos) in matchPositions.withIndex()) {
            val end = (pos + localQuery.length).coerceAtMost(text.length)
            // كلّ المطابقات: تظليل مصفّر-فاتح
            addStyle(
                SpanStyle(
                    background = if (i == activeMatch) activeMatchColor.copy(alpha = 0.45f)
                                 else activeMatchColor.copy(alpha = 0.18f),
                    fontWeight = FontWeight.Bold,
                ),
                pos, end,
            )
        }
    }
}

// ─── الصور والتصدير ──────────────────────────────────────────

private fun saveWindowScreenshot(bookTitle: String?, pageNumber: Int) {
    val frame = Frame.getFrames().firstOrNull { it.isShowing } ?: return
    val title = (bookTitle ?: "book").replace(Regex("""[^\p{L}\p{N}_-]+"""), "_")
    val target = chooseSavePath("$title-page$pageNumber.png", "PNG") ?: return
    try {
        val bounds: Rectangle = frame.bounds
        val image = Robot().createScreenCapture(bounds)
        ImageIO.write(image, "png", target)
    } catch (e: Exception) {
        // fail silently for now
    }
}

/**
 * يَلتقط النصّ المُحدَّد من الحافظة ويُولّد بطاقة-اقتباس بصرية.
 * المستخدم يَنسخ النصّ أوّلاً (Ctrl+C داخل الـ SelectionContainer)، ثمّ ينقر هذا الزرّ.
 */
private fun captureSelectionAsImage(bookTitle: String?, pageLabel: String) {
    val clipboard = Toolkit.getDefaultToolkit().systemClipboard
    val selectedText = try {
        clipboard.getData(DataFlavor.stringFlavor) as? String
    } catch (_: Exception) { null } ?: return

    if (selectedText.isBlank() || selectedText.length < 3) return

    val title = (bookTitle ?: "book").replace(Regex("""[^\p{L}\p{N}_-]+"""), "_")
    val target = chooseSavePath("$title-quote.png", "PNG") ?: return

    try {
        val image = renderQuoteCard(
            quote = selectedText.take(800),
            book = bookTitle ?: "—",
            page = pageLabel,
        )
        ImageIO.write(image, "png", target)
    } catch (_: Exception) { /* ignore */ }
}

/** يَرسم بطاقة اقتباس على Bitmap باستعمال Java AWT. */
private fun renderQuoteCard(quote: String, book: String, page: String): java.awt.image.BufferedImage {
    val width = 1000
    val padding = 60
    val img = java.awt.image.BufferedImage(width, 600, java.awt.image.BufferedImage.TYPE_INT_RGB)
    val g = img.createGraphics()
    g.setRenderingHint(
        java.awt.RenderingHints.KEY_TEXT_ANTIALIASING,
        java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB,
    )
    // خلفيّة ترابيّة فاتحة
    g.color = java.awt.Color(0xF5, 0xEE, 0xE3)
    g.fillRect(0, 0, width, 600)
    // إطار
    g.color = java.awt.Color(0xA0, 0x82, 0x6D)
    g.drawRect(20, 20, width - 40, 560)

    // عنوان «اقتباس»
    g.font = java.awt.Font("Sakkal Majalla", java.awt.Font.BOLD, 36)
    g.color = java.awt.Color(0xA0, 0x82, 0x6D)
    g.drawString("« »", padding, padding + 20)

    // النصّ
    g.font = java.awt.Font("Sakkal Majalla", java.awt.Font.PLAIN, 22)
    g.color = java.awt.Color(0x33, 0x33, 0x33)
    val maxWidth = width - 2 * padding
    val lines = wrapText(quote, g.fontMetrics, maxWidth)
    var y = padding + 80
    for (line in lines.take(15)) {
        g.drawString(line, padding, y)
        y += g.fontMetrics.height + 4
    }

    // الميتاداتا في الأسفل
    g.font = java.awt.Font("Sakkal Majalla", java.awt.Font.ITALIC, 18)
    g.color = java.awt.Color(0x8B, 0x73, 0x55)
    g.drawString("$book — صفحة $page", padding, 540)

    g.dispose()
    return img
}

private fun wrapText(text: String, fm: java.awt.FontMetrics, maxWidth: Int): List<String> {
    val words = text.split(Regex("""\s+"""))
    val out = mutableListOf<String>()
    var line = StringBuilder()
    for (w in words) {
        val candidate = if (line.isEmpty()) w else "$line $w"
        if (fm.stringWidth(candidate) > maxWidth && line.isNotEmpty()) {
            out.add(line.toString())
            line = StringBuilder(w)
        } else {
            line = StringBuilder(candidate)
        }
    }
    if (line.isNotEmpty()) out.add(line.toString())
    return out
}

private enum class BookExportFmt { TXT, DOCX }

private fun exportBookToFile(bookTitle: String?, pages: List<Page>, fmt: BookExportFmt) {
    if (pages.isEmpty()) return
    val title = (bookTitle ?: "book").replace(Regex("""[^\p{L}\p{N}_-]+"""), "_")
    val ext = if (fmt == BookExportFmt.TXT) "txt" else "docx"
    val target = chooseSavePath("$title.$ext", ext.uppercase()) ?: return
    try {
        if (fmt == BookExportFmt.TXT) {
            val sb = StringBuilder()
            sb.appendLine("=== ${bookTitle ?: ""} ===\n")
            for (p in pages) {
                sb.appendLine("---- صفحة ${p.originalPageNumber ?: p.pageNumber} ----")
                sb.appendLine(p.content)
                sb.appendLine()
            }
            Files.writeString(target.toPath(), sb.toString())
        } else {
            // DOCX (نَستعمل Apache POI الموجود سَلفاً)
            org.apache.poi.xwpf.usermodel.XWPFDocument().use { doc ->
                doc.createParagraph().createRun().apply {
                    setText(bookTitle ?: "")
                    isBold = true
                    fontSize = 18
                }
                for (p in pages) {
                    doc.createParagraph().createRun().apply {
                        setText("صفحة ${p.originalPageNumber ?: p.pageNumber}")
                        isItalic = true
                    }
                    doc.createParagraph().createRun().apply {
                        setText(p.content)
                    }
                }
                target.outputStream().use { doc.write(it) }
            }
        }
    } catch (_: Exception) { /* ignore */ }
}

/** يَحفظ رقم آخر صفحة قراءة في `bookmarks.properties` بجوار التفضيلات. */
private fun saveBookmark(bookId: Long, pageNumber: Int) {
    try {
        val bookmarksFile = AppRuntime.dataDir.resolve("bookmarks.properties").toFile()
        val props = java.util.Properties()
        if (bookmarksFile.exists()) bookmarksFile.inputStream().use { props.load(it) }
        props.setProperty("book.$bookId", pageNumber.toString())
        props.setProperty("book.$bookId.timestamp", System.currentTimeMillis().toString())
        bookmarksFile.outputStream().use { props.store(it, "Bahthia bookmarks") }
    } catch (_: Exception) { /* ignore */ }
}

private fun chooseSavePath(suggestedName: String, kind: String): File? {
    val chooser = JFileChooser().apply {
        dialogTitle = "حفظ كـ $kind"
        selectedFile = File(suggestedName)
    }
    val result = chooser.showSaveDialog(null)
    return if (result == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
}
