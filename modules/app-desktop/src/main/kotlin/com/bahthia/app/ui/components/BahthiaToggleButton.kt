package com.bahthia.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** موضع الزرّ في المجموعة — يُحدّد الحواف المدوّرة. */
enum class TogglePosition { FIRST, MIDDLE, LAST, ONLY }

/**
 * زرّ تبديل ترابيّ بحجم نصّه الطبيعيّ (يَلتفّ حول المحتوى لا حوله الحجم).
 *
 * يُستعمل داخل [BahthiaToggleGroup] لتكوين مجموعة متّصلة بصرياً.
 *
 * @param selected     هل هو مُفعَّل الآن؟
 * @param onClick      إجراء النقر
 * @param position     موضعه في المجموعة (يُحدِّد التدوير)
 * @param contentPadding هامش داخلي حول المحتوى
 * @param content      المحتوى (عادةً Text داخل [BahthiaTooltip])
 */
@Composable
fun BahthiaToggleButton(
    selected: Boolean,
    onClick: () -> Unit,
    position: TogglePosition,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    content: @Composable () -> Unit,
) {
    val cornerRadius = 8.dp
    val shape = when (position) {
        TogglePosition.FIRST  -> RoundedCornerShape(
            topStart = cornerRadius, bottomStart = cornerRadius,
            topEnd   = 0.dp,         bottomEnd   = 0.dp,
        )
        TogglePosition.MIDDLE -> RoundedCornerShape(0.dp)
        TogglePosition.LAST   -> RoundedCornerShape(
            topStart = 0.dp,         bottomStart = 0.dp,
            topEnd   = cornerRadius, bottomEnd   = cornerRadius,
        )
        TogglePosition.ONLY   -> RoundedCornerShape(cornerRadius)
    }

    val bg = if (selected)
        MaterialTheme.colorScheme.primaryContainer
    else
        Color.Transparent

    val borderColor = if (selected)
        MaterialTheme.colorScheme.primary
    else
        MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)

    val contentColor = if (selected)
        MaterialTheme.colorScheme.onPrimaryContainer
    else
        MaterialTheme.colorScheme.onSurface

    Surface(
        shape = shape,
        color = bg,
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier.padding(contentPadding),
            contentAlignment = Alignment.Center,
        ) {
            CompositionLocalProvider(LocalContentColor provides contentColor) {
                content()
            }
        }
    }
}

/**
 * مجموعة [BahthiaToggleButton] متَّصلة بصرياً (بدون فجوات بين الأزرار).
 *
 * مثال:
 * ```
 * BahthiaToggleGroup {
 *     BahthiaToggleButton(selected = a, onClick = ..., position = TogglePosition.FIRST)  { Text("...") }
 *     BahthiaToggleButton(selected = b, onClick = ..., position = TogglePosition.MIDDLE) { Text("...") }
 *     BahthiaToggleButton(selected = c, onClick = ..., position = TogglePosition.LAST)   { Text("...") }
 * }
 * ```
 */
@Composable
fun BahthiaToggleGroup(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    // لا فجوة بين العناصر — تَتلامس الحدود لتَبدو خطّاً متَّصلاً.
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(0.dp),
        content = content,
    )
}

/**
 * مساعد بسيط: يَحسب موضع كلّ زرّ في مجموعة من `count` عناصر بناءً على فهرسه.
 */
fun togglePositionOf(index: Int, count: Int): TogglePosition = when {
    count == 1            -> TogglePosition.ONLY
    index == 0            -> TogglePosition.FIRST
    index == count - 1    -> TogglePosition.LAST
    else                  -> TogglePosition.MIDDLE
}
