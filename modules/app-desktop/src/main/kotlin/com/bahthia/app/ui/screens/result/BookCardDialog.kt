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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bahthia.app.state.SearchViewModel
import com.bahthia.app.ui.components.BrownButton
import com.bahthia.search.BahthiaSearcher

/**
 * بطاقة الكتاب — ميتاداتا مختصرة.
 * تَعرض ما هو متاح في فهرس Lucene فقط (عنوان، مؤلّف، فنّ، سنة، عدد الصفحات).
 */
@Composable
fun BookCardDialog(
    viewModel: SearchViewModel,
    bookId: Long,
    onClose: () -> Unit,
) {
    var summary by remember(bookId) { mutableStateOf<BahthiaSearcher.BookSummary?>(null) }
    var loading by remember(bookId) { mutableStateOf(true) }

    LaunchedEffect(bookId) {
        loading = true
        summary = viewModel.fetchBookSummary(bookId)
        loading = false
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.widthIn(min = 420.dp, max = 600.dp).padding(16.dp),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "بطاقة الكتاب",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))

                when {
                    loading -> Text("جارٍ التحميل…", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    summary == null -> Text("تعذّر العثور على الكتاب", color = MaterialTheme.colorScheme.error)
                    else -> {
                        val s = summary!!
                        Field(label = "العنوان",      value = s.title)
                        Field(label = "المؤلّف",       value = s.author)
                        Field(label = "الفنّ",          value = s.category)
                        Field(label = "السنة",         value = s.year)
                        Field(label = "عدد الصفحات",   value = s.pagesCount.toString())
                        Field(label = "معرّف داخلي",   value = s.bookId.toString())
                    }
                }

                Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    BrownButton(text = "إغلاق", onClick = onClose)
                }
            }
        }
    }
}

@Composable
private fun Field(label: String, value: String?) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = "$label:",
            fontWeight = FontWeight.Bold,
            modifier = Modifier.widthIn(min = 110.dp),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = value?.takeIf { it.isNotBlank() } ?: "—",
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
