package com.bahthia.app.ui.screens.favorites

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bahthia.app.state.AppRuntime
import com.bahthia.app.state.SavedSearch
import com.bahthia.app.state.SavedSearchStore
import com.bahthia.app.ui.components.BrownButton
import com.bahthia.app.ui.components.LazyColumnWithScrollbar
import com.bahthia.app.ui.theme.LocalBahthiaColors
import java.awt.FileDialog
import java.awt.Frame
import java.nio.file.Path

/**
 * حِوار "المفضّلة" — قائمة جَلسات البَحث المحفوظة.
 *   - استرجاع جَلسة (يَملأ شريط البَحث + المُرشّحات + النتائج)
 *   - حَذف جَلسة وَاحِدة
 *   - حَذف الكلّ
 *   - تَصدير الكلّ إلى Word
 */
@Composable
fun FavoritesDialog(
    onClose: () -> Unit,
    onApply: (SavedSearch) -> Unit,
) {
    val colors = LocalBahthiaColors.current
    val store = AppRuntime.savedSearchStore
    val items = remember { mutableStateListOf<SavedSearch>() }
    var confirmClearAll by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        items.clear(); items.addAll(store.list())
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.widthIn(min = 600.dp, max = 800.dp).padding(24.dp),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                // الترويسة
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.Star,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = "المفضّلة — جَلسات البَحث",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "إغلاق")
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "العَدد: ${items.size} / ${SavedSearchStore.MAX_FAVORITES}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )

                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = colors.divider)
                Spacer(Modifier.height(8.dp))

                // القائمة
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFFDDDDDD)),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp, max = 400.dp),
                ) {
                    if (items.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = "(لا توجد جَلسات مَحفوظة بَعد. ابحث عن شيء وانقر ★ في صَندوق الكُنّاشة لِحفظ الجَلسة.)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(24.dp),
                            )
                        }
                    } else {
                        LazyColumnWithScrollbar {
                            items(items.size) { i ->
                                FavoriteRow(
                                    item = items[i],
                                    onApply = {
                                        onApply(items[i])
                                        onClose()
                                    },
                                    onDelete = {
                                        if (store.delete(items[i].id)) {
                                            items.removeAt(i)
                                        }
                                    },
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // الأَزرار السُفليّة
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    BrownButton(
                        text = "تَصدير الكلّ إلى Word",
                        onClick = {
                            if (items.isEmpty()) {
                                notice = "لا توجد جَلسات للتَصدير"
                            } else {
                                val path = pickSavePath()
                                if (path != null) {
                                    notice = if (store.exportAllToDocx(path)) "تَمّ التَصدير: ${path.fileName}"
                                             else                              "تَعذّر التَصدير"
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                    BrownButton(
                        text = "حَذف الكلّ",
                        onClick = { if (items.isNotEmpty()) confirmClearAll = true },
                        modifier = Modifier.weight(1f),
                    )
                    BrownButton(
                        text = "إغلاق",
                        onClick = onClose,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }

    if (confirmClearAll) {
        AlertDialog(
            onDismissRequest = { confirmClearAll = false },
            title = { Text("حَذف كلّ الجَلسات؟") },
            text = { Text("سيُحذف ${items.size} جَلسة. لا يُمكن التَراجع.") },
            confirmButton = {
                TextButton(onClick = {
                    val n = store.clear()
                    items.clear()
                    notice = "حُذِفت $n جَلسة"
                    confirmClearAll = false
                }) { Text("نَعَم، احذف الكلّ", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearAll = false }) { Text("إلغاء") }
            },
        )
    }

    notice?.let { msg ->
        AlertDialog(
            onDismissRequest = { notice = null },
            title = { Text("ملاحظة") },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = { notice = null }) { Text("حسناً") }
            },
        )
    }
}

@Composable
private fun FavoriteRow(
    item: SavedSearch,
    onApply: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            val summary = buildString {
                append("${item.results.size} نَتيجة")
                val filters = mutableListOf<String>()
                if (item.selectedCategories.isNotEmpty()) filters += "فئات: ${item.selectedCategories.size}"
                if (item.selectedYears.isNotEmpty())      filters += "سنوات: ${item.selectedYears.size}"
                if (item.selectedCountries.isNotEmpty())  filters += "دُول: ${item.selectedCountries.size}"
                if (item.selectedBookIds.isNotEmpty())    filters += "كتب: ${item.selectedBookIds.size}"
                if (filters.isNotEmpty()) {
                    append(" · ")
                    append(filters.joinToString(" · "))
                }
            }
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onApply) {
            Icon(
                Icons.Outlined.PlayArrow,
                contentDescription = "تَطبيق",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Outlined.DeleteOutline,
                contentDescription = "حَذف",
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
    HorizontalDivider(color = Color(0xFFF5F0EB), thickness = 1.dp)
}

private fun LazyListScope.items(count: Int, content: @Composable (Int) -> Unit) {
    items(count = count) { content(it) }
}

private typealias LazyListScope = androidx.compose.foundation.lazy.LazyListScope

/** يَفتح FileDialog أصلي لِنَظام التَشغيل لاختيار وُجهة الحفظ بِامتداد .docx. */
private fun pickSavePath(): Path? {
    val dlg = FileDialog(null as Frame?, "تَصدير المفضّلة إلى Word", FileDialog.SAVE)
    dlg.file = "bahthia-favorites.docx"
    dlg.isVisible = true
    val name = dlg.file ?: return null
    val dir = dlg.directory ?: return null
    val full = if (name.endsWith(".docx", ignoreCase = true)) name else "$name.docx"
    return Path.of(dir, full)
}
