package com.bahthia.app.ui.screens.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Subject
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Icon
import com.bahthia.app.state.AppRuntime
import com.bahthia.app.state.ImportViewModel
import com.bahthia.app.state.LibraryViewModel
import com.bahthia.app.state.SearchViewModel
import com.bahthia.app.ui.components.BahthiaTooltip
import com.bahthia.app.ui.components.BrownButton
import com.bahthia.app.ui.components.SettingsMenu
import com.bahthia.app.ui.components.bahthiaVerticalSplitter
import com.bahthia.app.ui.screens.books.BookEntry
import com.bahthia.app.ui.screens.books.BooksPanel
import com.bahthia.app.ui.screens.categories.CategoriesPanel
import com.bahthia.app.ui.screens.display.DisplayPanel
import com.bahthia.app.ui.screens.result.BookReaderDialog
import com.bahthia.app.ui.screens.search.SearchBar
import com.bahthia.app.ui.theme.LocalBahthiaColors

/**
 * الشاشة الرئيسية — تخطيط ٣ أعمدة مطابق لـ Python.
 * تستعمل [LibraryViewModel] لقراءة الفئات والكتب من Lucene الحقيقي
 * (لا أرقام مزيّفة).
 */
@Composable
fun MainScreen(
    searchViewModel: SearchViewModel,
    libraryViewModel: LibraryViewModel,
    onShowAbout: () -> Unit,
    onShowStats: () -> Unit,
    onShowPreferences: () -> Unit,
    onShowImport: () -> Unit,
    onShowClear: () -> Unit,
    onShowFavorites: () -> Unit = {},
    onCheckUpdates: () -> Unit = {},
    onBackupExport: () -> Unit = {},
    onBackupRestore: () -> Unit = {},
) {
    // تحميل البيانات الحقيقيّة عند فتح الشاشة
    LaunchedEffect(Unit) {
        if (!libraryViewModel.loaded) libraryViewModel.load()
        // مُزامَنة معيار البَحث الزَّمنيّ من التَفضيلات إلى SearchViewModel
        searchViewModel.timeMode = com.bahthia.app.state.AppRuntime.preferences.timeMode
    }

    LaunchedEffect(
        libraryViewModel.viewMode,
        libraryViewModel.categories.map { it.name to it.selected },
        libraryViewModel.books.map { it.id to it.selected },
    ) {
        searchViewModel.selectedCategories = libraryViewModel.effectiveSelectedCategories()
        searchViewModel.selectedYears      = libraryViewModel.effectiveSelectedYears()
        searchViewModel.selectedCountries  = libraryViewModel.effectiveSelectedCountries()
        searchViewModel.selectedBookIds    = libraryViewModel.effectiveSelectedBookIds()
    }

    val colors = LocalBahthiaColors.current

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(10.dp)) {
            // ملاحظة: نُقلت أيقونة الإعدادات إلى نهاية الصف الثاني داخل SearchBar
            //         (تحت زرّ "امسح" مباشرة) — لا حاجة لـ TopBar المستقلّ.
            SearchBar(
                viewModel = searchViewModel,
                settingsMenu = {
                    SettingsMenu(
                        onShowAbout = onShowAbout,
                        onShowStats = onShowStats,
                        onShowPreferences = onShowPreferences,
                        onShowImport = onShowImport,
                        onShowDelete = onShowClear,
                        onShowFavorites = onShowFavorites,
                        onCheckUpdates = onCheckUpdates,
                        onBackupExport = onBackupExport,
                        onBackupRestore = onBackupRestore,
                        searchViewModel = searchViewModel,
                        onTimeModeChange = {
                            // إعادة تَجميع المكتبة وفقَ المِحوَر الزَّمنيّ الجَديد
                            libraryViewModel.invalidate()
                            libraryViewModel.load()
                        },
                    )
                },
            )
            Spacer(Modifier.height(4.dp))

            // التخطيط الثلاثي (RTL: من اليمين) — قابل للسحب وإعادة التحجيم
            ResizablePanels(
                searchViewModel = searchViewModel,
                libraryViewModel = libraryViewModel,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )

            Spacer(Modifier.height(4.dp))
            // شريط حالة الاستيراد في الخلفيّة — يَظهر فقط عند وجود مهمّة جارية
            BackgroundImportBar(onReopen = onShowImport)
            StatusBar(searchViewModel, libraryViewModel)
        }
    }
}

/**
 * شريط رفيع يَظهر أعلى شريط الحالة عند وجود استيراد جارٍ في الخلفيّة
 * (المستخدم أَخفى حوار الاستيراد لكنّ المهمّة لا تَزال تَعمل).
 *
 * يَعرض: عدد الملفّات / الإجمالي · الزمن المنقضي · السرعة، مع زرّ "إعادة فتح" و "إلغاء".
 */
@Composable
private fun BackgroundImportBar(onReopen: () -> Unit) {
    val vm = AppRuntime.importVM
    val isActive = vm.state == ImportViewModel.State.RUNNING
            || vm.state == ImportViewModel.State.PAUSED
            || vm.state == ImportViewModel.State.PREPARING
            || vm.state == ImportViewModel.State.FINALIZING
    if (!isActive) return

    val total = vm.totalFiles
    val processed = vm.doneCount + vm.errorCount
    val elapsed = formatBgDuration(vm.elapsedSec)
    val speed = String.format("%.1f", vm.filesPerMin)

    val pausedSuffix = if (vm.state == ImportViewModel.State.PAUSED) "  ⏸" else ""

    Surface(
        shape = RoundedCornerShape(3.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${com.bahthia.i18n.tr("import.bgbar.prefix")} $processed/$total — $elapsed — ⚡ $speed/د$pausedSuffix",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.weight(1f),
            )
            BahthiaTooltip(text = com.bahthia.i18n.tr("import.bgbar.reopen")) {
                BrownButton(text = com.bahthia.i18n.tr("import.bgbar.reopen"), onClick = onReopen)
            }
            Spacer(Modifier.width(6.dp))
            BahthiaTooltip(text = com.bahthia.i18n.tr("import.bgbar.cancel")) {
                BrownButton(text = "⏹ ${com.bahthia.i18n.tr("import.bgbar.cancel")}", onClick = { vm.cancel() })
            }
        }
    }
}

private fun formatBgDuration(secs: Long): String {
    if (secs < 60) return "${secs}ث"
    val m = secs / 60
    val s = secs % 60
    if (m < 60) return "${m}د ${s}ث"
    val h = m / 60
    val mm = m % 60
    return "${h}س ${mm}د"
}

/**
 * تَخطيط الأعمدة الثلاثة بفواصل قابلة للسحب — يَستعمل JetBrains SplitPane.
 * يَتذكّر مواقع الفواصل في [com.bahthia.lifecycle.UserPreferences].
 */
@OptIn(org.jetbrains.compose.splitpane.ExperimentalSplitPaneApi::class)
@Composable
private fun ResizablePanels(
    searchViewModel: SearchViewModel,
    libraryViewModel: LibraryViewModel,
    modifier: Modifier = Modifier,
) {
    val prefs = AppRuntime.preferences
    val outerState = org.jetbrains.compose.splitpane.rememberSplitPaneState(prefs.splitCategories)
    val innerState = org.jetbrains.compose.splitpane.rememberSplitPaneState(prefs.splitBooks)

    com.bahthia.app.ui.components.ObserveSplitterPosition(outerState) { prefs.splitCategories = it }
    com.bahthia.app.ui.components.ObserveSplitterPosition(innerState) { prefs.splitBooks = it }

    // الكتاب المُختار للعرض الكامل (يُفتح بالنّقر المزدوج على اسم الكتاب في عمود الكتب)
    var openedBook: BookEntry? by remember { mutableStateOf(null) }

    org.jetbrains.compose.splitpane.HorizontalSplitPane(
        modifier = modifier,
        splitPaneState = outerState,
    ) {
        first(minSize = 180.dp) {
            CategoriesPanel(
                viewModel = searchViewModel,
                categories = libraryViewModel.categories,
                headerTitle = libraryViewModel.headerTitle(),
                cycleIcon = when (libraryViewModel.viewMode) {
                    LibraryViewModel.ViewMode.CATEGORIES -> Icons.Filled.KeyboardArrowDown
                    LibraryViewModel.ViewMode.YEARS      -> Icons.Filled.KeyboardArrowUp
                    LibraryViewModel.ViewMode.REGIONS    -> Icons.AutoMirrored.Filled.KeyboardArrowRight
                },
                cycleTooltip = libraryViewModel.cycleTooltip(),
                onCycleView = libraryViewModel::cycleViewMode,
                onToggleCategory = libraryViewModel::toggleCategory,
                onSelectAll = libraryViewModel::selectAllCategoriesAction,
                onClearAll = libraryViewModel::clearAllCategoriesAction,
            )
        }
        second(minSize = 300.dp) {
            org.jetbrains.compose.splitpane.HorizontalSplitPane(
                splitPaneState = innerState,
            ) {
                first(minSize = 220.dp) {
                    BooksPanel(
                        books = libraryViewModel.visibleBooks(),
                        onToggleBook = libraryViewModel::toggleBook,
                        onSelectAll = libraryViewModel::selectAllBooksAction,
                        onClearAll = libraryViewModel::clearAllBooksAction,
                        onOpenBook = { openedBook = it },
                    )
                }
                second(minSize = 350.dp) {
                    DisplayPanel(
                        viewModel = searchViewModel,
                        onSaveFavorite = {
                            // ★: التِقاط الحالة الحاليّة وحَفظها كَجَلسة
                            val snap = searchViewModel.snapshot(libraryViewModel.viewMode)
                            com.bahthia.app.state.AppRuntime.savedSearchStore.add(snap)
                        },
                    )
                }
                splitter { bahthiaVerticalSplitter() }
            }
        }
        splitter { bahthiaVerticalSplitter() }
    }

    // بطاقة "الكتاب كاملاً" — تَفتح عند النّقر المزدوج على اسم الكتاب في عمود الكتب
    openedBook?.let { book ->
        BookReaderDialog(
            viewModel = searchViewModel,
            bookId = book.id,
            bookTitle = book.title,
            initialPageNumber = 1,
            highlightTerm = null,
            onClose = { openedBook = null },
        )
    }
}

/** يَفصل بين عناصر شريط الإحصاءات. */
@Composable
private fun StatDivider() {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .padding(horizontal = 8.dp)
            .size(width = 1.dp, height = 14.dp)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
    )
}

@Composable
private fun StatusBar(
    searchViewModel: SearchViewModel,
    libraryViewModel: LibraryViewModel,
) {
    androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxWidth()) {
        // طبقة ١: الخلفيّة الترابيّة (أساس)
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            // طبقة ٢: تَعبئة شفّافة تَنمو مع تقدّم البحث
            val progress = searchViewModel.searchProgress
            if (progress >= 0f && searchViewModel.isSearching) {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .height(28.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                )
            }

            // طبقة ٣: المحتوى — يَختار الواجهة المناسبة
            when {
                searchViewModel.isSearching -> SearchInProgressContent(searchViewModel)
                searchViewModel.lastError != null -> ErrorContent(searchViewModel.lastError!!)
                searchViewModel.results.isNotEmpty() -> SearchResultsContent(searchViewModel)
                libraryViewModel.loaded && libraryViewModel.totalBooks == 0 ->
                    SimpleContent(com.bahthia.i18n.tr("status.empty"))
                libraryViewModel.loaded -> RichLibraryStats(libraryViewModel)
                else -> SimpleContent(com.bahthia.i18n.tr("status.loading"))
            }
        }
    }
}

/** الواجهة الافتراضيّة — إحصاءات المكتبة الكاملة بشكل غنيّ. */
@Composable
private fun RichLibraryStats(vm: LibraryViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatItem(icon = Icons.AutoMirrored.Outlined.MenuBook, label = "كتاب", value = formatNumber(vm.totalBooks))
        StatDivider()
        StatItem(icon = Icons.Outlined.Article,    label = "صفحة", value = formatNumber(vm.totalPages))
        StatDivider()
        StatItem(icon = Icons.Outlined.Subject, label = "كلمة", value = formatLargeNumber(vm.totalWords))
        StatDivider()
        StatItem(
            icon = Icons.Outlined.Category,
            label = "حقل معرفيّ",
            value = formatNumber(vm.categories.count { it.name != "__ALL_BOOKS__" && it.name != "__UNCLASSIFIED__" }),
        )
        StatDivider()
        StatItem(icon = Icons.Outlined.Person, label = "مؤلّف", value = formatNumber(vm.totalAuthors))
    }
}

/** أثناء البحث — رسالة + تقدّم نصّيّ (الخلفيّة تَتعبّأ خلفه). */
@Composable
private fun SearchInProgressContent(vm: SearchViewModel) {
    val frac = vm.searchProgress.coerceIn(0f, 1f)
    val pct = (frac * 100).toInt()
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.size(6.dp))
        val text = if (vm.searchTotalPages > 0)
            "جارٍ البحث... ${formatNumber((frac * vm.searchTotalPages).toInt())} / ${formatNumber(vm.searchTotalPages)} صفحة — $pct%"
        else
            com.bahthia.i18n.tr("status.searching")
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** بعد البحث — عَرض عدد النتائج + الوقت. */
@Composable
private fun SearchResultsContent(vm: SearchViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatItem(icon = Icons.Outlined.Search,      label = "نتيجة",   value = formatNumber(vm.results.size))
        StatDivider()
        StatItem(icon = Icons.Outlined.Visibility, label = "معروضة",  value = formatNumber(vm.visibleResultCount()))
        StatDivider()
        StatItem(icon = Icons.Outlined.Schedule,   label = "مللي ث",  value = formatNumber(vm.lastDurationMs.toInt()))
    }
}

@Composable
private fun ErrorContent(error: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.size(6.dp))
        Text(
            text = error,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun SimpleContent(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
        )
    }
}

/** خَلية إحصاء واحدة: أيقونة Outlined + قيمة + تسمية. */
@Composable
private fun StatItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.size(5.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.size(3.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
        )
    }
}

/** تنسيق رقم بفاصلة ألفيّة — يَستعمل الأرقام العربيّة الغربيّة (0-9) لا الهنديّة. */
private fun formatNumber(n: Int): String =
    java.text.NumberFormat.getNumberInstance(java.util.Locale.ENGLISH).format(n)

/** تنسيق رقم كبير (الكلمات): 1.2 مليون / 543 ألف / 12,345 — أرقام غربيّة. */
private fun formatLargeNumber(n: Long): String = when {
    n >= 1_000_000 -> String.format(java.util.Locale.ENGLISH, "%.1f مليون", n / 1_000_000.0)
    n >= 1_000     -> String.format(java.util.Locale.ENGLISH, "%.1f ألف", n / 1_000.0)
    else            -> n.toString()
}

