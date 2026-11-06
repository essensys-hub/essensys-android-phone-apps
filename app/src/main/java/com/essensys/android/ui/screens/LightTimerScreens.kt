package com.essensys.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.essensys.android.data.control.LightTimer
import com.essensys.android.ui.components.ActionButton
import com.essensys.android.ui.components.EssensysCard
import com.essensys.android.ui.components.FeedbackBanner
import com.essensys.android.ui.components.StatusPill
import com.essensys.android.ui.components.Tone
import com.essensys.android.ui.theme.Essensys

/**
 * Menu « Temporisations » (spec light-timers-clients) : les six lumières avec leur délai courant et le réglage.
 * Réservé aux administrateurs : l'appelant ne l'ouvre pas pour les autres (le serveur refuse de toute façon).
 */
@Composable
fun LightTimersScreen(vm: LightTimersViewModel, commandsEnabled: Boolean) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.load() }
    ScreenColumn {
        FeedbackBanner(state.feedback)
        EssensysCard(
            title = "Temporisations des lumières",
            description = "Délai d'extinction automatique : 1 à 255 minutes, ou Jamais",
        ) {
            if (!commandsEnabled) {
                Text("Armoire hors ligne : réglage indisponible.", Modifier.testTag("timers-offline"), color = Essensys.colors.textMuted)
            }
            LightTimer.entries.forEach { timer ->
                LightTimerControl(timer, vm, commandsEnabled, showName = true)
            }
        }
    }
}

/**
 * Réglage du délai d'une lumière : valeur courante, raccourcis 15 min, 30 min, 1 h, Jamais, et champ libre 1 à 255.
 * Même composant dans le menu central et dans l'écran Éclairage. Une saisie hors plage est refusée ici, sans requête.
 */
@Composable
fun LightTimerControl(timer: LightTimer, vm: LightTimersViewModel, commandsEnabled: Boolean, showName: Boolean = false) {
    val state by vm.state.collectAsStateWithLifecycle()
    val minutes = state.minutes[timer]
    val busy = timer in state.sending
    val waiting = timer in state.waiting
    var free by rememberSaveable(timer.id) { mutableStateOf("") }
    var error by rememberSaveable(timer.id) { mutableStateOf<String?>(null) }
    val tag = "timer-${timer.id}"

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (showName) Text(timer.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = Essensys.colors.text)
            else Text("Extinction automatique", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = Essensys.colors.text)
            StatusPill(
                if (waiting) "En attente de l'armoire" else LightTimer.describe(minutes),
                when {
                    waiting -> Essensys.colors.warning
                    minutes == null -> Essensys.colors.textMuted
                    else -> Essensys.colors.success
                },
                Modifier.semantics(mergeDescendants = true) {}.testTag("$tag-value"),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LightTimer.SHORTCUTS.forEach { (value, label) ->
                ActionButton(
                    label, { error = null; vm.set(timer, value) }, Modifier.weight(1f).testTag("$tag-$value"),
                    if (minutes == value) Tone.PRIMARY else Tone.SECONDARY,
                    loading = false, enabled = commandsEnabled && !busy, compact = true,
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            OutlinedTextField(
                value = free,
                onValueChange = { free = it.filter(Char::isDigit).take(3); error = null },
                label = { Text("Autre (1 à 255 min)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                enabled = commandsEnabled && !busy,
                modifier = Modifier.weight(1f).testTag("$tag-free"),
            )
            ActionButton(
                "Envoyer", {
                    val value = LightTimer.parseFree(free)
                    if (value == null) error = "${timer.label} : entre 1 et 255 minutes (Jamais pour désactiver)"
                    else { error = null; vm.set(timer, value) }
                },
                Modifier.testTag("$tag-send"), Tone.PRIMARY, loading = busy, enabled = commandsEnabled && !busy, compact = true,
            )
        }
        error?.let { Text(it, Modifier.testTag("$tag-error"), style = MaterialTheme.typography.bodySmall, color = Essensys.colors.danger) }
    }
}
