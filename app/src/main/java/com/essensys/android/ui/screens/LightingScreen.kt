package com.essensys.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.essensys.android.data.control.LayoutKind
import com.essensys.android.data.control.LightNames
import com.essensys.android.data.control.ObjectLayout
import com.essensys.android.data.control.ObjectLayouts
import com.essensys.android.data.control.LightTimer
import androidx.compose.material3.OutlinedTextField
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
fun LightingScreen(
    vm: ControlViewModel,
    commandsEnabled: Boolean,
    /** Non nul pour l'administrateur : réglage de l'extinction automatique des six lumières concernées. */
    timersVm: LightTimersViewModel? = null,
    /** Noms personnalisés des lumières (affichés partout) ; null = noms d'origine. */
    namesVm: LightNamesViewModel? = null,
    /** Administrateur de l'armoire : champ « Nom » et « Nom d'origine » dans la fiche de chaque lumière. */
    canRename: Boolean = false,
    /** Disposition (objets désactivés masqués, ordre choisi) ; null = catalogue complet dans l'ordre d'origine. */
    layoutVm: LightLayoutViewModel? = null,
    /** Administrateur de l'armoire : accès à l'écran de gestion de l'affichage. */
    canManageLayout: Boolean = false,
) {
    val names = namesVm?.state?.collectAsStateWithLifecycle()?.value
    val layoutState = layoutVm?.state?.collectAsStateWithLifecycle()?.value
    val layout = ObjectLayouts.effective(LayoutKind.LIGHTS, layoutState?.layout ?: ObjectLayout.DEFAULT)
    fun shown(light: IndexTable.Light) = names?.names?.get(light.id)?.display ?: light.name
    val inFlight by vm.inFlight.collectAsStateWithLifecycle()
    val feedback by vm.feedback.collectAsStateWithLifecycle()
    LaunchedEffect(timersVm) { timersVm?.load() }
    ScreenColumn {
        FeedbackBanner(feedback)
        timersVm?.let { FeedbackBanner(it.state.collectAsStateWithLifecycle().value.feedback) }
        FeedbackBanner(names?.feedback)
        if (canManageLayout && layoutVm != null) {
            LayoutManager(
                LayoutKind.LIGHTS, layoutVm,
                listOf(IndexTable.Group.MAIN to "Éclairage principal", IndexTable.Group.INDIRECT to "Éclairage indirect"),
                nameOf = { id -> names?.names?.get(id)?.display ?: IndexTable.lights.first { it.id == id }.name },
            )
        }
        listOf(
            IndexTable.Group.MAIN to ("Éclairage principal" to "Plafonniers et appliques"),
            IndexTable.Group.INDIRECT to ("Éclairage indirect" to "Ambiance, chevets, miroirs"),
        ).forEach { (group, titles) ->
            // Une seule liste, filtrée et ordonnée : le groupe et les lignes ne peuvent pas diverger.
            val lights = layout.visible(IndexTable.lights.filter { it.group == group }) { it.id }
            EssensysCard(title = titles.first, description = titles.second) {
                if (lights.isEmpty()) Text(ObjectLayouts.NO_LIGHT, Modifier.testTag("group-$group-empty"), color = Essensys.colors.textMuted)
                else GroupActions(
                    onAll = { on ->
                        vm.send("group-$group-$on", if (on) "Tout allumer" else "Tout éteindre", lights.map { it.command(on) })
                    },
                    labels = "Tout allumer" to "Tout éteindre",
                    tag = "group-$group",
                    inFlight = inFlight,
                    enabled = commandsEnabled,
                )
                lights.forEach { light ->
                    val shownName = shown(light)
                    CommandRow(
                        name = shownName,
                        tag = "light-${light.id}",
                        primary = "Allumer" to { vm.send("${light.id}-on", shownName, listOf(light.command(true))) },
                        secondary = "Éteindre" to { vm.send("${light.id}-off", shownName, listOf(light.command(false))) },
                        loadingPrimary = "${light.id}-on" in inFlight,
                        loadingSecondary = "${light.id}-off" in inFlight,
                        enabled = commandsEnabled,
                    )
                    val timer = LightTimer.byId(light.id)
                    if (timersVm != null && timer != null) LightTimerPanel(timer, timersVm, commandsEnabled, shownName)
                    if (canRename && namesVm != null) LightNamePanel(light.id, shownName, names?.names?.get(light.id)?.name, namesVm, light.id in (names?.saving ?: emptySet()))
                }
            }
        }
    }
}

/** Réglage de l'extinction automatique sous la ligne de la lumière, replié par défaut (spec light-timers-clients). */
@Composable
private fun LightTimerPanel(timer: LightTimer, vm: LightTimersViewModel, commandsEnabled: Boolean, displayName: String) {
    val state by vm.state.collectAsStateWithLifecycle()
    var open by rememberSaveable(timer.id) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton({ open = !open }, Modifier.testTag("light-${timer.id}-timer-toggle")) {
            Text("Extinction auto : ${LightTimer.describe(state.minutes[timer])}  ${if (open) "▴" else "▾"}")
        }
        if (open) LightTimerControl(timer, vm, commandsEnabled, displayName = displayName)
    }
}

/**
 * Fiche de la lumière, partie administrateur (spec light-names-clients) : champ « Nom » (1 à 40 caractères, validé
 * avant l'envoi), « Enregistrer » et « Nom d'origine ». Repliée par défaut. Aucun ordre n'est envoyé à l'armoire.
 */
@Composable
private fun LightNamePanel(id: String, shownName: String, customName: String?, vm: LightNamesViewModel, saving: Boolean) {
    var open by rememberSaveable(id) { mutableStateOf(false) }
    var text by rememberSaveable(id) { mutableStateOf("") }
    var error by rememberSaveable(id) { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton({ open = !open; if (open) { text = shownName; error = null } }, Modifier.testTag("light-$id-name-toggle")) {
            Text("Renommer  ${if (open) "▴" else "▾"}")
        }
        if (open) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it; error = null },
                label = { Text("Nom") },
                supportingText = { Text("${text.trim().codePointCount(0, text.trim().length)} / ${LightNames.MAX_LENGTH}") },
                singleLine = true,
                isError = error != null,
                enabled = !saving,
                modifier = Modifier.fillMaxWidth().testTag("light-$id-name-field"),
            )
            error?.let { Text(it, Modifier.testTag("light-$id-name-error"), style = MaterialTheme.typography.bodySmall, color = Essensys.colors.danger) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton(
                    "Enregistrer",
                    {
                        val check = LightNames.validate(text)
                        if (check.isFailure) error = LightNames.INVALID else { error = null; vm.rename(id, text) }
                    },
                    Modifier.weight(1f).testTag("light-$id-name-save"), Tone.PRIMARY, loading = saving, enabled = !saving, compact = true,
                )
                ActionButton(
                    "Nom d'origine", { error = null; vm.reset(id); open = false },
                    Modifier.weight(1f).testTag("light-$id-name-reset"), Tone.SECONDARY, enabled = !saving && customName != null, compact = true,
                )
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
            Text(name, Modifier.testTag("$tag-name"), style = MaterialTheme.typography.bodyMedium, color = Essensys.colors.text)
            if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = Essensys.colors.textMuted)
        }
        ActionButton(primary.first, primary.second, Modifier.testTag("$tag-primary"), Tone.PRIMARY, loadingPrimary, enabled, compact = true)
        ActionButton(secondary.first, secondary.second, Modifier.testTag("$tag-secondary"), Tone.SECONDARY, loadingSecondary, enabled, compact = true)
    }
}
