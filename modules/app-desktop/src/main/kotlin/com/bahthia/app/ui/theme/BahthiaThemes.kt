package com.bahthia.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color

/**
 * نوع السمة المختار من المستخدم.
 *
 * هذه الأسماء معتمدة في الإعدادات وفي ملفّ التفضيلات.
 */
enum class ThemeKind {
    /** ترابي — السمة الافتراضيّة، للقراءة الطويلة. */
    EARTHY,

    /** داكن — لراحة العين ليلاً. */
    DARK,

    /** رصاصي فاتح يشبه ChatGPT — مظهر عصري نظيف. */
    GRAY_CHATGPT,

    /** يتبع نمط نظام التشغيل تلقائياً. */
    AUTO,
}

/**
 * مجموعة الألوان لكلّ سمة. نستعمل Material 3 ColorScheme كأساس،
 * وعندنا أيضاً ألوان مخصّصة (للتظليل، الفصل، إلخ).
 */
data class BahthiaColors(
    val scheme: ColorScheme,
    val highlight: Color,
    val divider: Color,
    val codeBackground: Color,
)

private val EarthyColors = BahthiaColors(
    scheme = lightColorScheme(
        primary       = Color(0xFFA0826D),  // ترابي
        onPrimary     = Color.White,
        primaryContainer = Color(0xFFD4C4B0),
        onPrimaryContainer = Color(0xFF333333),
        secondary     = Color(0xFF8B7355),
        background    = Color(0xFFF5F5F5),
        onBackground  = Color(0xFF333333),
        surface       = Color(0xFFF9F9F9),
        onSurface     = Color(0xFF333333),
        surfaceVariant = Color(0xFFEDE6DA),
        outline       = Color(0xFFA0826D),
        error         = Color(0xFFD32F2F),
        onError       = Color.White,
    ),
    highlight     = Color(0xFFFFD54F),
    divider       = Color(0xFFD4C4B0),
    codeBackground = Color(0xFFEDE6DA),
)

private val DarkColors = BahthiaColors(
    scheme = darkColorScheme(
        primary       = Color(0xFF3D8B7E),
        onPrimary     = Color(0xFFE8E8E8),
        primaryContainer = Color(0xFF1F1F1F),
        onPrimaryContainer = Color(0xFFE8E8E8),
        secondary     = Color(0xFF595959),
        background    = Color(0xFF0D0D0D),
        onBackground  = Color(0xFFE8E8E8),
        surface       = Color(0xFF1F1F1F),
        onSurface     = Color(0xFFE8E8E8),
        surfaceVariant = Color(0xFF2A2A2A),
        outline       = Color(0xFF404040),
        error         = Color(0xFFEF5350),
        onError       = Color.Black,
    ),
    highlight     = Color(0xFFFFB300),
    divider       = Color(0xFF404040),
    codeBackground = Color(0xFF2A2A2A),
)

private val GrayChatGptColors = BahthiaColors(
    scheme = lightColorScheme(
        primary       = Color(0xFF10A37F),  // أخضر ChatGPT
        onPrimary     = Color.White,
        primaryContainer = Color(0xFFE3F1ED),
        onPrimaryContainer = Color(0xFF202123),
        secondary     = Color(0xFF565869),
        background    = Color(0xFFF7F7F8),
        onBackground  = Color(0xFF202123),
        surface       = Color(0xFFECECF1),
        onSurface     = Color(0xFF202123),
        surfaceVariant = Color(0xFFE5E5EA),
        outline       = Color(0xFF8E8EA0),
        error         = Color(0xFFD32F2F),
        onError       = Color.White,
    ),
    highlight     = Color(0xFFFFEB3B),
    divider       = Color(0xFFD9D9E3),
    codeBackground = Color(0xFFECECF1),
)

/**
 * يُرجع ألوان السمة المطلوبة. للوضع AUTO، يجب على المُستدعي أن يحدّد
 * الوضع الحالي للنظام ويمرّر `EARTHY` (للنهاري) أو `DARK` (للداكن) بدله.
 */
fun colorsFor(kind: ThemeKind, systemInDark: Boolean = false): BahthiaColors = when (kind) {
    ThemeKind.EARTHY        -> EarthyColors
    ThemeKind.DARK          -> DarkColors
    ThemeKind.GRAY_CHATGPT  -> GrayChatGptColors
    ThemeKind.AUTO          -> if (systemInDark) DarkColors else EarthyColors
}

/**
 * مدير السمة الحاليّة — حالة قابلة للملاحظة في Compose.
 * تبديلها يُحدِّث الواجهة فوراً بدون إعادة تشغيل.
 */
class ThemeState(initial: ThemeKind = ThemeKind.EARTHY) {
    val currentKind: MutableState<ThemeKind> = mutableStateOf(initial)
    fun switchTo(kind: ThemeKind) {
        currentKind.value = kind
    }
}

@Composable
fun rememberThemeState(initial: ThemeKind = ThemeKind.EARTHY): ThemeState =
    remember { ThemeState(initial) }
