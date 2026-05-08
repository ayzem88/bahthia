package com.bahthia.app.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

/**
 * حقل بحث مدمج بارتفاع 40dp بالضبط — يُستعمل في الشريط الرئيسيّ وفلاتر الأعمدة.
 *
 * **سبب وجوده**: `OutlinedTextField` الافتراضي في Material 3 يَستعمل padding داخلي
 * 16×16 يَلتهم النصّ في 40dp. هنا نَستعمل `BasicTextField` مع `DecorationBox`
 * + padding مُخصَّص 12×6 ليَتَّسع للنصّ.
 *
 * المظهر: عَدسة 18dp شفّافة 40٪ — placeholder "…" — خلفيّة بيضاء — نصّ bodyMedium.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BahthiaSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    onSearchAction: () -> Unit = {},
    /** الأَيقونة على اليَسار — افتراضيّاً عَدسة بَحث. تُمرَّر null لِإِخفائها. */
    leadingIcon: ImageVector? = Icons.Outlined.Search,
    /** نَصّ بَديل عند فَراغ الحَقل. */
    placeholder: String = "…",
    /** نَوع الـIME action على لَوحة المَفاتيح. */
    imeAction: ImeAction = ImeAction.Search,
    /** نَوع لَوحة المَفاتيح (نَصّ، رَقم، …). */
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val textStyle = MaterialTheme.typography.bodyMedium.copy(
        color = MaterialTheme.colorScheme.onSurface,
    )
    val colors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor   = Color.White,
        unfocusedContainerColor = Color.White,
        disabledContainerColor  = Color.White,
    )

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.height(40.dp),
        textStyle = textStyle,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = imeAction, keyboardType = keyboardType),
        // نَلتقط كلّ أَنواع الإِجراءات لِنَفس الـcallback (Search/Go/Done/Send)
        keyboardActions = KeyboardActions(
            onSearch = { onSearchAction() },
            onGo = { onSearchAction() },
            onDone = { onSearchAction() },
            onSend = { onSearchAction() },
        ),
        interactionSource = interactionSource,
        decorationBox = { innerTextField ->
            OutlinedTextFieldDefaults.DecorationBox(
                value = value,
                innerTextField = innerTextField,
                enabled = true,
                singleLine = true,
                visualTransformation = VisualTransformation.None,
                interactionSource = interactionSource,
                placeholder = {
                    Text(placeholder, style = MaterialTheme.typography.bodyMedium)
                },
                leadingIcon = leadingIcon?.let { icon ->
                    {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                },
                colors = colors,
                // الهامش المُخصَّص — يَترك مساحة كافية للنصّ في 40dp
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                container = {
                    OutlinedTextFieldDefaults.Container(
                        enabled = true,
                        isError = false,
                        interactionSource = interactionSource,
                        colors = colors,
                    )
                },
            )
        },
    )
}
