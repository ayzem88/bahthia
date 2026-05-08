package com.bahthia.app.ui.screens.categories

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.bahthia.app.state.SearchViewModel
import com.bahthia.app.ui.components.BahthiaTooltip
import com.bahthia.app.ui.components.LazyColumnWithScrollbar
import com.bahthia.app.ui.components.PanelHeader
import com.bahthia.app.ui.components.SelectAllClearAllRow
import com.bahthia.app.ui.theme.LocalBahthiaColors

/**
 * لوحة "الحقل المعرفيّ" — الترويسة الترابية + بحث + قائمة + تحديد/إلغاء.
 * مطابقة لـ Python `CategoriesPanel.create`.
 */
@Composable
fun CategoriesPanel(
    viewModel: SearchViewModel,
    categories: List<CategoryEntry>,
    headerTitle: String = com.bahthia.i18n.tr("panel.categories.title"),
    cycleIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    cycleTooltip: String = "",
    onCycleView: () -> Unit = {},
    onToggleCategory: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClearAll: () -> Unit,
) {
    var filter by remember { mutableStateOf("") }
    val filtered = remember(categories, filter) {
        if (filter.isBlank()) categories
        else categories.filter { it.name.contains(filter.trim(), ignoreCase = false) }
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp)) {
        PanelHeader(
            title = headerTitle,
            trailingIcon = if (cycleIcon != null) {
                {
                    BahthiaTooltip(text = com.bahthia.i18n.tr("panel.categories.cycle.tooltip")) {
                        androidx.compose.material3.Icon(
                            imageVector = cycleIcon,
                            contentDescription = cycleTooltip,
                            tint = Color.White,
                            modifier = Modifier
                                .clickable(onClick = onCycleView)
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                .size(20.dp),
                        )
                    }
                }
            } else null,
        )

        Spacer(Modifier.height(5.dp))

        com.bahthia.app.ui.components.BahthiaSearchField(
            value = filter,
            onValueChange = { filter = it },
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(5.dp))

        // قائمة الفئات
        Surface(
            shape = RoundedCornerShape(5.dp),
            color = Color.White,
            border = BorderStroke(2.dp, Color(0xFFDDDDDD)),
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            LazyColumnWithScrollbar {
                items(filtered.size) { i ->
                    CategoryRow(filtered[i], onToggle = { onToggleCategory(filtered[i].name) })
                }
            }
        }

        Spacer(Modifier.height(5.dp))

        SelectAllClearAllRow(
            onSelectAll = onSelectAll,
            onClearAll = onClearAll,
            selectAllTooltip = com.bahthia.i18n.tr("panel.selectAll.categories.tooltip"),
            clearAllTooltip  = com.bahthia.i18n.tr("panel.clearAll.categories.tooltip"),
        )
    }
}

private fun LazyListScope.items(count: Int, content: @Composable (Int) -> Unit) {
    items(count = count) { content(it) }
}

private typealias LazyListScope = androidx.compose.foundation.lazy.LazyListScope

/** يُحوّل الـ sentinels الداخليّة إلى نصّ المستخدم بالـ tr(). */
fun displayCategoryName(raw: String): String = when (raw) {
    "__ALL_BOOKS__"     -> com.bahthia.i18n.tr("panel.categories.allBooks")
    "__UNCLASSIFIED__"  -> com.bahthia.i18n.tr("panel.categories.unclassified")
    else                -> raw
}

@Composable
private fun CategoryRow(entry: CategoryEntry, onToggle: () -> Unit) {
    val colors = LocalBahthiaColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = entry.selected, onCheckedChange = { onToggle() })
        Text(
            text = "${displayCategoryName(entry.name)} [${entry.count}]",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
    HorizontalDivider(color = Color(0xFFF5F0EB), thickness = 1.dp)
}

/** عنصر فئة في القائمة. */
data class CategoryEntry(
    val name: String,
    val count: Int,
    val selected: Boolean,
)
