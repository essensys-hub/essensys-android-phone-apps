package com.essensys.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.essensys.android.data.control.ShutterNames
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.essensys.android.ui.components.ActionButton
import com.essensys.android.ui.components.Tone
import com.essensys.android.ui.theme.Essensys
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.essensys.android.data.control.IndexTable
import com.essensys.android.ui.components.EssensysCard
import com.essensys.android.ui.components.FeedbackBanner

/** Volets & stores — même structure que `ShuttersPage.tsx` (spec domotic-controls « Volets »). */
@Composable
fun ShuttersScreen(
    vm: ControlViewModel,
    commandsEnabled: Boolean,
    canReadTravelTimes: Boolean,
    /** Noms personnalisés des volets (affichés partout) ; null = noms d'origine. */
    namesVm: ShutterNamesViewModel? = null,
    /** Administrateur de l'armoire : champ « Nom » et « Nom d'origine » sous chaque volet. */
    canRename: Boolean = false,
) {
    val names = namesVm?.state?.collectAsStateWithLifecycle()?.value
    fun shown(shutter: IndexTable.Shutter) = names?.names?.get(shutter.id)?.display ?: shutter.name
    val inFlight by vm.inFlight.collectAsStateWithLifecycle()
    val feedback by vm.feedback.collectAsStateWithLifecycle()
    val times by vm.travelTimes.collectAsStateWithLifecycle()
    LaunchedEffect(canReadTravelTimes) {
        if (canReadTravelTimes) vm.loadTravelTimes(IndexTable.shutters.map { it.travelTimeIndex })
    }
    ScreenColumn {
        FeedbackBanner(feedback)
        FeedbackBanner(names?.feedback)
        listOf(
            IndexTable.Group.MAIN to ("Volets" to "Volets roulants"),
            IndexTable.Group.SPECIAL to ("Volet Store et Store banne" to "Éléments spéciaux"),
        ).forEach { (group, titles) ->
            val items = IndexTable.shutters.filter { it.group == group }
            EssensysCard(title = titles.first, description = titles.second) {
                GroupActions(
                    onAll = { open ->
                        vm.send("group-$group-$open", if (open) "Tout ouvrir" else "Tout fermer", items.map { it.command(open) })
                    },
                    labels = "Tout ouvrir" to "Tout fermer",
                    tag = "group-$group",
                    inFlight = inFlight,
                    enabled = commandsEnabled,
                )
                items.forEach { shutter ->
                    val shownName = shown(shutter)
                    CommandRow(
                        name = shownName,
                        tag = "shutter-${shutter.id}",
                        primary = "Ouvrir" to { vm.send("${shutter.id}-open", shownName, listOf(shutter.command(true))) },
                        secondary = "Fermer" to { vm.send("${shutter.id}-close", shownName, listOf(shutter.command(false))) },
                        loadingPrimary = "${shutter.id}-open" in inFlight,
                        loadingSecondary = "${shutter.id}-close" in inFlight,
                        enabled = commandsEnabled,
                        detail = times[shutter.travelTimeIndex]?.let { "Temps de course : $it s" },
                    )
                    if (canRename && namesVm != null) {
                        ShutterNamePanel(shutter.id, shownName, names?.names?.get(shutter.id)?.name, namesVm, shutter.id in (names?.saving ?: emptySet()))
                    }
                }
            }
        }
        TravelTimeCard(vm, inFlight, times, commandsEnabled && canReadTravelTimes, ::shown)
    }
}

/** Réglage du temps de course, comme la carte « Temps de course » du portail (spec shutter-travel-time). */
@Composable
private fun TravelTimeCard(
    vm: ControlViewModel,
    inFlight: Set<String>,
    times: Map<Int, String>,
    enabled: Boolean,
    nameOf: (IndexTable.Shutter) -> String,
) {
    val edited = remember { mutableStateMapOf<String, String>() }
    EssensysCard(title = "Temps de course", description = "Durée de commande du moteur, à l'ouverture et à la fermeture (1 à 255 secondes)") {
        IndexTable.shutters.forEach { shutter ->
            val current = times[shutter.travelTimeIndex]
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(nameOf(shutter), Modifier.testTag("time-name-${shutter.id}"), style = MaterialTheme.typography.bodyMedium, color = Essensys.colors.text)
                    Text(
                        current?.let { "Actuel : $it s" } ?: "Non transmis (défaut usine : 120 s, store : 255 s)",
                        Modifier.testTag("time-current-${shutter.id}"),
                        style = MaterialTheme.typography.bodySmall, color = Essensys.colors.textMuted,
                    )
                }
                OutlinedTextField(
                    value = edited[shutter.id].orEmpty(),
                    onValueChange = { v -> edited[shutter.id] = v.filter { it.isDigit() }.take(3) },
                    placeholder = { Text("s") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(76.dp).testTag("time-input-${shutter.id}"),
                )
                ActionButton(
                    "OK",
                    { if (vm.saveTravelTime(shutter, edited[shutter.id].orEmpty())) edited.remove(shutter.id) },
                    Modifier.testTag("time-save-${shutter.id}"), Tone.PRIMARY,
                    loading = "time-${shutter.id}" in inFlight,
                    enabled = enabled && !edited[shutter.id].isNullOrBlank(), compact = true,
                )
            }
        }
    }
}

/**
 * Fiche du volet, partie administrateur (spec shutter-names-clients) : champ « Nom » (1 à 40 caractères, validé avant
 * l'envoi), « Enregistrer » et « Nom d'origine ». Repliée par défaut. Aucun ordre n'est envoyé à l'armoire.
 */
@Composable
private fun ShutterNamePanel(id: String, shownName: String, customName: String?, vm: ShutterNamesViewModel, saving: Boolean) {
    var open by rememberSaveable(id) { mutableStateOf(false) }
    var text by rememberSaveable(id) { mutableStateOf("") }
    var error by rememberSaveable(id) { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton({ open = !open; if (open) { text = shownName; error = null } }, Modifier.testTag("shutter-$id-name-toggle")) {
            Text("Renommer  ${if (open) "▴" else "▾"}")
        }
        if (open) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it; error = null },
                label = { Text("Nom") },
                supportingText = { Text("${text.trim().codePointCount(0, text.trim().length)} / ${ShutterNames.MAX_LENGTH}") },
                singleLine = true,
                isError = error != null,
                enabled = !saving,
                modifier = Modifier.fillMaxWidth().testTag("shutter-$id-name-field"),
            )
            error?.let { Text(it, Modifier.testTag("shutter-$id-name-error"), style = MaterialTheme.typography.bodySmall, color = Essensys.colors.danger) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton(
                    "Enregistrer",
                    {
                        val check = ShutterNames.validate(text)
                        if (check.isFailure) error = ShutterNames.INVALID else { error = null; vm.rename(id, text) }
                    },
                    Modifier.weight(1f).testTag("shutter-$id-name-save"), Tone.PRIMARY, loading = saving, enabled = !saving, compact = true,
                )
                ActionButton(
                    "Nom d'origine", { error = null; vm.reset(id); open = false },
                    Modifier.weight(1f).testTag("shutter-$id-name-reset"), Tone.SECONDARY, enabled = !saving && customName != null, compact = true,
                )
            }
        }
    }
}
