package com.bahthia.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.bahthia.app.state.AppRuntime
import com.bahthia.app.state.LibraryViewModel
import com.bahthia.app.state.Screen
import com.bahthia.app.state.SearchViewModel
import com.bahthia.app.ui.screens.about.AboutScreen
import com.bahthia.app.ui.screens.developer.DeveloperModeDialog
import com.bahthia.app.ui.screens.import.ImportDialog
import com.bahthia.app.ui.screens.lifecycle.BackupExportDialog
import com.bahthia.app.ui.screens.lifecycle.BackupRestoreDialog
import com.bahthia.app.ui.screens.lifecycle.CheckUpdatesDialog
import com.bahthia.app.ui.screens.main.MainScreen
import com.bahthia.app.ui.screens.preferences.PreferencesDialog
import com.bahthia.app.ui.screens.splash.SplashScreen
import com.bahthia.app.ui.screens.statistics.StatisticsDialog
import com.bahthia.app.ui.screens.welcome.WelcomeScreen
import com.bahthia.app.ui.theme.BahthiaTheme
import com.bahthia.app.ui.theme.ThemeKind
import com.bahthia.app.ui.theme.rememberThemeState
import com.bahthia.domain.AppMetadata

fun main() = application {
    AppRuntime.installLifecycleServices() // crash reporter + shutdown flush — أوّلاً
    AppRuntime.ensureIndexInitialized()

    // العَرض: 1280 + 3 سم (≈ 113 نقطة على 96 DPI) = 1393 نقطة. الارتفاع كما هو.
    // الموقع: مُتَوسِّط على الشاشة دائماً عند كلّ إقلاع.
    val windowState: WindowState = rememberWindowState(
        size = DpSize(1393.dp, 800.dp),
        position = WindowPosition.Aligned(Alignment.Center),
    )

    // أيقونة النافذة (شريط المهامّ + شريط العنوان) — تُحمَّل من الموارد المضمَّنة.
    // في Compose Desktop نَستعمل useResource + BitmapPainter (لا painterResource الـ Android).
    val appIcon = androidx.compose.runtime.remember {
        try {
            androidx.compose.ui.graphics.painter.BitmapPainter(
                androidx.compose.ui.res.useResource("icons/bahthia.png") {
                    androidx.compose.ui.res.loadImageBitmap(it)
                }
            )
        } catch (_: Throwable) {
            null
        }
    }

    Window(
        onCloseRequest = ::exitApplication,
        title = "${AppMetadata.DISPLAY_NAME} ${AppMetadata.VERSION}",
        state = windowState,
        icon = appIcon,
        onKeyEvent = { event ->
            // اختصارات لوحة المفاتيح
            if (event.type == KeyEventType.KeyDown) {
                when {
                    // Ctrl+Shift+D — وضع المطوّر (مخفيّ)
                    event.isCtrlPressed && event.isShiftPressed && event.key == Key.D -> {
                        keyboardChannel.value = "DEV_MODE"; true
                    }
                    event.isCtrlPressed && event.key == Key.F      -> { keyboardChannel.value = "FOCUS_SEARCH"; true }
                    event.isCtrlPressed && event.key == Key.E      -> { keyboardChannel.value = "EXPORT"; true }
                    event.isCtrlPressed && event.key == Key.Enter  -> { keyboardChannel.value = "RUN_SEARCH"; true }
                    event.key == Key.Escape    -> { keyboardChannel.value = "ESC"; true }
                    event.key == Key.DirectionDown -> { keyboardChannel.value = "DOWN"; true }
                    event.key == Key.DirectionUp   -> { keyboardChannel.value = "UP"; true }
                    else -> false
                }
            } else false
        },
    ) {
        AppRoot()
    }
}

/** قناة تواصل بسيطة لاختصارات لوحة المفاتيح. */
private val keyboardChannel = androidx.compose.runtime.mutableStateOf<String?>(null)

@Composable
private fun AppRoot() {
    val themeState = rememberThemeState(initial = ThemeKind.EARTHY)
    val scope = rememberCoroutineScope()

    val searchVM = remember { SearchViewModel(AppRuntime.openSearcher()) }
    val libraryVM = remember { LibraryViewModel(scope) }

    // ابدأ تَحميل المكتبة فَوراً (قَبل عَرض الشاشة الرئيسيّة)
    // هذا يَسمح لـ SplashScreen بِعَرض شَريط تَقدُّم حَقيقيّ.
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (!libraryVM.loaded) libraryVM.load()
    }

    DisposableEffect(Unit) {
        onDispose {
            searchVM.close()
            // ViewModel الاستيراد يَعيش طول حياة التطبيق (يَدعم الاستيراد في الخلفيّة)
            // — نُغلقه مع إغلاق التطبيق لإيقاف أيّ مهمّة جارية ومسح الـ scope.
            runCatching { AppRuntime.importVM.close() }
        }
    }

    // نَبدأ من SPLASH ونَنتقل تَلقائيّاً إلى MAIN عند اكتِمال تَحميل المكتبة
    var currentScreen by remember { mutableStateOf(Screen.SPLASH) }

    // الانتقال السَلِس من SPLASH إلى MAIN — حدّ أَدنى ٦٠٠ ميلي للظُهور البَصريّ،
    // ثمّ يَنتظر اكتِمال التَّحميل (loaded == true)
    androidx.compose.runtime.LaunchedEffect(libraryVM.loaded) {
        if (currentScreen == Screen.SPLASH && libraryVM.loaded) {
            // فُرصة قَصيرة ليَصل شَريط التَّقدُّم بَصريّاً إلى ١٠٠٪
            kotlinx.coroutines.delay(450)
            currentScreen = Screen.MAIN
        }
    }
    var showStatistics by remember { mutableStateOf(false) }
    var showPreferences by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var showFavorites by remember { mutableStateOf(false) }

    // مُراقبة حالة الاستيراد: عند انتهائه (FINISHED) نُطبّق aggregate المحسوب مسبقاً
    // فَوراً على المَكتبة، حتّى لو كان المُستخدِم قد أَخفى الحوار أَثناء FINALIZING.
    // هذا يَضمن أنّ الواجِهة الرّئيسيّة جاهِزة بِالكامل بِلا انتظار إِضافيّ.
    val importState = AppRuntime.importVM.state
    androidx.compose.runtime.LaunchedEffect(importState) {
        if (importState == com.bahthia.app.state.ImportViewModel.State.FINISHED) {
            val pre = AppRuntime.importVM.precomputedAggregate
            if (pre != null && libraryVM.totalBooks != pre.totalBooks) {
                searchVM.reopenSearcher(AppRuntime.openSearcher())
                libraryVM.applyPrecomputedAggregate(pre)
            }
        }
    }
    // Phase 4 — حوارات دورة الحياة
    var showCheckUpdates by remember { mutableStateOf(false) }
    var showBackupExport by remember { mutableStateOf(false) }
    var showBackupRestore by remember { mutableStateOf(false) }
    // Phase 6 — وضع المطوّر
    var showDeveloperMode by remember { mutableStateOf(false) }

    // تنفيذ اختصارات لوحة المفاتيح
    val key = keyboardChannel.value
    if (key != null) {
        when (key) {
            "ESC"  -> {
                if (showStatistics) showStatistics = false
                else if (showPreferences) showPreferences = false
                else if (showDeveloperMode) showDeveloperMode = false
                else if (showCheckUpdates) showCheckUpdates = false
                else if (showBackupExport) showBackupExport = false
                else if (showBackupRestore) showBackupRestore = false
                else if (showImport) showImport = false
                else if (showClearConfirm) showClearConfirm = false
                else if (showFavorites) showFavorites = false
                else if (currentScreen == Screen.ABOUT) currentScreen = Screen.MAIN
                else searchVM.reset()
            }
            "DOWN"         -> searchVM.nextResult()
            "UP"           -> searchVM.previousResult()
            "DEV_MODE"     -> showDeveloperMode = true
            "FOCUS_SEARCH" -> searchVM.requestFocusSearch()
            "EXPORT"       -> if (searchVM.results.isNotEmpty()) searchVM.requestExport()
            "RUN_SEARCH"   -> searchVM.runSearch()
        }
        keyboardChannel.value = null
    }

    BahthiaTheme(themeState = themeState) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            androidx.compose.animation.Crossfade(
                targetState = currentScreen,
                animationSpec = androidx.compose.animation.core.tween(durationMillis = 380),
                label = "screen-crossfade",
            ) { screen -> when (screen) {
                Screen.SPLASH -> SplashScreen(progress = libraryVM.loadProgress.coerceAtLeast(0f))
                Screen.WELCOME -> WelcomeScreen(
                    onStartTour = { currentScreen = Screen.MAIN },
                    onSkip      = { currentScreen = Screen.MAIN },
                )
                Screen.MAIN -> MainScreen(
                    searchViewModel = searchVM,
                    libraryViewModel = libraryVM,
                    onShowAbout = { currentScreen = Screen.ABOUT },
                    onShowStats = { showStatistics = true },
                    onShowPreferences = { showPreferences = true },
                    onShowImport = { showImport = true },
                    onShowClear = { showClearConfirm = true },
                    onShowFavorites = { showFavorites = true },
                    onCheckUpdates = { showCheckUpdates = true },
                    onBackupExport = { showBackupExport = true },
                    onBackupRestore = { showBackupRestore = true },
                )
                Screen.ABOUT -> AboutScreen(
                    onClose = { currentScreen = Screen.MAIN },
                )
            } }

            if (showStatistics) {
                StatisticsDialog(
                    libraryViewModel = libraryVM,
                    onClose = { showStatistics = false },
                )
            }

            if (showFavorites) {
                com.bahthia.app.ui.screens.favorites.FavoritesDialog(
                    onClose = { showFavorites = false },
                    onApply = { saved ->
                        // ١) الانتقال للشاشة الرئيسيّة إن لم نَكن فيها
                        currentScreen = Screen.MAIN
                        // ٢) ضَبط viewMode في عَمود الفئات/السنوات/الدُّول
                        libraryVM.changeViewMode(saved.viewMode)
                        // ٣) ضَبط معيار الزَّمن (يَجب أن يَتطابق مَع ما حُفِظ)
                        AppRuntime.preferences.timeMode = saved.timeMode
                        searchVM.timeMode = saved.timeMode
                        libraryVM.invalidate(); libraryVM.load()
                        // ٤) استرجاع شَريط البَحث + النتائج فَوراً
                        searchVM.restoreFromSnapshot(saved)
                        // ٥) تَطبيق التَحديدات على الفئات والكتب (للتَوضيح البَصريّ)
                        val catNames = when (saved.viewMode) {
                            com.bahthia.app.state.LibraryViewModel.ViewMode.CATEGORIES -> saved.selectedCategories
                            com.bahthia.app.state.LibraryViewModel.ViewMode.YEARS      -> saved.selectedYears
                            com.bahthia.app.state.LibraryViewModel.ViewMode.REGIONS    -> saved.selectedCountries
                        }
                        libraryVM.applySelections(catNames, saved.selectedBookIds)
                    },
                )
            }

            if (showPreferences) {
                PreferencesDialog(
                    themeState = themeState,
                    onClose = { showPreferences = false },
                )
            }

            if (showImport) {
                ImportDialog(
                    onClose = { showImport = false },
                    onImported = {
                        // أُعيد فَتح الـsearcher (الفهرس تَغيَّر)
                        searchVM.reopenSearcher(AppRuntime.openSearcher())
                        libraryVM.invalidate()
                        // إن كان aggregate المُحسوب مُسبَقاً جاهزاً (الحالة الافتراضيّة بَعد
                        // استيراد كامِل) — نُمرِّره فَوراً لِيَكون كلّ شَيء جاهِزاً عند فَتح
                        // الواجِهة الرّئيسيّة. خِلاف ذلك (إِلغاء/استئناف…) — تَحميل عاديّ.
                        val pre = AppRuntime.importVM.precomputedAggregate
                        if (pre != null) {
                            libraryVM.applyPrecomputedAggregate(pre)
                        } else {
                            libraryVM.load()
                        }
                    },
                )
            }

            if (showClearConfirm) {
                ConfirmClearDialog(
                    onConfirm = {
                        searchVM.reset()
                        // إغلاق الباحث الحالي حتى نُمسح الفهرس
                        searchVM.closeSearcherForIndexMutation()
                        AppRuntime.clearLibrary()
                        searchVM.reopenSearcher(AppRuntime.openSearcher())
                        libraryVM.invalidate()
                        libraryVM.load()
                        showClearConfirm = false
                    },
                    onCancel = { showClearConfirm = false },
                )
            }

            // ─── Phase 4 — حوارات دورة الحياة ───
            if (showCheckUpdates) {
                CheckUpdatesDialog(onClose = { showCheckUpdates = false })
            }
            if (showBackupExport) {
                BackupExportDialog(onClose = { showBackupExport = false })
            }
            if (showBackupRestore) {
                BackupRestoreDialog(
                    onClose = { showBackupRestore = false },
                    onBeforeRestore = {
                        searchVM.closeSearcherForIndexMutation()
                    },
                    onRestoreFailed = {
                        runCatching { searchVM.reopenSearcher(AppRuntime.openSearcher()) }
                    },
                    onRestored = {
                        searchVM.reopenSearcher(AppRuntime.openSearcher())
                        libraryVM.invalidate()
                        libraryVM.load()
                    },
                )
            }

            // ─── Phase 6 — وضع المطوّر (Ctrl+Shift+D) ───
            if (showDeveloperMode) {
                DeveloperModeDialog(onClose = { showDeveloperMode = false })
            }
        }
    }
}

@Composable
private fun ConfirmClearDialog(onConfirm: () -> Unit, onCancel: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onCancel,
        title = { androidx.compose.material3.Text("⚠️ مسح المكتبة بالكامل؟") },
        text = { androidx.compose.material3.Text(
            "ستُحذف **كل** الكتب والصفحات من المكتبة الجديدة (لا يؤثّر على نسخة Python).\n" +
                    "هذه العمليّة لا يمكن التراجع عنها.\n\n" +
                    "بعد المسح: يمكنك استيراد الكتب من جديد مباشرة.",
        ) },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onConfirm) {
                androidx.compose.material3.Text(
                    "نعم، امسح",
                    color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onCancel) {
                androidx.compose.material3.Text("إلغاء")
            }
        },
    )
}
