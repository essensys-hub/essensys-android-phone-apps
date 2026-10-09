package com.essensys.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
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

/** Accueil V1 : raccourcis et fonctions à venir, sans données fictives (design « Bientôt disponible »). */
@Composable
fun HomeScreen(onOpen: (Destination) -> Unit) {
    ScreenColumn {
        EssensysCard(title = "Commandes", description = "Pilotez votre installation") {
            ActionButton("Éclairage", { onOpen(Destination.LIGHTING) }, Modifier.fillMaxWidth().testTag("open-lighting"))
            ActionButton("Volets & stores", { onOpen(Destination.SHUTTERS) }, Modifier.fillMaxWidth().testTag("open-shutters"))
        }
        EssensysCard(title = "Bientôt disponible", description = "Prochaine version de l'application") {
            listOf("Chauffage", "Scénarios", "Chauffe-eau", "Arrosage", "Alarme").forEach {
                Text("• $it", color = Essensys.colors.textMuted)
            }
            Text("En attendant, ces fonctions restent accessibles depuis le portail web.", style = MaterialTheme.typography.bodySmall, color = Essensys.colors.textMuted)
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
        EssensysCard(title = "Compte") {
            ActionButton("Se déconnecter", { vm.logout() }, Modifier.fillMaxWidth().testTag("logout"), Tone.DANGER)
            Text("Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall, color = Essensys.colors.textMuted)
        }
    }
}
