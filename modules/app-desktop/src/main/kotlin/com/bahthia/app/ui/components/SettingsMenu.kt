package com.bahthia.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Tour
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VolunteerActivism
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bahthia.app.state.SearchViewModel
import com.bahthia.app.ui.theme.LocalBahthiaColors
import com.bahthia.domain.AppMetadata
import com.bahthia.i18n.tr
import java.awt.Desktop
import java.net.URI

/**
 * قائمة الإعدادات الكاملة — مطابقة لـ Python `SearchPanel.show_settings_menu`.
 * كلّ الأيقونات Outlined لمظهر أخفّ وأكثر أناقة.
 */
@Composable
fun SettingsMenu(
    onShowAbout: () -> Unit = {},
    onShowStats: () -> Unit = {},
    onShowPreferences: () -> Unit = {},
    onShowImport: () -> Unit = {},
    onShowDelete: () -> Unit = {},
    onShowFavorites: () -> Unit = {},
    onShowWelcome: () -> Unit = {},
    onCheckUpdates: () -> Unit = {},
    onBackupExport: () -> Unit = {},
    onBackupRestore: () -> Unit = {},
    onTimeModeChange: () -> Unit = {},
    searchViewModel: SearchViewModel,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var subMenu by remember { mutableStateOf<String?>(null) }

    val colors = LocalBahthiaColors.current

    Box {
        BahthiaTooltip(text = tr("settings.tooltip")) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                shadowElevation = 1.dp,
            ) {
                IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = tr("settings.title"),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false; subMenu = null },
            shape = RoundedCornerShape(10.dp),
            shadowElevation = 4.dp,
        ) {
            SectionTitle(tr("settings.title"))
            HorizontalDivider(color = colors.divider, modifier = Modifier.padding(horizontal = 8.dp))

            // إدارة المكتبة
            MenuItem(Icons.Outlined.CloudUpload,    tr("settings.import"))   { menuOpen = false; onShowImport() }
            MenuItem(Icons.Outlined.DeleteOutline,  tr("settings.delete"))   { menuOpen = false; onShowDelete() }

            HorizontalDivider(color = colors.divider, modifier = Modifier.padding(horizontal = 8.dp))

            // تخصيص البحث
            MenuItem(
                Icons.Outlined.FormatListNumbered,
                "${tr("settings.limit")}: ${labelFor(searchViewModel.resultsLimit)}",
                showArrow = true,
            ) { subMenu = "limit" }
            MenuItem(Icons.Outlined.Language, tr("settings.language"), showArrow = true) {
                subMenu = "language"
            }
            MenuItem(Icons.Outlined.Schedule, tr("settings.timeMode"), showArrow = true) {
                subMenu = "time"
            }

            HorizontalDivider(color = colors.divider, modifier = Modifier.padding(horizontal = 8.dp))

            // الأدوات
            MenuItem(Icons.Outlined.BarChart,    tr("settings.stats"))     { menuOpen = false; onShowStats() }
            MenuItem(Icons.Outlined.StarBorder,  tr("settings.favorites")) { menuOpen = false; onShowFavorites() }

            HorizontalDivider(color = colors.divider, modifier = Modifier.padding(horizontal = 8.dp))

            // العرض
            // ملاحظة: اختيار السمة مَوجود داخل حِوار "التفضيلات" (انظر [PreferencesDialog]).
            MenuItem(Icons.Outlined.Tune, tr("settings.preferences")) {
                menuOpen = false; onShowPreferences()
            }
            MenuItem(Icons.Outlined.Tour, tr("settings.tour")) { menuOpen = false; onShowWelcome() }

            HorizontalDivider(color = colors.divider, modifier = Modifier.padding(horizontal = 8.dp))

            // النسخ الاحتياطي والتحديث
            MenuItem(Icons.Outlined.FileDownload, tr("settings.backup"), showArrow = true) {
                subMenu = "backup"
            }
            MenuItem(Icons.Outlined.SystemUpdate, tr("settings.checkUpdates")) {
                menuOpen = false; onCheckUpdates()
            }

            HorizontalDivider(color = colors.divider, modifier = Modifier.padding(horizontal = 8.dp))

            // الدعم والتواصل
            MenuItem(Icons.Outlined.BugReport, tr("settings.reportBug")) {
                menuOpen = false
                openLink("mailto:${AppMetadata.EMAIL}?subject=Bug%20Report%20-%20Bahthia%20${AppMetadata.VERSION}")
            }
            MenuItem(Icons.Outlined.Lightbulb, tr("settings.suggestFeature")) {
                menuOpen = false
                openLink("mailto:${AppMetadata.EMAIL}?subject=Feature%20Suggestion%20-%20Bahthia")
            }
            MenuItem(Icons.AutoMirrored.Outlined.MenuBook, tr("settings.suggestBook")) {
                menuOpen = false
                openLink("mailto:${AppMetadata.EMAIL}?subject=Book%20Suggestion%20-%20Bahthia")
            }
            MenuItem(Icons.Outlined.Info,  tr("settings.about"))   { menuOpen = false; onShowAbout() }
            MenuItem(Icons.Outlined.Email, tr("settings.contact")) { menuOpen = false; openLink("mailto:${AppMetadata.EMAIL}") }
            MenuItem(Icons.Outlined.VolunteerActivism, tr("settings.donate"), showArrow = true) {
                subMenu = "donate"
            }
            MenuItem(Icons.Outlined.Public, tr("settings.visitSite")) {
                menuOpen = false; openLink(AppMetadata.WEBSITE)
            }
        }

        // القوائم الفرعيّة
        DropdownMenu(
            expanded = subMenu != null,
            onDismissRequest = { subMenu = null; menuOpen = false },
            shape = RoundedCornerShape(10.dp),
            shadowElevation = 4.dp,
        ) {
            when (subMenu) {
                "limit"    -> LimitSubmenu(searchViewModel) { subMenu = null; menuOpen = false }
                "language" -> LanguageSubmenu             { subMenu = null; menuOpen = false }
                "time"     -> TimeCriteriaSubmenu(
                    searchViewModel = searchViewModel,
                    onTimeModeChange = onTimeModeChange,
                ) { subMenu = null; menuOpen = false }
                "backup"   -> BackupSubmenu(
                    onExport = { subMenu = null; menuOpen = false; onBackupExport() },
                    onRestore = { subMenu = null; menuOpen = false; onBackupRestore() },
                )
                "donate"   -> DonateSubmenu               { subMenu = null; menuOpen = false }
                else -> Unit
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

@Composable
private fun MenuItem(
    icon: ImageVector,
    text: String,
    showArrow: Boolean = false,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(text, style = MaterialTheme.typography.bodyMedium) },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                modifier = Modifier.size(20.dp),
            )
        },
        trailingIcon = if (showArrow) {
            {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.size(18.dp),
                )
            }
        } else null,
        onClick = onClick,
        contentPadding = MenuDefaults.DropdownMenuItemContentPadding,
    )
}

@Composable
private fun LimitSubmenu(viewModel: SearchViewModel, onDismiss: () -> Unit) {
    SectionTitle(tr("limit.title"))
    listOf(0 to tr("settings.limit.unlimited"), 500 to "500", 1000 to "1000", 10000 to "10000").forEach { (n, label) ->
        val target = if (n == 0) Int.MAX_VALUE else n
        SubmenuOption(
            label = label,
            selected = viewModel.resultsLimit == target,
            onClick = {
                viewModel.resultsLimit = target
                onDismiss()
            },
        )
    }
}

@Composable
private fun LanguageSubmenu(onDismiss: () -> Unit) {
    SectionTitle(tr("settings.language"))
    val current = com.bahthia.i18n.LocaleStore.current
    com.bahthia.i18n.Locale.entries.forEach { lang ->
        SubmenuOption(
            label = lang.displayName,
            selected = lang == current,
            enabled = true,
            onClick = {
                com.bahthia.i18n.LocaleStore.set(lang)
                onDismiss()
            },
        )
    }
}

@Composable
private fun TimeCriteriaSubmenu(
    searchViewModel: SearchViewModel,
    onTimeModeChange: () -> Unit,
    onDismiss: () -> Unit,
) {
    val prefs = com.bahthia.app.state.AppRuntime.preferences
    var current by remember { mutableStateOf(prefs.timeMode) }

    fun choose(newMode: com.bahthia.domain.TimeMode) {
        if (newMode != current) {
            prefs.timeMode = newMode
            current = newMode
            searchViewModel.timeMode = newMode
            onTimeModeChange()
        }
        onDismiss()
    }

    SectionTitle(tr("timeMode.title"))
    SubmenuOption(
        label = tr("timeMode.deathYear"),
        selected = current == com.bahthia.domain.TimeMode.DEATH_YEAR,
        onClick = { choose(com.bahthia.domain.TimeMode.DEATH_YEAR) },
    )
    SubmenuOption(
        label = tr("timeMode.usageDate"),
        selected = current == com.bahthia.domain.TimeMode.USAGE_DATE,
        onClick = { choose(com.bahthia.domain.TimeMode.USAGE_DATE) },
    )
}

@Composable
private fun BackupSubmenu(onExport: () -> Unit, onRestore: () -> Unit) {
    SectionTitle(tr("settings.backup"))
    DropdownMenuItem(
        text = { Text(tr("backup.export"), style = MaterialTheme.typography.bodyMedium) },
        leadingIcon = {
            Icon(
                Icons.Outlined.FileDownload,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                modifier = Modifier.size(20.dp),
            )
        },
        onClick = onExport,
    )
    DropdownMenuItem(
        text = { Text(tr("backup.restore"), style = MaterialTheme.typography.bodyMedium) },
        leadingIcon = {
            Icon(
                Icons.Outlined.FileUpload,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                modifier = Modifier.size(20.dp),
            )
        },
        onClick = onRestore,
    )
}

@Composable
private fun DonateSubmenu(onDismiss: () -> Unit) {
    SectionTitle(tr("settings.donate"))
    DropdownMenuItem(
        text = { Text("PayPal", style = MaterialTheme.typography.bodyMedium) },
        leadingIcon = {
            Icon(
                Icons.Outlined.Public,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                modifier = Modifier.size(20.dp),
            )
        },
        onClick = { openLink("https://paypal.me/aymen.nji"); onDismiss() },
    )
    DropdownMenuItem(
        text = { Text("Patreon", style = MaterialTheme.typography.bodyMedium) },
        leadingIcon = {
            Icon(
                Icons.Outlined.FavoriteBorder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                modifier = Modifier.size(20.dp),
            )
        },
        onClick = { openLink("https://patreon.com/bahthia"); onDismiss() },
    )
    DropdownMenuItem(
        text = { Text(tr("donate.bank"), style = MaterialTheme.typography.bodyMedium) },
        leadingIcon = {
            Icon(
                Icons.Outlined.AccountBalance,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                modifier = Modifier.size(20.dp),
            )
        },
        onClick = { openLink("mailto:${AppMetadata.EMAIL}?subject=تبرّع%20-%20تحويل%20بنكي"); onDismiss() },
    )
}

/** خيار قائمة فرعيّة بسيط مع علامة ✓ عند الاختيار. */
@Composable
private fun SubmenuOption(
    label: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = when {
                    !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    selected -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurface
                },
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
        },
        leadingIcon = if (selected) {
            {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
        } else null,
        onClick = onClick,
        enabled = enabled,
    )
}

private fun labelFor(limit: Int): String = when (limit) {
    Int.MAX_VALUE -> "دون تحديد"
    else -> limit.toString()
}

private fun openLink(uri: String) {
    try {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(URI(uri))
        } else if (uri.startsWith("mailto:") && Desktop.isDesktopSupported()
                   && Desktop.getDesktop().isSupported(Desktop.Action.MAIL)) {
            Desktop.getDesktop().mail(URI(uri))
        }
    } catch (_: Exception) { /* ignore */ }
}
