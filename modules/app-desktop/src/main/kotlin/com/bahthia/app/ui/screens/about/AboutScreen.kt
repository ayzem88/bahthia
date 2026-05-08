package com.bahthia.app.ui.screens.about

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bahthia.app.ui.theme.LocalBahthiaColors
import com.bahthia.domain.AppMetadata
import java.awt.Desktop
import java.net.URI

/** شاشة "حول" — معلومات التطبيق + دعاء + روابط. */
@Composable
fun AboutScreen(onClose: () -> Unit) {
    val colors = LocalBahthiaColors.current

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .verticalScroll(rememberScrollState())
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))
                    .padding(40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = AppMetadata.NAME,
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${AppMetadata.RELEASE_LABEL} — الإصدار ${AppMetadata.VERSION}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )

                Spacer(Modifier.height(24.dp))
                HorizontalDivider(color = colors.divider)
                Spacer(Modifier.height(24.dp))

                Text(
                    text = "أداة بحث متقدّمة في النصوص العربية",
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(20.dp))

                Text("طوّرها", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = AppMetadata.AUTHOR,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                )

                Spacer(Modifier.height(8.dp))

                ClickableText(
                    text = AppMetadata.EMAIL,
                    onClick = { openLink("mailto:${AppMetadata.EMAIL}") },
                )

                Spacer(Modifier.height(4.dp))

                ClickableText(
                    text = AppMetadata.WEBSITE,
                    onClick = { openLink(AppMetadata.WEBSITE) },
                )

                Spacer(Modifier.height(28.dp))
                HorizontalDivider(color = colors.divider)
                Spacer(Modifier.height(20.dp))

                Text(
                    text = "هذا العمل خالصٌ لوجه الله تعالى",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "أسأله أن ينفع به وأن يجعله صدقةً جاريةً\nعن والديَّ — حفظهما الله ورحمهما",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(20.dp))
                Text(
                    text = "دعاؤكم لي ولوالديَّ بظهر الغيب أعظم مكافأة",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(36.dp))

                Button(onClick = onClose) {
                    Text("إغلاق", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun ClickableText(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.clickable { onClick() }.padding(4.dp),
    )
}

private fun openLink(uri: String) {
    try {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(URI(uri))
        }
    } catch (_: Exception) { /* ignore */ }
}
