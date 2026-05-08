package com.bahthia.app.ui.screens.splash

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.res.useResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * شاشة البَدء — تَظهر أَثناء تَحميل المكتبة. تَحمل:
 *   - شِعار المكتبة
 *   - خلفيّة تُرابيّة بِتَدرُّج خَفيف
 *   - شَريط تَقدُّم حَديث + نِسبة مئويّة (٠..١٠٠) تَعكس الفَحص الحَقيقيّ
 *
 * [progress] يَجب أن يَكون في النِّطاق ‎0f..1f‎.
 */
@Composable
fun SplashScreen(progress: Float) {
    val safeProgress = progress.coerceIn(0f, 1f)
    // إنعام التَّقدُّم بَصريّاً — يَنتقل بِسَلاسَة بَدَل القَفَزات
    val animatedProgress by animateFloatAsState(
        targetValue = safeProgress,
        animationSpec = tween(durationMillis = 220),
        label = "splash-progress",
    )
    val percent = (animatedProgress * 100).toInt().coerceIn(0, 100)

    val logoPainter = remember {
        try {
            BitmapPainter(useResource("icons/splash-logo.png") { loadImageBitmap(it) })
        } catch (_: Throwable) {
            // fallback إلى أيقونة التَّطبيق إن فُقِد splash-logo.png
            try {
                BitmapPainter(useResource("icons/bahthia.png") { loadImageBitmap(it) })
            } catch (_: Throwable) { null }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp),
        ) {
            // ─── الشِّعار ───
            if (logoPainter != null) {
                Image(
                    painter = logoPainter,
                    contentDescription = "Bahthia logo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(220.dp),
                )
            } else {
                // بَديل نَصّي إن فَشِل التَّحميل
                Text(
                    text = "بَحْثيَّة",
                    fontFamily = DecorativeFontFamily,
                    fontSize = 48.sp,
                    color = TextColor,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(Modifier.height(20.dp))
            // ─── العُنوان: ٣٦sp بِخَطّ مُزَخرف ───
            Text(
                text = "المكتبة البَحثيَّة",
                style = TextStyle(
                    fontFamily = DecorativeFontFamily,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = TextColor,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(40.dp))

            // ─── شَريط التَّقدُّم ───
            ProgressBar(progress = animatedProgress, percent = percent)
        }
    }
}

@Composable
private fun ProgressBar(progress: Float, percent: Int) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.widthIn(max = 380.dp).fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(TrackColor),
        ) {
            // الجُزء المُمتَلئ — أَغمَق من الخَلفيّة
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .height(28.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(FillBrush),
            )
            // النِّسبة المئويّة في الوَسَط
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "$percent٪",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = "جارٍ تَجهيز المكتبة…",
            style = MaterialTheme.typography.bodyMedium,
            color = SubtitleColor,
        )
    }
}

// ─── الخَطّ المُزخرف ───
// "Aldhabi" خَطّ زَخرفي مُتوفِّر افتراضيّاً على Windows (Arabic Supplemental).
// إن لم يَتوفَّر، Skia يَتراجع إلى خَطّ النّظام الافتراضيّ تَلقائيّاً.
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private val DecorativeFontFamily: FontFamily = FontFamily("Aldhabi")

// ─── الأَلوان: خَلفيّة فاتِحة جدّاً + شَريط أَغمَق ───
private val BackgroundColor = Color(0xFFFBF7F0) // كريمي فاتح جدّاً
private val TextColor       = Color(0xFF5D3F2A) // بُنّيّ غامِق للنّصّ
private val SubtitleColor   = Color(0xFF8A6E55) // بُنّيّ مُتوسِّط
private val TrackColor      = Color(0x33A67E5D) // مَسار الشَريط (شَفّاف)

private val FillBrush = Brush.horizontalGradient(
    colors = listOf(Color(0xFFA67E5D), Color(0xFF7A5A3F)), // ترابي إلى بُنّيّ غامِق
)
