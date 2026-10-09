package com.essensys.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Window
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.essensys.android.data.session.ConnectionMode
import com.essensys.android.data.session.LinkState
import com.essensys.android.di.AppContainer
import com.essensys.android.ui.components.StatusPill
import com.essensys.android.ui.screens.ControlViewModel
import com.essensys.android.ui.screens.Destination
import com.essensys.android.ui.screens.HomeScreen
import com.essensys.android.ui.screens.LightingScreen
import com.essensys.android.ui.screens.LinkScreen
import com.essensys.android.ui.screens.LoginScreen
import com.essensys.android.ui.screens.PasswordChangeScreen
import com.essensys.android.ui.screens.ScreenColumn
import com.essensys.android.ui.screens.SessionViewModel
import com.essensys.android.ui.screens.SettingsScreen
import com.essensys.android.ui.screens.ShuttersScreen
import com.essensys.android.ui.theme.Essensys
import com.essensys.android.ui.theme.EssensysTheme

/** Racine de l'app : thème, puis aiguillage connexion → mot de passe → liaison → commandes. */
@Composable
fun EssensysRoot(container: AppContainer) {
    val sessionVm: SessionViewModel = viewModel { SessionViewModel(container.sessionStore, container.auth, container.portal) }
    val controlVm: ControlViewModel = viewModel { ControlViewModel(container.control) }
    val session by sessionVm.session.collectAsStateWithLifecycle()
    val link by sessionVm.link.collectAsStateWithLifecycle()

    EssensysTheme(preference = session.theme) {
        Column(Modifier.fillMaxSize().background(Essensys.colors.background)) {
            // Les écrans hors Scaffold gèrent eux-mêmes les barres système (edge-to-edge).
            val insets = Modifier.windowInsetsPadding(WindowInsets.safeDrawing)
            when {
                !session.isAuthenticated -> Column(insets) { LoginScreen(sessionVm) }
                session.passwordChangeRequired -> Column(insets) { PasswordChangeScreen(sessionVm) }
                else -> {
                    LaunchedEffect(session.mode, session.isAuthenticated) { sessionVm.refreshLink() }
                    when (val l = link) {
                        null -> Column(insets) { ScreenColumn { Text("Chargement…", color = Essensys.colors.textMuted) } }
                        LinkState.Linked -> MainScaffold(sessionVm, controlVm)
                        else -> Column(insets) { LinkScreen(sessionVm, l) }
                    }
                }
            }
        }
    }
}

@Composable
private fun MainScaffold(sessionVm: SessionViewModel, controlVm: ControlViewModel) {
    var destination by rememberSaveable { mutableStateOf(Destination.HOME) }
    val session by sessionVm.session.collectAsStateWithLifecycle()
    val online by sessionVm.gatewayOnline.collectAsStateWithLifecycle()
    val lastAction by sessionVm.lastAction.collectAsStateWithLifecycle()

    // Rafraîchissements uniquement au premier plan (design D6).
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle, session.mode) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { sessionVm.pollWhileVisible() }
    }
    val commandsEnabled = session.mode == ConnectionMode.LAN || online == true

    Scaffold(
        containerColor = Essensys.colors.background,
        topBar = {
            Column(Modifier.background(Essensys.colors.card).windowInsetsPadding(WindowInsets.statusBars)) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(destination.title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = Essensys.colors.text)
                    if (session.mode == ConnectionMode.LAN) {
                        StatusPill("Réseau local", Essensys.colors.primary, Modifier.testTag("status-lan"))
                    } else when (online) {
                        true -> StatusPill("Armoire en ligne", Essensys.colors.success, Modifier.testTag("status-online"))
                        false -> StatusPill("Armoire hors ligne", Essensys.colors.danger, Modifier.testTag("status-offline"))
                        null -> StatusPill("Cloud", Essensys.colors.secondary)
                    }
                }
                if (session.testMode) {
                    Text(
                        "Mode test actif — les commandes ne sont pas exécutées",
                        Modifier.fillMaxWidth().background(Essensys.colors.warning.copy(alpha = 0.15f)).padding(horizontal = 16.dp, vertical = 6.dp).testTag("test-banner"),
                        color = Essensys.colors.text, style = MaterialTheme.typography.bodySmall,
                    )
                }
                val info = when {
                    session.mode == ConnectionMode.LAN -> "Réseau local — connexion directe à l'armoire"
                    online == false -> "Armoire hors ligne : les commandes sont désactivées"
                    lastAction != null -> "Dernière action : ${lastAction?.actionInfo ?: "—"}${if (lastAction?.isDone == false) " (en cours)" else ""}"
                    else -> null
                }
                if (info != null) {
                    Text(info, Modifier.padding(horizontal = 16.dp, vertical = 4.dp).testTag("session-info"), style = MaterialTheme.typography.bodySmall, color = Essensys.colors.textMuted)
                }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = Essensys.colors.card) {
                Destination.entries.forEach { d ->
                    NavigationBarItem(
                        selected = d == destination,
                        onClick = { destination = d },
                        icon = { Icon(d.icon(), contentDescription = null) },
                        label = { Text(d.label) },
                        modifier = Modifier.testTag("nav-${d.name}"),
                    )
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            when (destination) {
                Destination.HOME -> HomeScreen { destination = it }
                Destination.LIGHTING -> LightingScreen(controlVm, commandsEnabled)
                Destination.SHUTTERS -> ShuttersScreen(controlVm, commandsEnabled, canReadTravelTimes = true)
                Destination.SETTINGS -> SettingsScreen(sessionVm)
            }
        }
    }
}

private fun Destination.icon() = when (this) {
    Destination.HOME -> Icons.Outlined.Home
    Destination.LIGHTING -> Icons.Outlined.Lightbulb
    Destination.SHUTTERS -> Icons.Outlined.Window
    Destination.SETTINGS -> Icons.Outlined.Settings
}
