package com.bahthia.app.ui.screens.result

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bahthia.app.state.SearchViewModel
import com.bahthia.app.ui.components.BrownButton
import com.bahthia.app.ui.components.ScrollableColumn
import com.bahthia.app.ui.theme.LocalBahthiaColors
import com.bahthia.domain.Page

/**
 * عرض الصفحة كاملةً مع تظليل المطابقة (إن وُجدت).
 * يُفتح من زرّ "عرض الصفحة كاملة" على نتيجة محدّدة.
 */
@Composable
fun PageReaderDialog(
    viewModel: SearchViewModel,
    bookId: Long,
    pageNumber: Int,
    bookTitle: String?,
    highlightTerm: String?,
    onClose: () -> Unit,
) {
    var page by remember(bookId, pageNumber) { mutableStateOf<Page?>(null) }
    var loading by remember(bookId, pageNumber) { mutableStateOf(true) }
    val colors = LocalBahthiaColors.current

    LaunchedEffect(bookId, pageNumber) {
        loading = true
        page = viewModel.fetchPage(bookId, pageNumber)
        loading = false
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(0.85f).fillMaxHeight(0.85f).padding(16.dp),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                ReaderHeader(
                    title = bookTitle ?: "—",
                    subtitle = "الصفحة ${page?.originalPageNumber ?: pageNumber}",
                    onClose = onClose,
                )

                Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(16.dp)) {
                    when {
                        loading -> CenterText("جارٍ التحميل…")
                        page == null -> CenterText("تعذّر العثور على الصفحة")
                        else -> {
                            ScrollableColumn(modifier = Modifier.fillMaxSize()) {
                                Text(
                                    text = highlightAll(page!!.content, highlightTerm, colors.highlight),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    BrownButton(text = "إغلاق", onClick = onClose)
                }
            }
        }
    }
}

@Composable
internal fun ReaderHeader(title: String, subtitle: String, onClose: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = subtitle,
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            com.bahthia.app.ui.components.BahthiaTooltip(text = "إغلاق القارئ (Esc)") {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "إغلاق",
                    tint = Color.White,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .clickable(onClick = onClose)
                        .padding(8.dp)
                        .size(20.dp),
                )
            }
        }
    }
}

@Composable
internal fun CenterText(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
    }
}

internal fun highlightAll(text: String, term: String?, color: Color): AnnotatedString {
    if (term.isNullOrBlank()) return AnnotatedString(text)
    return buildAnnotatedString {
        var idx = 0
        while (idx < text.length) {
            val pos = text.indexOf(term, idx)
            if (pos < 0) {
                append(text.substring(idx))
                break
            }
            append(text.substring(idx, pos))
            val s = length
            append(term)
            addStyle(SpanStyle(background = color, fontWeight = FontWeight.Bold), s, length)
            idx = pos + term.length
        }
    }
}

