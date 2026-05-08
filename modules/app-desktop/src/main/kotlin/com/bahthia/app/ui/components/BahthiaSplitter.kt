package com.bahthia.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import org.jetbrains.compose.splitpane.ExperimentalSplitPaneApi
import org.jetbrains.compose.splitpane.SplitPaneState
import org.jetbrains.compose.splitpane.SplitterScope
import java.awt.Cursor

/**
 * مَقابض السحب الترابيّة الموحَّدة.
 *
 * **العَموديّة** (للأعمدة الأفقيّة): مقبض ٦dp مع مؤشّر ↔
 * **الأفقيّة** (للأقسام الرأسيّة): مقبض ٦dp مع مؤشّر ↕
 *
 * المقبض **شفّاف** لكنّه يَلتقط الـ hover ليُغيّر مؤشّر الفأرة.
 * شريحة بصريّة دقيقة (1dp) داخل المقبض تُشير لمكانه.
 */
@OptIn(ExperimentalSplitPaneApi::class, ExperimentalComposeUiApi::class)
fun SplitterScope.bahthiaVerticalSplitter() {
    visiblePart {
        // الخطّ المرئيّ في الوسط (1dp ترابي)
        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
        )
    }
    handle {
        // المقبض الواسع للسحب — شفّاف لكنّه يَلتقط الفأرة
        Box(
            modifier = Modifier
                .markAsHandle()
                .pointerHoverIcon(PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR)))
                .width(6.dp)
                .fillMaxHeight(),
        )
    }
}

@OptIn(ExperimentalSplitPaneApi::class, ExperimentalComposeUiApi::class)
fun SplitterScope.bahthiaHorizontalSplitter() {
    visiblePart {
        Box(
            modifier = Modifier
                .height(1.dp)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
        )
    }
    handle {
        Box(
            modifier = Modifier
                .markAsHandle()
                .pointerHoverIcon(PointerIcon(Cursor(Cursor.N_RESIZE_CURSOR)))
                .height(6.dp)
                .fillMaxWidth(),
        )
    }
}

/**
 * يُراقب موقع الـ splitter ويَستدعي [onChange] عند كلّ تَغيير
 * (مع distinct لِتَجنّب الكتابات المكرَّرة).
 *
 * يُستعمَل لحفظ الموقع في [com.bahthia.lifecycle.UserPreferences].
 */
@OptIn(ExperimentalSplitPaneApi::class)
@Composable
fun ObserveSplitterPosition(
    state: SplitPaneState,
    onChange: (Float) -> Unit,
) {
    LaunchedEffect(state) {
        snapshotFlow { state.positionPercentage }
            .distinctUntilChanged()
            .collect { onChange(it) }
    }
}
