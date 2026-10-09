package com.essensys.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.essensys.android.data.control.IndexTable
import com.essensys.android.ui.components.ActionButton
import com.essensys.android.ui.components.EssensysCard
import com.essensys.android.ui.components.FeedbackBanner
import com.essensys.android.ui.components.Tone
import com.essensys.android.ui.theme.Essensys

/** Éclairage — même structure que `LightingPage.tsx` (spec domotic-controls « Éclairage »). */
@Composable
fun LightingScreen(vm: ControlViewModel, commandsEnabled: Boolean) {
    val inFlight by vm.inFlight.collectAsStateWithLifecycle()
    val feedback by vm.feedback.collectAsStateWithLifecycle()
    ScreenColumn {
        FeedbackBanner(feedback)
        listOf(
            IndexTable.Group.MAIN to ("Éclairage principal" to "Plafonniers et appliques"),
            IndexTable.Group.INDIRECT to ("Éclairage indirect" to "Ambiance, chevets, miroirs"),
        ).forEach { (group, titles) ->
            val lights = IndexTable.lights.filter { it.group == group }
            EssensysCard(title = titles.first, description = titles.second) {
                GroupActions(
                    onAll = { on ->
                        vm.send("group-$group-$on", if (on) "Tout allumer" else "Tout éteindre", lights.map { it.command(on) })
                    },
                    labels = "Tout allumer" to "Tout éteindre",
                    tag = "group-$group",
                    inFlight = inFlight,
                    enabled = commandsEnabled,
                )
                lights.forEach { light ->
                    CommandRow(
                        name = light.name,
                        tag = "light-${light.id}",
                        primary = "Allumer" to { vm.send("${light.id}-on", light.name, listOf(light.command(true))) },
                        secondary = "Éteindre" to { vm.send("${light.id}-off", light.name, listOf(light.command(false))) },
                        loadingPrimary = "${light.id}-on" in inFlight,
                        loadingSecondary = "${light.id}-off" in inFlight,
                        enabled = commandsEnabled,
                    )
                }
            }
        }
    }
}

@Composable
internal fun GroupActions(
    onAll: (Boolean) -> Unit,
    labels: Pair<String, String>,
    tag: String,
    inFlight: Set<String>,
    enabled: Boolean,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val groupBusy = inFlight.any { it.startsWith(tag) }
        ActionButton(labels.first, { onAll(true) }, Modifier.weight(1f).testTag("$tag-all-on"), Tone.PRIMARY, loading = "$tag-true" in inFlight, enabled = enabled && !groupBusy)
        ActionButton(labels.second, { onAll(false) }, Modifier.weight(1f).testTag("$tag-all-off"), Tone.SECONDARY, loading = "$tag-false" in inFlight, enabled = enabled && !groupBusy)
    }
}

@Composable
internal fun CommandRow(
    name: String,
    tag: String,
    primary: Pair<String, () -> Unit>,
    secondary: Pair<String, () -> Unit>,
    loadingPrimary: Boolean,
    loadingSecondary: Boolean,
    enabled: Boolean,
    detail: String? = null,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyMedium, color = Essensys.colors.text)
            if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = Essensys.colors.textMuted)
        }
        ActionButton(primary.first, primary.second, Modifier.testTag("$tag-primary"), Tone.PRIMARY, loadingPrimary, enabled, compact = true)
        ActionButton(secondary.first, secondary.second, Modifier.testTag("$tag-secondary"), Tone.SECONDARY, loadingSecondary, enabled, compact = true)
    }
}
