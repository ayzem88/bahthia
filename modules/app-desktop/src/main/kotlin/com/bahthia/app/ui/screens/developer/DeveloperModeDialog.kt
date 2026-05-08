package com.bahthia.app.ui.screens.developer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bahthia.app.state.AppRuntime
import com.bahthia.app.ui.components.BrownButton
import com.bahthia.domain.AppMetadata
import com.bahthia.lifecycle.publishing.PublisherConfig
import com.bahthia.lifecycle.publishing.PublishingService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path

/**
 * شاشة وضع المطوّر — مخفيّة خلف اختصار `Ctrl+Shift+D`.
 *
 * تُتاح فقط إن وُجد ملفّ `~/.bahthia/publisher.properties` صالح.
 * تَعرض زرّاً واحداً لنشر MSI مبني مسبقاً:
 *   1. البحث عن MSI يطابق `AppMetadata.VERSION`
 *   2. رفع MSI وتحديث version.json عبر cPanel API
 */
@Composable
fun DeveloperModeDialog(onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val config = remember { PublisherConfig.loadOrNull() }

    var releaseNotes by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    var stage by remember { mutableStateOf("") }
    var fraction by remember { mutableStateOf(0f) }
    val log = remember { mutableStateListOf<LogEntry>() }
    var done by remember { mutableStateOf<PublishingService.Result.Success?>(null) }

    Dialog(
        onDismissRequest = { if (!working) onClose() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.widthIn(min = 600.dp, max = 800.dp).padding(16.dp),
        ) {
            Column(
                modifier = Modifier.padding(20.dp).verticalScroll(rememberScrollState()),
            ) {
                Text(
                    "🛠️ وضع المطوّر — نشر إصدار جديد",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "هذا الحوار خاصّ بالمطوّر. لا يَظهر للمستخدم النهائيّ.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )

                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))

                if (config == null) {
                    NoConfigSection()
                } else {
                    ConfigSummary(config)

                    Spacer(Modifier.height(16.dp))
                    Text("الإصدار الحاليّ: ${AppMetadata.VERSION}", fontWeight = FontWeight.Bold)
                    Text(
                        "ملاحظة: لتغيير الإصدار، عدّل `AppMetadata.VERSION` ثمّ أعد بناء MSI قبل النشر.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )

                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = releaseNotes,
                        onValueChange = { releaseNotes = it },
                        label = { Text("ملاحظات الإصدار") },
                        placeholder = { Text("ما الجديد في هذا الإصدار؟") },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
                        minLines = 3,
                    )

                    Spacer(Modifier.height(16.dp))

                    if (working) {
                        Text(stage, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { fraction },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    if (done != null) {
                        Spacer(Modifier.height(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(6.dp),
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text("✅ نُشر بنجاح!", fontWeight = FontWeight.Bold)
                                Text("الإصدار: ${done!!.version}")
                                Text("الحجم: ${humanSize(done!!.sizeBytes)}")
                                Text("MSI: ${done!!.msiUrl}", fontFamily = FontFamily.Monospace,
                                     style = MaterialTheme.typography.bodySmall)
                                Text("API: ${done!!.versionJsonUrl}", fontFamily = FontFamily.Monospace,
                                     style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }

                    if (log.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Text("السجلّ:", fontWeight = FontWeight.Bold)
                        Box(modifier = Modifier.heightIn(max = 240.dp).fillMaxWidth()) {
                            LazyColumn {
                                items(log) { entry ->
                                    Text(
                                        "${entry.icon}  ${entry.text}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = entry.color ?: MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        if (!working) BrownButton(text = "إغلاق", onClick = onClose)
                        Spacer(Modifier.height(0.dp))
                        if (!working && done == null) {
                            androidx.compose.material3.TextButton(onClick = {
                                working = true
                                log.clear()
                                done = null
                                scope.launch {
                                    runPublish(
                                        config = config,
                                        version = AppMetadata.VERSION,
                                        notes = releaseNotes.trim().ifBlank { "تحديث جديد" },
                                        log = log,
                                        onStage = { name, frac -> stage = name; fraction = frac },
                                        onDone = { done = it },
                                    )
                                    working = false
                                }
                            }) { Text("نشر MSI الموجود", color = MaterialTheme.colorScheme.primary,
                                     fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
        }
    }
}

// ─── Helpers ───

private suspend fun runPublish(
    config: PublisherConfig,
    version: String,
    notes: String,
    log: androidx.compose.runtime.snapshots.SnapshotStateList<LogEntry>,
    onStage: (String, Float) -> Unit,
    onDone: (PublishingService.Result.Success) -> Unit,
) {
    withContext(Dispatchers.IO) {
        // 1. ابحث عن MSI المبنيّ
        val projectRoot = Path.of("").toAbsolutePath()
        val msiPath = findMsi(projectRoot, version)
        if (msiPath == null) {
            withContext(Dispatchers.Main) {
                log.add(LogEntry("⚠️", "لم أجد MSI للإصدار $version. شغّل: gradlew :app-desktop:packageMsi", LogColor.WARN))
                log.add(LogEntry("ℹ️", "ابحث في: modules/app-desktop/build/compose/binaries/main/msi/", LogColor.INFO))
            }
            return@withContext
        }
        withContext(Dispatchers.Main) {
            log.add(LogEntry("📦", "وُجد MSI: ${msiPath.fileName}"))
        }

        // 2. نشر
        val service = PublishingService(config)
        val result = service.publish(msiPath, version, notes) { progress ->
            when (progress) {
                is PublishingService.Progress.Stage -> onStage(progress.name, progress.fraction)
                is PublishingService.Progress.Log -> {
                    val color = when (progress.level) {
                        PublishingService.Progress.Level.WARN -> LogColor.WARN
                        PublishingService.Progress.Level.ERROR -> LogColor.ERROR
                        else -> LogColor.INFO
                    }
                    // لا نُحدّث الـ log مباشرةً من thread خلفيّ — نَستعمل launch
                }
            }
        }

        withContext(Dispatchers.Main) {
            when (result) {
                is PublishingService.Result.Success -> {
                    log.add(LogEntry("✅", "نُشر الإصدار ${result.version}"))
                    onDone(result)
                }
                is PublishingService.Result.Failure -> {
                    log.add(LogEntry("❌", "[${result.stage}] ${result.message}", LogColor.ERROR))
                }
            }
        }
    }
}

/**
 * يَعثر على ملفّ MSI المبنيّ في مجلّد jpackage القياسي.
 *
 * ملاحظة: اسم MSI يأتي من `packageVersion` في build.gradle.kts (ثابت تقنيّاً)،
 * بينما الإصدار المعروض في الواجهة يأتي من `AppMetadata.VERSION`.
 * لذلك نَقبل أيّ MSI يُعثر عليه — لا نُطابق الاسم بالإصدار.
 */
private fun findMsi(projectRoot: Path, @Suppress("UNUSED_PARAMETER") version: String): Path? {
    val candidates = listOf(
        projectRoot.resolve("modules/app-desktop/build/compose/binaries/main/msi"),
        projectRoot.resolve("../modules/app-desktop/build/compose/binaries/main/msi"),
    )
    for (dir in candidates) {
        if (!Files.exists(dir)) continue
        val msi = Files.list(dir).use { stream ->
            stream.filter { path -> path.fileName.toString().endsWith(".msi", ignoreCase = true) }
                  .findFirst().orElse(null)
        }
        if (msi != null) return msi
    }
    return null
}

@Composable
private fun NoConfigSection() {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(6.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text("⚠️ ملفّ الإعدادات غير موجود", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                "أنشِئ ملفّاً في:\n${PublisherConfig.defaultPath}\n\n" +
                "بالحقول التالية:\n" +
                "  cpanel.host=...\n" +
                "  cpanel.user=...\n" +
                "  cpanel.token=...\n" +
                "  cpanel.remote.dir=...\n" +
                "  site.base.url=https://www.bahthia.com",
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun ConfigSummary(config: PublisherConfig) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(6.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text("الإعدادات", fontWeight = FontWeight.Bold)
            Text("الخادم: ${config.cpanelHost}:${config.cpanelPort}",
                 style = MaterialTheme.typography.bodySmall)
            Text("المستخدم: ${config.cpanelUser}",
                 style = MaterialTheme.typography.bodySmall)
            Text("التوكن: ${config.cpanelToken.take(4)}…${config.cpanelToken.takeLast(4)}",
                 style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            Text("المسار: ${config.remoteDir}", style = MaterialTheme.typography.bodySmall)
            Text("الموقع: ${config.siteBaseUrl}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

private data class LogEntry(
    val icon: String,
    val text: String,
    val color: Color? = null,
)

private object LogColor {
    val WARN = Color(0xFFE65100)
    val ERROR = Color(0xFFD32F2F)
    val INFO: Color? = null
}

private fun humanSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    bytes < 1024L * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
    else -> "${bytes / (1024L * 1024 * 1024)} GB"
}
