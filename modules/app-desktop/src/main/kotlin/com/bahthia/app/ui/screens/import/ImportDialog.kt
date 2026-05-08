package com.bahthia.app.ui.screens.import

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.bahthia.app.state.ImportViewModel
import com.bahthia.app.ui.components.BahthiaTooltip
import com.bahthia.app.ui.components.BrownButton
import com.bahthia.app.ui.components.LazyColumnWithScrollbar
import com.bahthia.app.ui.components.WindowFileDropTarget
import com.bahthia.app.ui.theme.LocalBahthiaColors
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.JFileChooser

/**
 * حوار الاستيراد الاحترافيّ — لوحة معلومات غنيّة + إلغاء/إيقاف + حفظ آخر مسار + multi-select
 * + إخفاء (يَستمرّ في الخلفيّة).
 *
 * ملكيّة [ImportViewModel] في [AppRuntime.importVM] (singleton)، فالحوار يَقرأ منه فقط
 * ولا يُغلقه عند الإخفاء — هذا ما يُتيح "الاستيراد في الخلفيّة".
 */
@Composable
fun ImportDialog(
    onClose: () -> Unit,
    onImported: () -> Unit,
) {
    val colors = LocalBahthiaColors.current
    val scope = rememberCoroutineScope()
    // ViewModel singleton يَعيش طول حياة التطبيق (انظر AppRuntime.importVM)
    val vm = AppRuntime.importVM
    var showResumeOffer by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<com.bahthia.app.state.ImportProgressStore.Snapshot?>(null) }

    // عند فتح الحوار: لو وُجدت لقطة سابقة لم تَكتمل → نَعرض عرض الاستئناف.
    // لكن إن وُجد استيراد جارٍ في الخلفيّة → نَعرض حالته فلا نُظهر عرض الاستئناف.
    androidx.compose.runtime.LaunchedEffect(Unit) {
        val backgroundActive = vm.state == ImportViewModel.State.RUNNING
                || vm.state == ImportViewModel.State.PAUSED
                || vm.state == ImportViewModel.State.PREPARING
                || vm.state == ImportViewModel.State.FINALIZING
        if (!backgroundActive) {
            val snap = AppRuntime.importProgressStore.load()
            if (snap != null && !snap.isStale && snap.remaining > 0 && snap.pending.isNotEmpty()) {
                showResumeOffer = snap
            }
        }
    }

    // ملاحظة: لا نَستدعي vm.close() عند إغلاق الحوار — VM يَعيش في AppRuntime.

    val running = vm.state == ImportViewModel.State.RUNNING
            || vm.state == ImportViewModel.State.PAUSED
            || vm.state == ImportViewModel.State.PREPARING
            || vm.state == ImportViewModel.State.FINALIZING
    // FINALIZING ليست "finished" — لا يَزال يَحسب الإحصاءات. الزّرّ يَظهر فَقط عند FINISHED.
    val finished = vm.state == ImportViewModel.State.FINISHED
            || vm.state == ImportViewModel.State.CANCELLED

    // سحب وإفلات: تَلقّي ملفّات من Windows Explorer
    WindowFileDropTarget { files ->
        if (running) return@WindowFileDropTarget
        val pdfs = files.filter { it.fileName.toString().lowercase().endsWith(".pdf") }
        val texts = files.filter {
            val n = it.fileName.toString().lowercase()
            n.endsWith(".txt") || n.endsWith(".docx")
        }
        when {
            pdfs.isNotEmpty() && texts.isEmpty() -> {
                AppRuntime.preferences.lastImportFolderPdf = pdfs.first().parent.toString()
                vm.startPdfImport(pdfs, AppRuntime.indexDir, AppRuntime.preferences.mistralApiKey)
            }
            texts.isNotEmpty() && pdfs.isEmpty() -> {
                AppRuntime.preferences.lastImportFolderTxt = texts.first().parent.toString()
                vm.startTextImport(texts, AppRuntime.indexDir)
            }
            else -> { /* مزيج أو فارغ: نَتجاهل لتجنّب الخلط */ }
        }
    }

    Dialog(
        onDismissRequest = { if (!running) onClose() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.widthIn(min = 800.dp, max = 1000.dp).padding(24.dp),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                // ─── الترويسة ───
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "📥 استيراد الكتب إلى المكتبة",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                    )
                }

                Spacer(Modifier.height(16.dp))

                showResumeOffer?.let { snap ->
                    ResumeOffer(
                        snap = snap,
                        onResume = {
                            val apiKey = AppRuntime.preferences.mistralApiKey
                            when (snap.kind) {
                                "PDF"  -> vm.startPdfImport(snap.pending, AppRuntime.indexDir, apiKey)
                                "TEXT" -> vm.startTextImport(snap.pending, AppRuntime.indexDir)
                            }
                            showResumeOffer = null
                        },
                        onDiscard = {
                            AppRuntime.importProgressStore.clear()
                            showResumeOffer = null
                        },
                    )
                    Spacer(Modifier.height(16.dp))
                }

                if (vm.state == ImportViewModel.State.IDLE) {
                    SourceChoices(vm = vm, scope = scope)
                } else {
                    InfoPanel(vm = vm)
                }

                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = colors.divider)
                Spacer(Modifier.height(8.dp))

                // ─── السجلّ ───
                if (vm.log.isNotEmpty()) {
                    Text(
                        text = "السجلّ:",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Box(modifier = Modifier.heightIn(max = 220.dp).fillMaxWidth()) {
                        LazyColumnWithScrollbar {
                            items(vm.log.size) { i ->
                                val entry = vm.log[i]
                                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                                    Text(text = entry.icon, modifier = Modifier.padding(end = 6.dp))
                                    Text(
                                        text = entry.text,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = entry.color ?: MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // ─── أزرار التحكّم ───
                ControlButtons(
                    vm = vm,
                    running = running,
                    finished = finished,
                    onClose = onClose,
                    onImported = onImported,
                    onHide = onClose, // إخفاء = إغلاق الحوار، لكنّ المهمّة تُتابِع في الخلفيّة
                )
            }
        }
    }
}

// ─── شاشة اختيار المصدر ───────────────────────────────────────

@Composable
private fun SourceChoices(vm: ImportViewModel, scope: kotlinx.coroutines.CoroutineScope) {
    Text(
        text = "اختر مصدر الكتب:",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
    )
    Spacer(Modifier.height(12.dp))

    SectionLabel("📝 ملفات نصّيّة جاهزة (TXT أو DOCX)")
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        BahthiaTooltip(text = "اختر ملفّاً واحداً أو عدّة ملفّات (Ctrl+click)") {
            BrownButton(text = "📄 ملفّات TXT/DOCX", onClick = {
                val files = pickFiles(arrayOf("txt", "docx"), "اختر ملفّات TXT/DOCX",
                    initialDir = AppRuntime.preferences.lastImportFolderTxt)
                if (files.isNotEmpty()) {
                    AppRuntime.preferences.lastImportFolderTxt = files.first().parent.toString()
                    vm.startTextImport(files, AppRuntime.indexDir)
                }
            })
        }
        BahthiaTooltip(text = "كلّ TXT/DOCX في المجلّد المختار (وفروعه)") {
            BrownButton(text = "📁 مجلَّد TXT/DOCX", onClick = {
                val dir = pickDirectory("اختر مجلّداً", initialDir = AppRuntime.preferences.lastImportFolderTxt)
                if (dir != null) {
                    AppRuntime.preferences.lastImportFolderTxt = dir.toString()
                    val files = collectFiles(dir, listOf("txt", "docx"))
                    if (files.isNotEmpty()) vm.startTextImport(files, AppRuntime.indexDir)
                }
            })
        }
    }

    Spacer(Modifier.height(20.dp))

    SectionLabel("📕 كتب PDF (تحويل OCR ثمّ استيراد)")
    val apiKey = AppRuntime.preferences.mistralApiKey
    if (apiKey.isBlank()) {
        Text(
            text = "⚠️ مفتاح Mistral API غير مضبوط — افتح الإعدادات → التفضيلات",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(vertical = 4.dp),
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        BahthiaTooltip(text = "اختر ملفّاً أو عدّة ملفّات PDF") {
            BrownButton(text = "📕 ملفّات PDF", onClick = {
                val files = pickFiles(arrayOf("pdf"), "اختر ملفّات PDF",
                    initialDir = AppRuntime.preferences.lastImportFolderPdf)
                if (files.isNotEmpty()) {
                    AppRuntime.preferences.lastImportFolderPdf = files.first().parent.toString()
                    vm.startPdfImport(files, AppRuntime.indexDir, apiKey)
                }
            })
        }
        BahthiaTooltip(text = "كلّ PDFs في المجلّد المختار") {
            BrownButton(text = "📚 مجلَّد PDFs", onClick = {
                val dir = pickDirectory("اختر مجلّد PDFs", initialDir = AppRuntime.preferences.lastImportFolderPdf)
                if (dir != null) {
                    AppRuntime.preferences.lastImportFolderPdf = dir.toString()
                    val files = collectFiles(dir, listOf("pdf"))
                    if (files.isNotEmpty()) vm.startPdfImport(files, AppRuntime.indexDir, apiKey)
                }
            })
        }
    }

    Spacer(Modifier.height(8.dp))
    Text(
        text = "💡 يَتذكّر التطبيق آخر مجلّد اخترتَه ويَفتحه تلقائياً في المرّة القادمة.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
    )
    Text(
        text = "🖱 يُمكنك أيضاً سحب الملفّات من Windows Explorer وإفلاتها هنا — يَكتشف النوع تلقائياً.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
    )
}

// ─── لوحة المعلومات الغنيّة ─────────────────────────────────────

@Composable
private fun InfoPanel(vm: ImportViewModel) {
    val total = vm.totalFiles
    val processed = vm.doneCount + vm.errorCount
    val overall = if (total > 0) processed.toFloat() / total else 0f

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // عنوان حالة
        Text(
            text = stateLabel(vm.state, processed, total),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = stateColor(vm.state),
        )

        // الملفّ الحاليّ
        vm.currentFile?.let { name ->
            Text(
                text = "الملفّ الحاليّ: $name",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        if (vm.stageMessage.isNotBlank()) {
            Text(
                text = vm.stageMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
        }

        // شريط تقدّم إجمالي
        LinearProgressIndicator(
            progress = { overall },
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(4.dp))

        // الإحصاءات في صفّين
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            StatCell("✅ مُنجَز",    "${vm.doneCount} / $total")
            StatCell("📄 صفحات",    "${vm.pagesIndexed}")
            StatCell("⚠️ أخطاء",    "${vm.errorCount}")
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            StatCell("⏱ مضى",      formatDuration(vm.elapsedSec))
            StatCell("⏰ متوقَّع",   if (vm.etaSec >= 0) formatDuration(vm.etaSec) else "—")
            StatCell("⚡ السرعة",   String.format("%.1f", vm.filesPerMin) + " /دقيقة")
        }
    }
}

@Composable
private fun StatCell(label: String, value: String) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
        modifier = Modifier.padding(horizontal = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

private fun stateLabel(state: ImportViewModel.State, done: Int, total: Int): String = when (state) {
    ImportViewModel.State.IDLE       -> ""
    ImportViewModel.State.PREPARING  -> "🔄 جارٍ التحضير..."
    ImportViewModel.State.RUNNING    -> "▶ جارٍ الاستيراد ($done / $total)"
    ImportViewModel.State.PAUSED     -> "⏸ متوقّف مؤقّتاً ($done / $total)"
    ImportViewModel.State.FINALIZING -> "⚙ جارٍ تَحضير المَكتبة لِلعَرض…"
    ImportViewModel.State.CANCELLED  -> "⏹ أُلغي — حُفظ $done من $total"
    ImportViewModel.State.FINISHED   -> "✅ اكتمل — $done مُستورَد، المَكتبة جاهزة"
    ImportViewModel.State.ERROR      -> "❌ فشل"
}

@Composable
private fun stateColor(state: ImportViewModel.State): Color = when (state) {
    ImportViewModel.State.ERROR      -> MaterialTheme.colorScheme.error
    ImportViewModel.State.CANCELLED  -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    ImportViewModel.State.PAUSED     -> Color(0xFFFFA726)
    ImportViewModel.State.FINALIZING -> Color(0xFF1976D2)  // أَزرق — تَحضير
    ImportViewModel.State.FINISHED   -> Color(0xFF2E7D32)
    else                              -> MaterialTheme.colorScheme.primary
}

// ─── أزرار التحكّم ───────────────────────────────────────────

@Composable
private fun ControlButtons(
    vm: ImportViewModel,
    running: Boolean,
    finished: Boolean,
    onClose: () -> Unit,
    onImported: () -> Unit,
    onHide: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when {
            vm.state == ImportViewModel.State.RUNNING -> {
                BahthiaTooltip(text = "إيقاف مؤقّت بين الملفّات (يَستأنف بعد ذلك)") {
                    BrownButton(text = "⏸ إيقاف مؤقّت", onClick = { vm.pause() })
                }
                BahthiaTooltip(text = "إلغاء فوريّ — يَحفظ ما تَمّ استيراده") {
                    BrownButton(text = "⏹ إلغاء", onClick = { vm.cancel() })
                }
            }
            vm.state == ImportViewModel.State.PAUSED -> {
                BahthiaTooltip(text = "متابعة من حيث توقّفنا") {
                    BrownButton(text = "▶ استئناف", onClick = { vm.resume() })
                }
                BahthiaTooltip(text = "إلغاء وحفظ المُنجَز") {
                    BrownButton(text = "⏹ إلغاء", onClick = { vm.cancel() })
                }
            }
            else -> {} // IDLE / PREPARING — لا شيء
        }
        Spacer(Modifier.weight(1f))
        // زرّ "إخفاء" — يَظهر فقط أثناء استيراد جارٍ ليَنقل المهمّة إلى الخلفيّة
        if (running) {
            BahthiaTooltip(text = com.bahthia.i18n.tr("import.minimize.tooltip")) {
                BrownButton(text = "🗕 ${com.bahthia.i18n.tr("import.minimize")}", onClick = onHide)
            }
        }
        if (finished) {
            BrownButton(text = "إغلاق وتحديث المكتبة", onClick = {
                onImported()
                vm.reset() // مسح الحالة كي يَبدأ المستخدم استيراداً جديداً عند إعادة الفتح
                onClose()
            })
        } else if (!running) {
            BrownButton(text = "إغلاق", onClick = {
                // إغلاق طبيعي (IDLE/CANCELLED/ERROR): نَمسح الحالة
                vm.reset()
                onClose()
            })
        }
    }
}

// ─── عَرض استئناف بعد الانهيار ─────────────────────────────────

@Composable
private fun ResumeOffer(
    snap: com.bahthia.app.state.ImportProgressStore.Snapshot,
    onResume: () -> Unit,
    onDiscard: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "🔄 وُجدت مهمّة استيراد سابقة لم تَكتمل",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "النوع: ${if (snap.kind == "PDF") "PDFs" else "TXT/DOCX"} · " +
                       "اكتمل: ${snap.completed}/${snap.total} · متبقٍّ: ${snap.remaining}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BrownButton(text = "▶ استئناف", onClick = onResume)
                BrownButton(text = "تجاهل", onClick = onDiscard)
            }
        }
    }
}

// ─── أدوات مساعدة ────────────────────────────────────────────

private fun LazyListScope.items(count: Int, content: @Composable (Int) -> Unit) {
    items(count = count) { content(it) }
}
private typealias LazyListScope = androidx.compose.foundation.lazy.LazyListScope

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}

private fun formatDuration(secs: Long): String {
    if (secs < 60) return "${secs}ث"
    val m = secs / 60
    val s = secs % 60
    if (m < 60) return "${m}د ${s}ث"
    val h = m / 60
    val mm = m % 60
    return "${h}س ${mm}د"
}

// ─── اختيار الملفّات (multi-select + initial path) ──────────────────

private fun pickFiles(extensions: Array<String>, title: String, initialDir: String = ""): List<Path> {
    val chooser = JFileChooser().apply {
        dialogTitle = title
        isMultiSelectionEnabled = true
        fileSelectionMode = JFileChooser.FILES_ONLY
        if (initialDir.isNotBlank()) {
            val d = File(initialDir)
            if (d.exists() && d.isDirectory) currentDirectory = d
        }
        fileFilter = object : javax.swing.filechooser.FileFilter() {
            override fun accept(f: File): Boolean {
                if (f.isDirectory) return true
                val name = f.name.lowercase()
                return extensions.any { name.endsWith(".$it") }
            }
            override fun getDescription(): String = extensions.joinToString(", ") { ".$it" }
        }
    }
    val result = chooser.showOpenDialog(null)
    if (result != JFileChooser.APPROVE_OPTION) return emptyList()
    val selected = chooser.selectedFiles
    return if (selected.isNullOrEmpty()) {
        chooser.selectedFile?.let { listOf(it.toPath()) } ?: emptyList()
    } else {
        selected.map { it.toPath() }
    }
}

private fun pickDirectory(title: String, initialDir: String = ""): Path? {
    val chooser = JFileChooser().apply {
        dialogTitle = title
        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        if (initialDir.isNotBlank()) {
            val d = File(initialDir)
            if (d.exists() && d.isDirectory) currentDirectory = d
        }
    }
    val result = chooser.showOpenDialog(null)
    return if (result == JFileChooser.APPROVE_OPTION) chooser.selectedFile?.toPath() else null
}

private fun collectFiles(dir: Path, extensions: List<String>): List<Path> {
    if (!Files.isDirectory(dir)) return emptyList()
    return Files.walk(dir).use { stream ->
        stream
            .filter { Files.isRegularFile(it) }
            .filter { p ->
                val name = p.fileName.toString().lowercase()
                extensions.any { name.endsWith(".$it") }
            }
            .sorted()
            .toList()
    }
}
