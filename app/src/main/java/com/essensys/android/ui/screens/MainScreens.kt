package com.essensys.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Grass
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Power
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.Window
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import com.essensys.android.ui.components.FeedbackBanner
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.essensys.android.BuildConfig
import com.essensys.android.data.session.ConnectionMode
import com.essensys.android.ui.components.ActionButton
import com.essensys.android.ui.components.EssensysCard
import com.essensys.android.ui.components.Tone
import com.essensys.android.ui.theme.Essensys
import com.essensys.android.ui.theme.ThemePreference

/** Colonne défilante centrée, utilisable de 360 dp à la tablette (spec portal-theme « Petit écran »). */
@Composable
fun ScreenColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 720.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
    }
}

/** Scénarios rapides de l'accueil (spec home-dashboard). */
private val QUICK_SCENARIOS = listOf(2 to "Je sors", 4 to "Je rentre", 5 to "Je vais me coucher", 6 to "Je me lève")

private data class Tile(val tag: String, val title: String, val description: String, val icon: ImageVector, val open: () -> Unit)

/**
 * Accueil : scénarios rapides et une tuile par fonction, comme le tableau de bord du portail
 * (`DashboardPage.tsx`). L'état de l'armoire et la dernière action restent dans l'en-tête.
 */
@Composable
fun HomeScreen(
    onOpen: (Destination) -> Unit,
    onOpenFeature: (Feature) -> Unit,
    scenariosVm: ScenariosViewModel,
    commandsEnabled: Boolean,
) {
    val scenarios by scenariosVm.state.collectAsStateWithLifecycle()
    val tiles = buildList {
        add(Tile("open-lighting", "Éclairage", "Lumières principales et indirectes", Icons.Outlined.Lightbulb) { onOpen(Destination.LIGHTING) })
        add(Tile("open-shutters", "Volets & stores", "Ouverture et fermeture", Icons.Outlined.Window) { onOpen(Destination.SHUTTERS) })
        add(Tile("open-heating", "Chauffage", "Zones et planning", Icons.Outlined.Thermostat) { onOpen(Destination.HEATING) })
        add(Tile("open-SCENARIOS", "Scénarios", "Je sors, vacances, personnalisés", Icons.Outlined.PlayCircle) { onOpenFeature(Feature.SCENARIOS) })
        add(Tile("open-WATER_HEATER", "Chauffe-eau", "Marche, heures creuses, arrêt", Icons.Outlined.WaterDrop) { onOpenFeature(Feature.WATER_HEATER) })
        add(Tile("open-SPRINKLER", "Arrosage", "Programmation jardin", Icons.Outlined.Grass) { onOpenFeature(Feature.SPRINKLER) })
        add(Tile("open-SECURITY_OUTLETS", "Prises de sécurité", "Couper et rétablir", Icons.Outlined.Power) { onOpenFeature(Feature.SECURITY_OUTLETS) })
        if (BuildConfig.ALARM_ENABLED) add(Tile("open-ALARM", "Alarme", "Mise en service et arrêt", Icons.Outlined.Shield) { onOpenFeature(Feature.ALARM) })
    }
    ScreenColumn {
        FeedbackBanner(scenarios.feedback)
        EssensysCard(title = "Scénarios rapides", description = if (commandsEnabled) "Un appui lance le scénario" else "Armoire hors ligne : lancement impossible") {
            QUICK_SCENARIOS.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { (slot, label) ->
                        ActionButton(
                            label, { scenariosVm.launch(slot, label) }, Modifier.weight(1f).testTag("quick-$slot"), Tone.PRIMARY,
                            loading = "launch-$slot" in scenarios.inFlight, enabled = commandsEnabled, compact = true,
                        )
                    }
                }
            }
        }
        tiles.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { tile -> HomeTile(tile, Modifier.weight(1f).fillMaxHeight()) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun HomeTile(tile: Tile, modifier: Modifier) {
    Surface(
        modifier = modifier.clickable(onClick = tile.open).testTag(tile.tag),
        shape = MaterialTheme.shapes.medium,
        color = Essensys.colors.card,
        border = BorderStroke(1.dp, Essensys.colors.border),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(tile.icon, contentDescription = null, tint = Essensys.colors.primary)
            Text(tile.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = Essensys.colors.text)
            Text(tile.description, style = MaterialTheme.typography.bodySmall, color = Essensys.colors.textMuted)
        }
    }
}

@Composable
fun SettingsScreen(vm: SessionViewModel) {
    val session by vm.session.collectAsStateWithLifecycle()
    ScreenColumn {
        EssensysCard(title = "Connexion") {
            Text(
                if (session.mode == ConnectionMode.CLOUD) "Cloud — ${SessionViewModel.CLOUD_LABEL}" else "Réseau local — ${session.lanHost.substringAfter("://")}",
                color = Essensys.colors.text,
            )
            ActionButton(
                if (session.mode == ConnectionMode.CLOUD) "Passer en réseau local" else "Passer en cloud",
                { vm.setMode(if (session.mode == ConnectionMode.CLOUD) ConnectionMode.LAN else ConnectionMode.CLOUD) },
                Modifier.fillMaxWidth().testTag("switch-mode"), Tone.SECONDARY,
            )
        }
        EssensysCard(title = "Apparence") {
            listOf(ThemePreference.SYSTEM to "Système", ThemePreference.LIGHT to "Clair", ThemePreference.DARK to "Sombre").forEach { (pref, label) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = session.theme == pref, onClick = { vm.setTheme(pref) }, modifier = Modifier.testTag("theme-${pref.name}"))
                    Text(label, color = Essensys.colors.text)
                }
            }
        }
        EssensysCard(title = "Mode test", description = "Les commandes sont validées par le serveur sans être exécutées par l'armoire") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Activer le mode test", Modifier.weight(1f), color = Essensys.colors.text)
                Switch(checked = session.testMode, onCheckedChange = vm::setTestMode, modifier = Modifier.testTag("test-mode"))
            }
        }
        val account by vm.account.collectAsStateWithLifecycle()
        EssensysCard(title = "Compte") {
            if (session.mode == ConnectionMode.CLOUD) {
                account?.user?.let { user ->
                    val name = listOfNotNull(user.firstName, user.lastName).joinToString(" ").ifBlank { null }
                    Text(listOfNotNull(name, user.email).joinToString(" — "), Modifier.testTag("account-user"), color = Essensys.colors.text)
                }
                account?.armoire?.let { armoire ->
                    Text(
                        "Armoire ${armoire.serial ?: "—"}" + (armoire.lastSeen?.let { " · dernier contact ${formatLastSeen(it)}" } ?: ""),
                        Modifier.testTag("account-armoire"), style = MaterialTheme.typography.bodySmall, color = Essensys.colors.textMuted,
                    )
                }
            }
            ActionButton("Se déconnecter", { vm.logout() }, Modifier.fillMaxWidth().testTag("logout"), Tone.DANGER)
            Text("Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall, color = Essensys.colors.textMuted)
        }
    }
}

/** « 2026-10-25T08:00:00Z » → « 25/10/2026 08:00 » (heure locale) ; brut si illisible. */
internal fun formatLastSeen(raw: String): String = runCatching {
    java.time.Instant.parse(raw).atZone(java.time.ZoneId.systemDefault())
        .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
}.getOrDefault(raw)
