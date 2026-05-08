package com.bahthia.app.ui.components

import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * شريط تمرير ترابي مطابق لـ Python (book_card_dialog.py:57-72):
 *   - النطاق:  #ede7e3 (بيج فاتح)
 *   - الإبهام: #a0826d (ترابي)
 *   - hover:   #8b7355 (ترابي أغمق)
 *   - العرض:   10dp
 *   - الشكل:   مدوَّر 5dp
 *
 * يَستعمله المُكوّنان [LazyColumnWithScrollbar] و [ScrollableColumn].
 */
val BahthiaScrollbarStyle: ScrollbarStyle = ScrollbarStyle(
    minimalHeight = 16.dp,
    thickness = 10.dp,
    shape = RoundedCornerShape(5.dp),
    hoverDurationMillis = 250,
    unhoverColor = Color(0xFFA0826D).copy(alpha = 0.55f),
    hoverColor   = Color(0xFF8B7355),
)

/**
 * يُغلِّف كامل تطبيق Compose داخل [content] بحيث يُستعمل [BahthiaScrollbarStyle]
 * تلقائياً لأيّ `VerticalScrollbar` أو `HorizontalScrollbar` لاحقاً.
 */
@Composable
fun ProvideBahthiaScrollbarStyle(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalScrollbarStyle provides BahthiaScrollbarStyle) {
        content()
    }
}

/**
 * `LazyColumn` مع شريط تمرير عمودي ترابي يَظهر تلقائياً عند الحاجة.
 *
 * الشريط في **يسار** الصندوق ليُطابق سلوك Python في الواجهة العربيّة.
 */
@Composable
fun LazyColumnWithScrollbar(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    content: LazyListScope.() -> Unit,
) {
    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = state,
            modifier = Modifier.fillMaxSize().padding(start = 12.dp), // مكان للشريط على اليسار
            content = content,
        )
        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(state),
            style = BahthiaScrollbarStyle,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .padding(vertical = 2.dp),
        )
    }
}

/**
 * عمود قابل للتمرير عمودياً مع شريط ترابي.
 *
 * يُستعمل لمحتوى نصّي طويل (صفحة كتاب، نتيجة بحث، إلخ) لا يَستعمل LazyColumn.
 */
@Composable
fun ScrollableColumn(
    modifier: Modifier = Modifier,
    state: ScrollState = rememberScrollState(),
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier.fillMaxSize()) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.fillMaxSize().verticalScroll(state).padding(start = 12.dp),
        ) {
            content()
        }
        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(state),
            style = BahthiaScrollbarStyle,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .padding(vertical = 2.dp),
        )
    }
}
