package com.bahthia.app.ui.screens.welcome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bahthia.app.ui.theme.LocalBahthiaColors
import com.bahthia.domain.AppMetadata

/**
 * شاشة الترحيب التي تظهر عند أوّل تشغيل،
 * ومن قائمة "حول" متى شاء المستخدم.
 *
 * تجمع: بسملة، ترحيب، تعريف بالمكتبة، تعريف بالمطوّر،
 * طلب الدعاء، زرّ بدء جولة و زرّ تخطّي.
 *
 * @param onStartTour       يُستدعى عند نقر "ابدأ الجولة التعريفية".
 * @param onSkip            يُستدعى عند نقر "تخطّي".
 */
@Composable
fun WelcomeScreen(
    onStartTour: () -> Unit,
    onSkip: () -> Unit,
) {
    val colors = LocalBahthiaColors.current

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .verticalScroll(rememberScrollState())
                    .background(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(20.dp),
                    )
                    .padding(40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // ﷽
                Text(
                    text = "بسم الله الرحمن الرحيم",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(20.dp))

                // السلام
                Text(
                    text = "السلام عليكم ورحمة الله وبركاته",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(28.dp))

                HorizontalDivider(color = colors.divider, thickness = 1.dp)

                Spacer(Modifier.height(28.dp))

                // الترحيب
                Text(
                    text = "أهلاً بك في ${AppMetadata.NAME}",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = "أداة بحثٍ متقدّمة في النصوص العربية",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(36.dp))

                // التعريف بالمطوّر
                Text(
                    text = "طوّرها بحبٍّ ابتغاءً لوجه الله",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = AppMetadata.AUTHOR,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    text = AppMetadata.EMAIL,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(36.dp))

                HorizontalDivider(color = colors.divider, thickness = 1.dp)

                Spacer(Modifier.height(28.dp))

                // الدعاء والإهداء
                Text(
                    text = "هذا العمل خالصٌ لوجه الله تعالى\n" +
                            "أسأله أن ينفع به وأن يجعله صدقةً جاريةً\n" +
                            "عن والديَّ — حفظهما الله ورحمهما",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(20.dp))

                Text(
                    text = "دعاؤكم لي ولوالديَّ بظهر الغيب أعظم مكافأة",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(40.dp))

                // أزرار الإجراء
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = onStartTour,
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor   = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        Text(
                            text = "ابدأ الجولة التعريفيّة",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }

                    Spacer(Modifier.width(8.dp))

                    OutlinedButton(
                        onClick = onSkip,
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
                    ) {
                        Text(
                            text = "تخطّي",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                Text(
                    text = "${AppMetadata.RELEASE_LABEL} — الإصدار ${AppMetadata.VERSION}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                )
            }
        }
    }
}
