package com.essensys.android.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.essensys.android.data.control.IndexTable
import com.essensys.android.data.control.IndexTable.HeatingSlot
import com.essensys.android.data.control.ScheduleGrid
import com.essensys.android.ui.components.ActionButton
import com.essensys.android.ui.components.EssensysCard
import com.essensys.android.ui.components.FeedbackBanner
import com.essensys.android.ui.components.Tone
import com.essensys.android.ui.theme.Essensys

/**
 * Chauffage — planning hebdomadaire par zone et mode immédiat (`HeatingPage.tsx`, spec heating-control).
 * Sur téléphone, la grille s'édite jour par jour (24 cases) au lieu de la grille 7 × 24 du portail.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HeatingScreen(vm: HeatingViewModel, commandsEnabled: Boolean, lanMode: Boolean) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { if (!state.loaded) vm.load() }
    val busy = state.saving || state.syncing

    ScreenColumn {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IndexTable.heatingZones.forEach { zone ->
                FilterChip(
                    selected = zone == state.zone,
                    onClick = { if (zone != state.zone) vm.load(zone) },
                    enabled = !busy,
                    label = { Text(zone.name) },
                    modifier = Modifier.testTag("zone-${zone.id}"),
                )
            }
        }
        FeedbackBanner(state.feedback)
        if (state.stale) {
            Text(
                "Données de l'armoire peut-être anciennes",
                Modifier.fillMaxWidth().background(Essensys.colors.warning.copy(alpha = 0.15f)).padding(12.dp).testTag("heating-stale"),
                color = Essensys.colors.text, style = MaterialTheme.typography.bodySmall,
            )
        }

        EssensysCard(title = "Planning — ${state.zone.name}", description = "Touchez une heure pour lui appliquer la consigne choisie") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                HeatingSlot.palette.forEach { slot ->
                    FilterChip(
                        selected = slot == state.brush,
                        onClick = { vm.selectBrush(slot) },
                        label = { Text(slot.label) },
                        leadingIcon = { Box(Modifier.size(12.dp).background(slot.color(), MaterialTheme.shapes.extraSmall)) },
                        modifier = Modifier.testTag("brush-${slot.name}"),
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                ScheduleGrid.DAY_LABELS.forEachIndexed { day, label ->
                    val selected = day == state.day
                    // Pas de FilterChip ici : ses marges internes coupent « Lu » sur deux lignes à 360 dp.
                    Surface(
                        modifier = Modifier.weight(1f).height(36.dp).clickable { vm.selectDay(day) }
                            .semantics { this.selected = selected }.testTag("day-$day"),
                        shape = MaterialTheme.shapes.small,
                        color = if (selected) Essensys.colors.primary else Essensys.colors.card,
                        border = BorderStroke(1.dp, if (selected) Essensys.colors.primary else Essensys.colors.border),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(label, maxLines = 1, style = MaterialTheme.typography.labelLarge, color = if (selected) Color.White else Essensys.colors.text)
                        }
                    }
                }
            }
            DayGrid(state.grid, state.day, enabled = state.loaded && !busy, onTap = vm::paint)
            ActionButton(
                "Appliquer ${ScheduleGrid.DAY_LABELS[state.day]} à toute la semaine", vm::copyDayToAll,
                Modifier.fillMaxWidth().testTag("heating-copy-day"), Tone.SECONDARY, enabled = state.loaded && !busy,
            )
            state.pendingHint?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Essensys.colors.textMuted, modifier = Modifier.testTag("heating-pending")) }
            state.progress?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Essensys.colors.primary, modifier = Modifier.testTag("heating-progress")) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton("Annuler", vm::revert, Modifier.weight(1f).testTag("heating-revert"), Tone.SECONDARY, enabled = state.dirty && !busy)
                ActionButton(
                    "Enregistrer", vm::save, Modifier.weight(1f).testTag("heating-save"), Tone.PRIMARY,
                    loading = state.saving, enabled = state.dirty && commandsEnabled && !state.syncing,
                )
            }
            ActionButton(
                if (lanMode) "Synchroniser avec l'armoire" else "Relire les dernières valeurs",
                vm::syncFromArmoire, Modifier.fillMaxWidth().testTag("heating-sync"), Tone.SECONDARY,
                loading = state.syncing, enabled = !state.saving && (commandsEnabled || !lanMode),
            )
        }

        EssensysCard(title = "Mode immédiat", description = "S'applique tout de suite à la zone ${state.zone.name}") {
            state.zone.immediateModes().forEach { mode ->
                ActionButton(
                    mode.label, { vm.setMode(mode) }, Modifier.fillMaxWidth().testTag("mode-${mode.name}"),
                    if (mode == IndexTable.ImmediateMode.AUTO) Tone.PRIMARY else Tone.SECONDARY,
                    loading = state.modeInFlight == mode, enabled = commandsEnabled && state.modeInFlight == null,
                )
            }
        }
    }
}

/** 24 heures du jour choisi, en 4 rangées de 6. */
@Composable
private fun DayGrid(grid: ScheduleGrid, day: Int, enabled: Boolean, onTap: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        (0 until 4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                (0 until 6).forEach { col ->
                    val hour = row * 6 + col
                    val slot = HeatingSlot.of(grid[day, hour])
                    val label = slot?.label ?: "?"
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1.2f)
                            .clickable(enabled = enabled) { onTap(hour) }
                            .semantics { contentDescription = "${hour} h : $label" }
                            .testTag("hour-$hour"),
                        shape = MaterialTheme.shapes.small,
                        color = slot.color(),
                        border = BorderStroke(1.dp, Essensys.colors.border),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Text("${hour}h", style = MaterialTheme.typography.labelSmall, color = Color.White, fontWeight = FontWeight.SemiBold)
                            Text(slot?.short() ?: "?", style = MaterialTheme.typography.labelSmall, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

/** Couleurs de l'écran physique SC945D, reprises du portail (`SCHEDULE_MODES`). */
private fun HeatingSlot?.color(): Color = when (this) {
    HeatingSlot.CONFORT -> Color(0xFFEF4444)
    HeatingSlot.ECO -> Color(0xFFF97316)
    HeatingSlot.ECO_PLUS -> Color(0xFFEAB308)
    HeatingSlot.ECO_PLUS_PLUS -> Color(0xFF06B6D4)
    HeatingSlot.OFF -> Color(0xFF64748B)
    HeatingSlot.FROST -> Color(0xFF3B82F6)
    null -> Color(0xFF94A3B8)
}

private fun HeatingSlot.short() = when (this) {
    HeatingSlot.CONFORT -> "C"
    HeatingSlot.ECO -> "E"
    HeatingSlot.ECO_PLUS -> "E+"
    HeatingSlot.ECO_PLUS_PLUS -> "E++"
    HeatingSlot.OFF -> "—"
    HeatingSlot.FROST -> "HG"
}
