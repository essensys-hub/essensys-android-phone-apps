package com.essensys.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.essensys.android.data.control.IndexTable
import com.essensys.android.ui.components.ActionButton
import com.essensys.android.ui.components.EssensysCard
import com.essensys.android.ui.components.FeedbackBanner
import com.essensys.android.ui.components.Tone
import com.essensys.android.ui.theme.Essensys

/** Chauffe-eau (spec comfort-controls) : `Cumulus_Mode`, indice 353. */
@Composable
fun WaterHeaterScreen(vm: ComfortViewModel, commandsEnabled: Boolean) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.loadModes() }
    ScreenColumn {
        FeedbackBanner(state.feedback)
        EssensysCard(title = "Mode de fonctionnement", description = "Mode actuel : ${state.modes?.waterHeater?.label ?: "inconnu"}") {
            IndexTable.WaterHeaterMode.entries.forEach { mode ->
                ActionButton(
                    mode.label, { vm.setWaterHeater(mode) }, Modifier.fillMaxWidth().testTag("heater-${mode.name}"),
                    if (state.modes?.waterHeater == mode) Tone.PRIMARY else Tone.SECONDARY,
                    loading = "heater-${mode.name}" in state.inFlight, enabled = commandsEnabled,
                )
            }
            Text(
                "Heures creuses : le chauffe-eau ne chauffe que pendant les heures creuses de votre contrat.",
                style = MaterialTheme.typography.bodySmall, color = Essensys.colors.textMuted,
            )
        }
    }
}

/** Arrosage (spec comfort-controls) : `Arrose_Mode`, indice 363. */
@Composable
fun SprinklerScreen(vm: ComfortViewModel, commandsEnabled: Boolean) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.loadModes() }
    ScreenColumn {
        FeedbackBanner(state.feedback)
        EssensysCard(title = "Mode d'arrosage", description = "Mode actuel : ${state.modes?.sprinklerLabel ?: "inconnu"}") {
            IndexTable.SprinklerMode.entries.forEach { mode ->
                ActionButton(
                    mode.label, { vm.setSprinkler(mode) }, Modifier.fillMaxWidth().testTag("sprinkler-${mode.name}"),
                    if (state.modes?.sprinklerMinutes == mode.value) Tone.PRIMARY else Tone.SECONDARY,
                    loading = "sprinkler-${mode.name}" in state.inFlight, enabled = commandsEnabled,
                )
            }
            Text(
                "Automatique : l'armoire suit le planning d'arrosage programmé.",
                style = MaterialTheme.typography.bodySmall, color = Essensys.colors.textMuted,
            )
        }
    }
}

/**
 * Alarme (spec alarm-control). Le code vit seulement dans cet écran : `remember` (pas `rememberSaveable`),
 * vidé après chaque envoi et dès que l'app passe en arrière-plan.
 */
@Composable
fun AlarmScreen(vm: ComfortViewModel, commandsEnabled: Boolean, lanMode: Boolean) {
    val state by vm.state.collectAsStateWithLifecycle()
    var code by remember { mutableStateOf("") }
    var confirmOff by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { code = ""; confirmOff = false }

    if (confirmOff) {
        AlertDialog(
            onDismissRequest = { confirmOff = false },
            title = { Text("Arrêter l'alarme") },
            text = { Text("Confirmez-vous l'arrêt de l'alarme ?") },
            confirmButton = {
                TextButton({ vm.alarm(on = false, code = code); code = ""; confirmOff = false }, Modifier.testTag("alarm-off-confirm")) { Text("Arrêter") }
            },
            dismissButton = { TextButton({ confirmOff = false }, Modifier.testTag("alarm-off-cancel")) { Text("Annuler") } },
        )
    }

    ScreenColumn {
        FeedbackBanner(state.feedback)
        if (lanMode) {
            EssensysCard(title = "Alarme") {
                Text(
                    "Disponible en mode cloud seulement. La gateway locale ne permet pas encore de piloter l'alarme : connectez-vous en mode cloud.",
                    Modifier.testTag("alarm-cloud-only"), color = Essensys.colors.textMuted,
                )
            }
            return@ScreenColumn
        }
        EssensysCard(title = "Contrôle de l'alarme", description = "Entrez votre code à 4 chiffres") {
            OutlinedTextField(
                value = code,
                onValueChange = { new -> code = new.filter { it.isDigit() }.take(4) },
                label = { Text("Code") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, autoCorrectEnabled = false),
                modifier = Modifier.fillMaxWidth().testTag("alarm-code"),
            )
            val ready = code.length == 4 && commandsEnabled
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton(
                    "Mettre en service", { vm.alarm(on = true, code = code); code = "" }, Modifier.weight(1f).testTag("alarm-on"), Tone.PRIMARY,
                    loading = "alarm-on" in state.inFlight, enabled = ready,
                )
                ActionButton(
                    "Arrêter", { confirmOff = true }, Modifier.weight(1f).testTag("alarm-off"), Tone.DANGER,
                    loading = "alarm-off" in state.inFlight, enabled = ready,
                )
            }
            Text(
                "L'envoi ne garantit pas l'état de l'alarme. Vérifiez l'état sur le clavier de l'armoire.",
                Modifier.testTag("alarm-reminder"), style = MaterialTheme.typography.bodySmall, color = Essensys.colors.textMuted,
            )
        }
    }
}
