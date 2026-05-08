package com.bahthia.app.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

// تَدرُّجات اللَّون التُرابي للزرّ:
// - الافتراضيّ: ترابي أَساسيّ من السمة (`primary`)
// - عند الضَغط:  ترابيّ فاتح (يُغيّر اللَّون فَقط، لا حَركة أُخرى)
// - عند التَعطيل: ترابيّ بَاهِت — يَبقى مُتَّسقاً مع باقي الواجِهة بدلاً من الرَماديّ
private val PRESSED_BROWN  = Color(0xFFD4C4B0)
private val DISABLED_BROWN = Color(0xFFE5D5C2)
private val DISABLED_TEXT  = Color(0xFF8A6E55)

/** زرّ ترابي بنفس تنسيق Python QPushButton. */
@Composable
fun BrownButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    fontSize: androidx.compose.ui.unit.TextUnit = MaterialTheme.typography.bodyMedium.fontSize,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val container = if (isPressed) PRESSED_BROWN else MaterialTheme.colorScheme.primary
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        interactionSource = interactionSource,
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = Color.White,
            disabledContainerColor = DISABLED_BROWN,
            disabledContentColor = DISABLED_TEXT,
        ),
        shape = RoundedCornerShape(5.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(text = text, fontWeight = FontWeight.Bold)
    }
}

/** ترويسة ملوّنة بترابي للوحات (الحقل المعرفي، الكتب). */
@Composable
fun PanelHeader(
    title: String,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    Surface(
        shape = RoundedCornerShape(5.dp),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            if (trailingIcon != null) {
                trailingIcon()
            }
        }
    }
}

/** صفّ زرَّي تحديد الكلّ / إلغاء الكلّ — الترتيب نفسه في كل اللوحات. */
@Composable
fun SelectAllClearAllRow(
    onSelectAll: () -> Unit,
    onClearAll: () -> Unit,
    @Suppress("UNUSED_PARAMETER") selectAllTooltip: String = "",
    @Suppress("UNUSED_PARAMETER") clearAllTooltip: String = "",
) {
    // ملاحظة: لا نَلفّ الأزرار بـ BahthiaTooltip هنا لأنّه يُفسد توزيع `weight(1f)`
    // في الـ Row (TooltipBox يُغلّف المحتوى في Box يَكسر تدفّق القياس).
    // النصّ "تحديد الكلّ"/"إلغاء الكلّ" واضح ذاتياً ولا يَحتاج تلميحاً.
    Row(modifier = Modifier.fillMaxWidth()) {
        BrownButton(
            text = com.bahthia.i18n.tr("panel.selectAll"),
            onClick = onSelectAll,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(5.dp))
        BrownButton(
            text = com.bahthia.i18n.tr("panel.clearAll"),
            onClick = onClearAll,
            modifier = Modifier.weight(1f),
        )
    }
}
