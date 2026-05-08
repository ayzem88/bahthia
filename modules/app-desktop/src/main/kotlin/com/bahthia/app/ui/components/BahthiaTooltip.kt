package com.bahthia.app.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * تلميح ترابيّ خفيف يَظهر عند تمرير الفأرة فوق العنصر.
 *
 * ‍يَستعمل [TooltipBox] من Material 3 الذي يَختار تلقائياً موضعاً لا يُغطّي
 * الواجهة (فوق أو تحت العنصر حسب المساحة المتوفّرة).
 *
 * المظهر:
 *   - خلفيّة ترابيّة فاتحة (primaryContainer)
 *   - حواف مدوَّرة 8dp
 *   - عرض أقصى 280dp فيُلتفّ النصّ الطويل
 *   - تأخير ٥٠٠ مللي ث قبل الظهور (افتراضي Material)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BahthiaTooltip(
    text: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (text.isBlank()) {
        content()
        return
    }
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = {
            PlainTooltip(
                shape = RoundedCornerShape(8.dp),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor   = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.widthIn(max = 280.dp),
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }
        },
        state = rememberTooltipState(),
        modifier = modifier,
        content = content,
    )
}
