package com.bahthia.app.ui.screens.lifecycle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.bahthia.app.state.AppRuntime
import com.bahthia.app.ui.components.BrownButton
import com.bahthia.lifecycle.AutoUpdater
import com.bahthia.lifecycle.BackupManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.net.URI
import java.nio.file.Path

// ─────────────────────────────────────────────────────────────────────
// 1) حوار التحقّق من التحديثات
// ─────────────────────────────────────────────────────────────────────

@Composable
fun CheckUpdatesDialog(onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var info by remember { mutableStateOf<AutoUpdater.UpdateInfo?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        scope.launch {
            try {
                info = withContext(Dispatchers.IO) { AppRuntime.autoUpdater.checkForUpdates() }
            } catch (e: Exception) {
                error = e.message ?: "خطأ غير معروف"
            } finally {
                loading = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("🔄 التحقّق من التحديثات") },
        text = {
            Column {
                when {
                    loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.padding(end = 12.dp))
                        Text("جارٍ الاتّصال بـ bahthia.com…")
                    }
                    info != null -> {
                        Text(
                            "🎉 يتوفّر إصدار جديد!",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("الإصدار: ${info!!.version}")
                        if (info!!.releaseDate.isNotBlank()) Text("التاريخ: ${info!!.releaseDate}")
                        if (info!!.notes.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text("الجديد:", fontWeight = FontWeight.Bold)
                            Text(info!!.notes)
                        }
                    }
                    error != null -> Text(
                        "⚠️ فشل الاتّصال: $error\n(تأكّد من الإنترنت)",
                        color = MaterialTheme.colorScheme.error,
                    )
                    else -> Text("✓ أنت على آخر إصدار.")
                }
            }
        },
        confirmButton = {
            if (info != null && info!!.downloadUrl.isNotBlank()) {
                TextButton(onClick = {
                    openLink(info!!.downloadUrl)
                    onClose()
                }) { Text("تنزيل الآن") }
            } else {
                TextButton(onClick = onClose) { Text("حسناً") }
            }
        },
        dismissButton = if (info != null) {
            { TextButton(onClick = onClose) { Text("لاحقاً") } }
        } else null,
    )
}

// ─────────────────────────────────────────────────────────────────────
// 2) حوار النسخ الاحتياطي (تصدير)
// ─────────────────────────────────────────────────────────────────────

@Composable
fun BackupExportDialog(onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var working by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<BackupManager.BackupResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!working) onClose() },
        title = { Text("📦 تصدير المكتبة") },
        text = {
            Column {
                Text(
                    "ستُنشأ نسخة احتياطية تحتوي على:\n" +
                            "• فهرس Lucene (الكتب والصفحات)\n" +
                            "• ملفّ التفضيلات (السمة، مفتاح Mistral، إلخ)\n" +
                            "• إحصاءات الاستعمال (إن مُفعّلة)\n\n" +
                            "المكان: ${AppRuntime.backupManager.defaultBackupDir}"
                )
                Spacer(Modifier.height(12.dp))
                when {
                    working -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.padding(end = 12.dp))
                        Text("جارٍ الضغط…")
                    }
                    result != null -> Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(6.dp),
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text("✓ تمّ بنجاح", fontWeight = FontWeight.Bold)
                            Text("الملفّ: ${result!!.backupFile.fileName}")
                            Text("الحجم: ${humanSize(result!!.sizeBytes)}")
                            Text("عدد الملفّات: ${result!!.filesIncluded}")
                        }
                    }
                    error != null -> Text("⚠️ فشل: $error", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            when {
                result != null -> TextButton(onClick = {
                    openFolder(result!!.backupFile.parent)
                    onClose()
                }) { Text("فتح المجلد") }
                !working && error == null -> TextButton(onClick = {
                    working = true
                    scope.launch {
                        try {
                            result = withContext(Dispatchers.IO) {
                                AppRuntime.backupManager.createBackup()
                            }
                            AppRuntime.telemetry.recordFeatureUsage("backup.export")
                        } catch (e: Exception) {
                            error = e.message ?: "خطأ غير معروف"
                        } finally {
                            working = false
                        }
                    }
                }) { Text("ابدأ التصدير") }
                else -> TextButton(onClick = onClose, enabled = !working) { Text("حسناً") }
            }
        },
        dismissButton = if (result == null && !working) {
            { TextButton(onClick = onClose) { Text("إلغاء") } }
        } else null,
    )
}

// ─────────────────────────────────────────────────────────────────────
// 3) حوار النسخ الاحتياطي (استعادة)
// ─────────────────────────────────────────────────────────────────────

@Composable
fun BackupRestoreDialog(
    onClose: () -> Unit,
    onBeforeRestore: () -> Unit = {},
    onRestoreFailed: () -> Unit = {},
    onRestored: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val backups = remember { AppRuntime.backupManager.listBackups() }
    var selected by remember { mutableStateOf<Path?>(null) }
    var working by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = { if (!working) onClose() }) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.widthIn(min = 500.dp, max = 700.dp).padding(16.dp),
        ) {
            Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState())) {
                Text("♻️ استعادة من نسخة احتياطية", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))

                if (backups.isEmpty()) {
                    Text(
                        "لا توجد نسخ احتياطية في المجلد الافتراضي:\n${AppRuntime.backupManager.defaultBackupDir}",
                        color = MaterialTheme.colorScheme.error,
                    )
                } else {
                    Text("اختر النسخة المراد استعادتها:")
                    Spacer(Modifier.height(8.dp))
                    backups.forEach { p ->
                        val isSelected = p == selected
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TextButton(onClick = { selected = p }) {
                                    Text(p.fileName.toString())
                                }
                                Spacer(Modifier.weight(1f))
                                Text(humanSize(java.nio.file.Files.size(p)))
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                if (working) Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.padding(end = 12.dp))
                    Text("جارٍ الاستعادة…")
                }
                if (done) Text(
                    "✓ تمّت الاستعادة بنجاح. تم تحديث المكتبة ومحرك البحث.",
                    color = MaterialTheme.colorScheme.primary,
                )
                if (error != null) Text("⚠️ فشل: $error", color = MaterialTheme.colorScheme.error)

                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    if (!done) BrownButton(text = "إلغاء", onClick = onClose)
                    Spacer(Modifier.height(0.dp))
                    if (selected != null && !done && !working) {
                        TextButton(onClick = {
                            working = true
                            scope.launch {
                                try {
                                    onBeforeRestore()
                                    withContext(Dispatchers.IO) {
                                        AppRuntime.backupManager.restoreBackup(selected!!)
                                    }
                                    AppRuntime.telemetry.recordFeatureUsage("backup.restore")
                                    done = true
                                    onRestored()
                                } catch (e: Exception) {
                                    onRestoreFailed()
                                    error = e.message ?: "خطأ غير معروف"
                                } finally {
                                    working = false
                                }
                            }
                        }) { Text("استعادة") }
                    }
                    if (done) BrownButton(text = "إغلاق", onClick = onClose)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────
// أدوات
// ─────────────────────────────────────────────────────────────────────

private fun humanSize(bytes: Long): String = when {
    bytes < 1024            -> "$bytes B"
    bytes < 1024 * 1024     -> "${bytes / 1024} KB"
    bytes < 1024L * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
    else -> "${bytes / (1024 * 1024 * 1024)} GB"
}

private fun openLink(uri: String) {
    try {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(URI(uri))
        }
    } catch (_: Exception) { /* ignore */ }
}

private fun openFolder(folder: Path) {
    try {
        if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(folder.toFile())
    } catch (_: Exception) { /* ignore */ }
}
