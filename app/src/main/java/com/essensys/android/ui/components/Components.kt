package com.essensys.android.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.essensys.android.ui.theme.Essensys

/** Carte du portail (`ControlCard` : rounded-xl, en-tête, bordure). Spec portal-theme « Composants alignés ». */
@Composable
fun EssensysCard(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = Essensys.colors
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = colors.card,
        border = BorderStroke(1.dp, colors.border),
    ) {
        Column {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(colors.cardHeader)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = colors.text)
                if (description != null) {
                    Text(description, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                }
            }
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
        }
    }
}

enum class Tone { PRIMARY, SECONDARY, DANGER }

@Composable
fun ActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: Tone = Tone.PRIMARY,
    loading: Boolean = false,
    enabled: Boolean = true,
    compact: Boolean = false,
) {
    val colors = Essensys.colors
    // Boutons de ligne plus serrés : les libellés de pièces restent lisibles à 360 dp (spec portal-theme).
    val padding = if (compact) PaddingValues(horizontal = 12.dp, vertical = 8.dp) else ButtonDefaults.ContentPadding
    val content: @Composable () -> Unit = {
        if (loading) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = LocalContentColorFor(tone))
            Spacer(Modifier.width(8.dp))
        }
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    when (tone) {
        Tone.PRIMARY, Tone.DANGER -> Button(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled && !loading,
            shape = MaterialTheme.shapes.small,
            contentPadding = padding,
            colors = ButtonDefaults.buttonColors(containerColor = if (tone == Tone.DANGER) colors.danger else colors.primary),
        ) { content() }
        Tone.SECONDARY -> OutlinedButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled && !loading,
            shape = MaterialTheme.shapes.small,
            contentPadding = padding,
            border = BorderStroke(1.dp, colors.border),
        ) { content() }
    }
}

@Composable
private fun LocalContentColorFor(tone: Tone): Color =
    if (tone == Tone.SECONDARY) Essensys.colors.primary else Color.White

/** Badge d'état en pilule (`rounded-full`). */
@Composable
fun StatusPill(text: String, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier
            .background(color.copy(alpha = 0.12f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(Modifier.size(8.dp), shape = CircleShape, color = color) {}
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

/** Message de retour d'une commande (succès / erreur / test). */
data class Feedback(val text: String, val kind: Kind) {
    enum class Kind { SUCCESS, ERROR, INFO }
}

@Composable
fun FeedbackBanner(feedback: Feedback?, modifier: Modifier = Modifier) {
    if (feedback == null) return
    val colors = Essensys.colors
    val color = when (feedback.kind) {
        Feedback.Kind.SUCCESS -> colors.success
        Feedback.Kind.ERROR -> colors.danger
        Feedback.Kind.INFO -> colors.primary
    }
    Surface(
        modifier = modifier.fillMaxWidth().testTag("feedback"),
        shape = MaterialTheme.shapes.small,
        color = color.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.4f)),
    ) {
        Text(feedback.text, Modifier.padding(12.dp), color = color, style = MaterialTheme.typography.bodyMedium)
    }
}
