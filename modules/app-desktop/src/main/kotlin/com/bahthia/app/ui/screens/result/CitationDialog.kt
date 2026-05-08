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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.bahthia.app.ui.components.BrownButton
import com.bahthia.domain.SearchResult
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.time.Year

/**
 * يَبني نصّ اقتباس من نتيجة بحث في ٤ أنماط: APA، MLA، Chicago، عربيّ.
 */
@Composable
fun CitationDialog(
    result: SearchResult,
    onClose: () -> Unit,
) {
    var style by remember { mutableStateOf(CitationStyle.ARABIC) }
    val text = remember(style) { buildCitation(result, style) }
    var copied by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.widthIn(min = 500.dp, max = 720.dp).padding(16.dp),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    "اقتباس أكاديميّ",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))

                Text("النمط:", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                CitationStyle.entries.forEach { s ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = style == s, onClick = { style = s; copied = false })
                        Text(text = s.label, modifier = Modifier.padding(start = 4.dp))
                    }
                }

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = text,
                    onValueChange = { /* readOnly */ },
                    label = { Text("النصّ") },
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                )

                if (copied) {
                    Spacer(Modifier.height(4.dp))
                    Text("✅ نُسخ إلى الحافظة", color = MaterialTheme.colorScheme.primary)
                }

                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    BrownButton(text = "إغلاق", onClick = onClose)
                    Spacer(Modifier.height(0.dp))
                    BrownButton(
                        text = "نسخ",
                        onClick = {
                            Toolkit.getDefaultToolkit().systemClipboard
                                .setContents(StringSelection(text), null)
                            copied = true
                        },
                    )
                }
            }
        }
    }
}

enum class CitationStyle(val label: String) {
    ARABIC("عربيّ"),
    APA("APA"),
    MLA("MLA"),
    CHICAGO("Chicago"),
}

private fun buildCitation(r: SearchResult, style: CitationStyle): String {
    val author = r.bookAuthor?.takeIf { it.isNotBlank() } ?: "[مؤلّف غير معروف]"
    val title = r.bookTitle?.takeIf { it.isNotBlank() } ?: "[بدون عنوان]"
    val year = r.bookYear?.takeIf { it.isNotBlank() } ?: "د.ت"
    val page = r.originalPageNumber ?: r.pageNumber.toString()
    val accessYear = Year.now().value

    return when (style) {
        CitationStyle.ARABIC ->
            "$author. $title. ($year). صفحة $page. (مكتبة بحثيّة، استُرجع $accessYear)."
        CitationStyle.APA ->
            "$author ($year). $title. p. $page."
        CitationStyle.MLA ->
            "$author. $title. $year, p. $page."
        CitationStyle.CHICAGO ->
            "$author, $title ($year), $page."
    }
}
