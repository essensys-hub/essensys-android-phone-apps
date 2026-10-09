package com.essensys.android.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.essensys.android.data.control.IndexTable
import com.essensys.android.ui.components.EssensysCard
import com.essensys.android.ui.components.FeedbackBanner

/** Volets & stores — même structure que `ShuttersPage.tsx` (spec domotic-controls « Volets »). */
@Composable
fun ShuttersScreen(vm: ControlViewModel, commandsEnabled: Boolean, canReadTravelTimes: Boolean) {
    val inFlight by vm.inFlight.collectAsStateWithLifecycle()
    val feedback by vm.feedback.collectAsStateWithLifecycle()
    val times by vm.travelTimes.collectAsStateWithLifecycle()
    LaunchedEffect(canReadTravelTimes) {
        if (canReadTravelTimes) vm.loadTravelTimes(IndexTable.shutters.map { it.travelTimeIndex })
    }
    ScreenColumn {
        FeedbackBanner(feedback)
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
                    CommandRow(
                        name = shutter.name,
                        tag = "shutter-${shutter.id}",
                        primary = "Ouvrir" to { vm.send("${shutter.id}-open", shutter.name, listOf(shutter.command(true))) },
                        secondary = "Fermer" to { vm.send("${shutter.id}-close", shutter.name, listOf(shutter.command(false))) },
                        loadingPrimary = "${shutter.id}-open" in inFlight,
                        loadingSecondary = "${shutter.id}-close" in inFlight,
                        enabled = commandsEnabled,
                        detail = times[shutter.travelTimeIndex]?.let { "Temps de course : $it s" },
                    )
                }
            }
        }
    }
}
