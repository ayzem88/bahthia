package com.bahthia.app.ui.screens.statistics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bahthia.app.state.LibraryViewModel
import com.bahthia.app.ui.components.BrownButton
import com.bahthia.app.ui.screens.categories.displayCategoryName
import com.bahthia.app.ui.theme.LocalBahthiaColors

/**
 * حوار "الإحصاءات" — يعرض:
 *   - عدد الكتب الإجمالي
 *   - عدد الصفحات الإجمالي
 *   - توزيع الفئات
 *   - أكثر المؤلفين تكراراً
 */
@Composable
fun StatisticsDialog(
    libraryViewModel: LibraryViewModel,
    onClose: () -> Unit,
) {
    val colors = LocalBahthiaColors.current

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.widthIn(min = 600.dp, max = 800.dp).padding(24.dp),
        ) {
            Column(modifier = Modifier.padding(24.dp).verticalScroll(rememberScrollState())) {
                // الترويسة
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "📊 إحصاءات المكتبة البحثيّة",
                        style = MaterialTheme.typography.headlineSmall,
                        color = androidx.compose.ui.graphics.Color.White,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                    )
                }

                Spacer(Modifier.height(20.dp))

                // البطاقات الكبرى
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    StatCard(
                        label = "الكتب",
                        value = libraryViewModel.totalBooks.toString(),
                        modifier = Modifier.weight(1f),
                    )
                    StatCard(
                        label = "الصفحات",
                        value = libraryViewModel.totalPages.toString(),
                        modifier = Modifier.weight(1f),
                    )
                    StatCard(
                        label = "الفئات",
                        value = libraryViewModel.categories.size.toString(),
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(Modifier.height(24.dp))
                HorizontalDivider(color = colors.divider)
                Spacer(Modifier.height(16.dp))

                // توزيع الفئات
                Text(
                    text = "توزيع الفئات",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(8.dp))

                if (libraryViewModel.categories.isEmpty()) {
                    Text(
                        text = "(لا توجد بيانات بعد — حمّل بياناتك عبر زرّ الترحيل)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    )
                } else {
                    libraryViewModel.categories.take(20).forEach { cat ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text(
                                text = displayCategoryName(cat.name),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = cat.count.toString(),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        HorizontalDivider(color = colors.divider, thickness = 1.dp)
                    }
                }

                Spacer(Modifier.height(24.dp))

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
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(20.dp).fillMaxWidth(),
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
        }
    }
}
