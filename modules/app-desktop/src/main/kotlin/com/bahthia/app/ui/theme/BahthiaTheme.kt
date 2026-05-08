package com.bahthia.app.ui.theme

import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp

/**
 * الخطّ العربي.
 *
 * نستعمل "Sakkal Majalla" — الخطّ المعتمد في نسخة Python.
 * `FontFamily(name)` في Compose Desktop يحلّ اسم النظام مباشرة.
 * هذا الخطّ مثبَّت على كلّ Windows منذ Office 2007.
 */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
val ArabicFontFamily: FontFamily = FontFamily("Sakkal Majalla")


/**
 * نمط طباعي عربي مع أحجام تلائم القراءة الطويلة.
 */
private val ArabicTypography = Typography(
    displayLarge   = TextStyle(fontFamily = ArabicFontFamily, fontSize = 36.sp, fontWeight = FontWeight.Bold),
    displayMedium  = TextStyle(fontFamily = ArabicFontFamily, fontSize = 30.sp, fontWeight = FontWeight.Bold),
    displaySmall   = TextStyle(fontFamily = ArabicFontFamily, fontSize = 26.sp, fontWeight = FontWeight.SemiBold),
    headlineLarge  = TextStyle(fontFamily = ArabicFontFamily, fontSize = 24.sp, fontWeight = FontWeight.SemiBold),
    headlineMedium = TextStyle(fontFamily = ArabicFontFamily, fontSize = 22.sp, fontWeight = FontWeight.Medium),
    headlineSmall  = TextStyle(fontFamily = ArabicFontFamily, fontSize = 20.sp, fontWeight = FontWeight.Medium),
    titleLarge     = TextStyle(fontFamily = ArabicFontFamily, fontSize = 18.sp, fontWeight = FontWeight.Medium),
    titleMedium    = TextStyle(fontFamily = ArabicFontFamily, fontSize = 16.sp, fontWeight = FontWeight.Medium),
    titleSmall     = TextStyle(fontFamily = ArabicFontFamily, fontSize = 14.sp, fontWeight = FontWeight.Medium),
    bodyLarge      = TextStyle(fontFamily = ArabicFontFamily, fontSize = 16.sp),
    bodyMedium     = TextStyle(fontFamily = ArabicFontFamily, fontSize = 14.sp),
    bodySmall      = TextStyle(fontFamily = ArabicFontFamily, fontSize = 12.sp),
    labelLarge     = TextStyle(fontFamily = ArabicFontFamily, fontSize = 14.sp, fontWeight = FontWeight.Medium),
    labelMedium    = TextStyle(fontFamily = ArabicFontFamily, fontSize = 12.sp),
    labelSmall     = TextStyle(fontFamily = ArabicFontFamily, fontSize = 11.sp),
)

/** الألوان المخصّصة (التظليل، الفواصل) متاحة عبر `LocalBahthiaColors`. */
val LocalBahthiaColors = staticCompositionLocalOf<BahthiaColors> {
    error("BahthiaColors not provided. Wrap your UI with BahthiaTheme { ... }.")
}

/**
 * الـ Composable الرئيسي للسمة. يلفّ كلّ الواجهة ويفرض:
 *   - Material 3 ColorScheme
 *   - الطباعة العربية
 *   - اتجاه RTL
 *   - الألوان المخصّصة عبر LocalBahthiaColors
 *
 * تبديل السمة (`themeState.switchTo(...)`) يُعيد تركيب كلّ الواجهة فوراً.
 */
@Composable
fun BahthiaTheme(
    themeState: ThemeState = rememberThemeState(),
    content: @Composable () -> Unit,
) {
    val systemInDark = isSystemInDarkTheme()
    val colors = colorsFor(themeState.currentKind.value, systemInDark)

    CompositionLocalProvider(
        LocalBahthiaColors provides colors,
        LocalLayoutDirection provides LayoutDirection.Rtl, // RTL افتراضي للعربية
        LocalScrollbarStyle provides com.bahthia.app.ui.components.BahthiaScrollbarStyle,
    ) {
        MaterialTheme(
            colorScheme = colors.scheme,
            typography  = ArabicTypography,
            content     = content,
        )
    }
}
