package com.essensys.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.essensys.android.ui.components.StatusPill
import com.essensys.android.ui.components.Tone
import com.essensys.android.ui.theme.Essensys

/** Scénarios 2 à 8 (`ScenariosPage.tsx`, spec scenarios). */
@Composable
fun ScenariosScreen(vm: ScenariosViewModel, commandsEnabled: Boolean) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.load() }

    state.confirmRestore?.let { slot ->
        AlertDialog(
            onDismissRequest = vm::cancelRestore,
            title = { Text("Réglages d'usine") },
            text = { Text("Remettre « ${slot.label} » dans son état d'origine ? Vos modifications de ce scénario seront perdues.") },
            confirmButton = { TextButton(vm::confirmRestore, Modifier.testTag("restore-confirm")) { Text("Remettre") } },
            dismissButton = { TextButton(vm::cancelRestore, Modifier.testTag("restore-cancel")) { Text("Annuler") } },
        )
    }

    ScreenColumn {
        FeedbackBanner(state.feedback)
        val editor = state.editor
        if (editor != null) {
            EssensysCard(title = "Éditer — ${editor.slot.label}", description = "Lumières et volets commandés par ce scénario") {
                when {
                    editor.loading -> Text("Chargement…", color = Essensys.colors.textMuted)
                    editor.incomplete -> Text(
                        "La définition de ce scénario n'est pas encore connue (l'armoire ne l'a pas transmise). " +
                            "L'enregistrer remettrait à zéro ses autres réglages : modification impossible pour l'instant.",
                        Modifier.testTag("scenario-incomplete"), color = Essensys.colors.textMuted,
                    )
                    else -> {
                        editor.fields.forEach { field ->
                            Text(field.title(), style = MaterialTheme.typography.titleSmall, color = Essensys.colors.text)
                            field.bits.forEach { bit ->
                                val checked = editor.definition?.isSet(field.index, bit.value) == true
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = checked,
                                        onCheckedChange = { vm.toggle(field.index, bit.value, it) },
                                        enabled = !editor.saving,
                                        modifier = Modifier.testTag("bit-${field.index}-${bit.value}"),
                                    )
                                    Text(bit.label, color = Essensys.colors.text)
                                }
                            }
                        }
                        // Prises de sécurité : Scenario_Securite, décalage 31 du bloc (spec security-outlets).
                        Text("Prises de sécurité", style = MaterialTheme.typography.titleSmall, color = Essensys.colors.text)
                        IndexTable.ScenarioSecurity.entries.forEach { choice ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = editor.definition?.security == choice,
                                    onClick = { vm.setSecurity(choice) },
                                    enabled = !editor.saving,
                                    modifier = Modifier.testTag("scenario-security-${choice.name}"),
                                )
                                Text(choice.label, color = Essensys.colors.text)
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionButton("Fermer", vm::closeEditor, Modifier.weight(1f).testTag("editor-close"), Tone.SECONDARY)
                    ActionButton(
                        "Enregistrer", vm::saveEditor, Modifier.weight(1f).testTag("editor-save"), Tone.PRIMARY,
                        loading = editor.saving, enabled = commandsEnabled && editor.definition != null,
                    )
                }
            }
        }
        state.slots.forEach { slot ->
            EssensysCard(title = slot.label) {
                if (slot.lastLaunched == slot.slot) StatusPill("Dernier lancé", Essensys.colors.success, Modifier.testTag("last-${slot.slot}"))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionButton(
                        "Lancer", { vm.launch(slot.slot, slot.label) }, Modifier.weight(1f).testTag("launch-${slot.slot}"), Tone.PRIMARY,
                        loading = "launch-${slot.slot}" in state.inFlight, enabled = commandsEnabled, compact = true,
                    )
                    if (slot.editable) {
                        ActionButton(
                            "Éditer", { vm.openEditor(slot) }, Modifier.weight(1f).testTag("edit-${slot.slot}"), Tone.SECONDARY,
                            enabled = state.editor == null, compact = true,
                        )
                    }
                }
                if (slot.slot in IndexTable.restorableScenarioSlots) {
                    ActionButton(
                        "Réglages d'usine", { vm.askRestore(slot) }, Modifier.fillMaxWidth().testTag("restore-${slot.slot}"), Tone.SECONDARY,
                        loading = "restore-${slot.slot}" in state.inFlight, enabled = commandsEnabled, compact = true,
                    )
                }
            }
        }
    }
}

/** Libellé lisible des champs `meta/bitmasks` (le portail affiche le nom brut). */
private fun com.essensys.android.data.http.BitmaskField.title(): String = when {
    name.startsWith("Eteindre_PDV") -> "Éteindre — pièces de vie"
    name.startsWith("Eteindre_CHB") -> "Éteindre — chambres"
    name.startsWith("Allumer_CHB") -> "Allumer — chambres"
    name.startsWith("Allumer_PDV") -> "Allumer — pièces de vie"
    name.startsWith("OuvrirVolets") -> "Ouvrir les volets — pièces de vie"
    name.startsWith("FermerVolets") -> "Fermer les volets — pièces de vie"
    else -> name
}
