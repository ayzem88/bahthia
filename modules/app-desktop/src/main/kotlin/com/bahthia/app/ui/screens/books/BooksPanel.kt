package com.bahthia.app.ui.screens.books

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.bahthia.app.ui.components.BahthiaTooltip
import com.bahthia.app.ui.components.LazyColumnWithScrollbar
import com.bahthia.app.ui.components.PanelHeader
import com.bahthia.app.ui.components.SelectAllClearAllRow

/**
 * لوحة "الكتب" — الترويسة + بحث في العناوين + قائمة + تحديد/إلغاء.
 * مطابقة لـ Python `BooksPanel.create`.
 */
@Composable
fun BooksPanel(
    books: List<BookEntry>,
    onToggleBook: (Long) -> Unit,
    onSelectAll: () -> Unit,
    onClearAll: () -> Unit,
    onOpenBook: (BookEntry) -> Unit = {},
) {
    var filter by remember { mutableStateOf("") }
    val filtered = remember(books, filter) {
        if (filter.isBlank()) books
        else books.filter { it.title.contains(filter.trim(), ignoreCase = false) }
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp)) {
        PanelHeader(title = com.bahthia.i18n.tr("panel.books.title"))

        Spacer(Modifier.height(5.dp))

        com.bahthia.app.ui.components.BahthiaSearchField(
            value = filter,
            onValueChange = { filter = it },
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(5.dp))

        Surface(
            shape = RoundedCornerShape(5.dp),
            color = Color.White,
            border = BorderStroke(2.dp, Color(0xFFDDDDDD)),
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            LazyColumnWithScrollbar {
                items(filtered.size) { i ->
                    BookRow(
                        filtered[i],
                        onToggle = { onToggleBook(filtered[i].id) },
                        onOpen = { onOpenBook(filtered[i]) },
                    )
                }
            }
        }

        Spacer(Modifier.height(5.dp))

        SelectAllClearAllRow(
            onSelectAll = onSelectAll,
            onClearAll = onClearAll,
            selectAllTooltip = com.bahthia.i18n.tr("panel.selectAll.books.tooltip"),
            clearAllTooltip  = com.bahthia.i18n.tr("panel.clearAll.books.tooltip"),
        )
    }
}

private fun LazyListScope.items(count: Int, content: @Composable (Int) -> Unit) {
    items(count = count) { content(it) }
}

private typealias LazyListScope = androidx.compose.foundation.lazy.LazyListScope

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookRow(entry: BookEntry, onToggle: () -> Unit, onOpen: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onToggle,
                onDoubleClick = onOpen,
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = entry.selected, onCheckedChange = { onToggle() })

        // قَصّ العنوان إلى ٥ كلمات مع "..." إن طال
        val titleWords = entry.title.split(Regex("""\s+""")).filter { it.isNotEmpty() }
        val (truncatedTitle, isTruncated) = if (titleWords.size > 5)
            titleWords.take(5).joinToString(" ") + " ..." to true
        else
            entry.title to false

        // النصّ مع تَمييز لون المؤلّف بالـ AnnotatedString
        val authorPart = entry.author?.takeIf { it.isNotBlank() }
        val authorColor = MaterialTheme.colorScheme.primary
        val display = remember(truncatedTitle, authorPart) {
            androidx.compose.ui.text.buildAnnotatedString {
                append(truncatedTitle)
                if (authorPart != null) {
                    append(" | ")
                    val start = length
                    append(authorPart)
                    addStyle(
                        androidx.compose.ui.text.SpanStyle(color = authorColor),
                        start,
                        length,
                    )
                }
            }
        }

        // tooltip بالعنوان كاملاً عند المرور (يُظهَر فقط لو قُصّ)
        val fullTooltip = if (isTruncated) {
            entry.title + (authorPart?.let { " | $it" } ?: "")
        } else ""

        BahthiaTooltip(text = fullTooltip, modifier = Modifier.weight(1f)) {
            Text(
                text = display,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    HorizontalDivider(color = Color(0xFFF5F0EB), thickness = 1.dp)
}

data class BookEntry(
    val id: Long,
    val title: String,
    val author: String? = null,
    val category: String? = null,
    val year: String? = null,
    val deathYear: String? = null,
    val country: String? = null,
    val selected: Boolean = false,
)
