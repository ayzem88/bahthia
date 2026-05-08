package com.bahthia.app.ui.screens.preferences

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bahthia.app.ui.components.BrownButton
import com.bahthia.app.ui.theme.LocalBahthiaColors
import com.bahthia.app.ui.theme.ThemeKind
import com.bahthia.app.ui.theme.ThemeState

/**
 * حوار "التفضيلات" — يجمع:
 *   - السمة (٤ خيارات)
 *   - حجم الخطّ (S / M / L / XL)
 *   - الخطّ العربي (Sakkal Majalla / Amiri / Scheherazade / Cairo)
 *   - الخصوصية (telemetry)
 *
 * Phase 4 سيُضيف الحفظ على القرص. الآن: حالة في الذاكرة.
 */
@Composable
fun PreferencesDialog(
    themeState: ThemeState,
    onClose: () -> Unit,
) {
    val colors = LocalBahthiaColors.current
    val prefs = com.bahthia.app.state.AppRuntime.preferences
    var fontSize by remember { mutableStateOf(prefs.fontSize) }
    var fontFamily by remember { mutableStateOf(prefs.fontFamily) }
    var telemetryEnabled by remember { mutableStateOf(prefs.telemetryEnabled) }
    var mistralKey by remember { mutableStateOf(prefs.mistralApiKey) }
    var keyVisible by remember { mutableStateOf(false) }
    var parallelWorkers by remember { mutableStateOf(prefs.parallelOcrWorkers) }
    var parallelTextWorkers by remember { mutableStateOf(prefs.parallelTextWorkers) }
    val cpuCount = remember { Runtime.getRuntime().availableProcessors() }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.widthIn(min = 600.dp, max = 700.dp).padding(24.dp),
        ) {
            Column(modifier = Modifier.padding(24.dp).verticalScroll(rememberScrollState())) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "⚙️ التفضيلات",
                        style = MaterialTheme.typography.headlineSmall,
                        color = androidx.compose.ui.graphics.Color.White,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                    )
                }

                Spacer(Modifier.height(20.dp))

                // ─── السمة ───
                SectionHeader("السمة")
                ThemeRadio("ترابي (افتراضي)",     ThemeKind.EARTHY,       themeState)
                ThemeRadio("داكن",                 ThemeKind.DARK,         themeState)
                ThemeRadio("رصاصي ChatGPT",        ThemeKind.GRAY_CHATGPT, themeState)
                ThemeRadio("تلقائي (يتبع النظام)", ThemeKind.AUTO,        themeState)

                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = colors.divider)
                Spacer(Modifier.height(16.dp))

                // ─── حجم الخطّ ───
                SectionHeader("حجم الخطّ")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("S" to "صغير", "M" to "متوسط", "L" to "كبير", "XL" to "كبير جدّاً").forEach { (code, name) ->
                        AssistChip(
                            onClick = { fontSize = code },
                            label = { Text(name) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = if (fontSize == code) MaterialTheme.colorScheme.primaryContainer
                                                else MaterialTheme.colorScheme.surface,
                            ),
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = colors.divider)
                Spacer(Modifier.height(16.dp))

                // ─── الخطّ العربي ───
                SectionHeader("الخطّ العربي")
                listOf("Sakkal Majalla", "Amiri", "Scheherazade", "Cairo").forEach { font ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = fontFamily == font,
                            onClick = { fontFamily = font },
                        )
                        Text(font, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = colors.divider)
                Spacer(Modifier.height(16.dp))

                // ─── الخصوصية ───
                SectionHeader("الخصوصية")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = telemetryEnabled,
                        onCheckedChange = { telemetryEnabled = it },
                    )
                    Column {
                        Text(
                            "إرسال إحصاءات استخدام مجهولة الهويّة",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            "(عدد البحثات، الميزات الأكثر استعمالاً — لا بيانات شخصيّة)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = colors.divider)
                Spacer(Modifier.height(16.dp))

                // ─── مفتاح Mistral OCR ───
                SectionHeader("🔑 مفتاح Mistral API (لاستيراد PDF)")
                Text(
                    text = "يُستعمل عند استيراد ملفات PDF لتحويلها إلى نصّ عبر OCR.\n" +
                            "احصل على مفتاحك من: https://console.mistral.ai/api-keys",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = mistralKey,
                    onValueChange = { mistralKey = it },
                    label = { Text("Mistral API Key") },
                    placeholder = { Text("xxxxxxxxxxxxxxxx") },
                    singleLine = true,
                    visualTransformation = if (keyVisible) {
                        androidx.compose.ui.text.input.VisualTransformation.None
                    } else {
                        androidx.compose.ui.text.input.PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        androidx.compose.material3.TextButton(onClick = { keyVisible = !keyVisible }) {
                            Text(if (keyVisible) "إخفاء" else "إظهار")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    com.bahthia.app.ui.components.BrownButton(
                        text = "حفظ المفتاح",
                        onClick = {
                            prefs.mistralApiKey = mistralKey.trim()
                        },
                    )
                    Spacer(Modifier.width(8.dp))
                    if (mistralKey.isNotBlank()) {
                        Text(
                            text = "مُسجَّل (${mistralKey.length} حرفاً)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = colors.divider)
                Spacer(Modifier.height(16.dp))

                // ─── توازي OCR ───
                SectionHeader(com.bahthia.i18n.tr("import.parallel.workers"))
                Text(
                    text = com.bahthia.i18n.tr("import.parallel.workers.hint"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1, 2, 3, 4).forEach { n ->
                        AssistChip(
                            onClick = {
                                parallelWorkers = n
                                prefs.parallelOcrWorkers = n
                            },
                            label = { Text(n.toString()) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = if (parallelWorkers == n) MaterialTheme.colorScheme.primaryContainer
                                                else MaterialTheme.colorScheme.surface,
                            ),
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = colors.divider)
                Spacer(Modifier.height(16.dp))

                // ─── تَوازي استيراد النّصوص (TXT/DOCX) ───
                SectionHeader("خُيوط استيراد النّصوص")
                Text(
                    text = "عَدد الخُيوط المُتَوازية لِقِراءة وفَهرسة TXT/DOCX. " +
                            "كَمبيوترك يَملك $cpuCount نَواة. الموصى: ${cpuCount.coerceIn(2, 8)}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1, 2, 4, 6, 8, 12, 16).filter { it <= maxOf(8, cpuCount * 2) }.forEach { n ->
                        AssistChip(
                            onClick = {
                                parallelTextWorkers = n
                                prefs.parallelTextWorkers = n
                            },
                            label = { Text(n.toString()) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = if (parallelTextWorkers == n) MaterialTheme.colorScheme.primaryContainer
                                                else MaterialTheme.colorScheme.surface,
                            ),
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                Text(
                    text = "💡 السمة وحجم الخطّ والخصوصية تُحفَظ تلقائياً عند تغييرها.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                )

                Spacer(Modifier.height(16.dp))
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
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun ThemeRadio(label: String, kind: ThemeKind, themeState: ThemeState) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RadioButton(
            selected = themeState.currentKind.value == kind,
            onClick = { themeState.switchTo(kind) },
        )
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}
