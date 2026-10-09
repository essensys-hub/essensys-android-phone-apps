package com.essensys.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.essensys.android.data.session.ConnectionMode
import com.essensys.android.data.session.LinkState
import com.essensys.android.ui.components.ActionButton
import com.essensys.android.ui.components.EssensysCard
import com.essensys.android.ui.components.Feedback
import com.essensys.android.ui.components.FeedbackBanner
import com.essensys.android.ui.components.Tone
import com.essensys.android.ui.theme.Essensys

/** Connexion : choix Cloud / Réseau local, hôte LAN, identifiants (specs mobile-auth, connection-modes). */
@Composable
fun LoginScreen(vm: SessionViewModel) {
    val session by vm.session.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val info by vm.info.collectAsStateWithLifecycle()
    val pending by vm.pendingCertificate.collectAsStateWithLifecycle()
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var lanHost by rememberSaveable(session.lanHost) { mutableStateOf(session.lanHost) }

    ScreenColumn {
        Text("Essensys", style = MaterialTheme.typography.headlineMedium, color = Essensys.colors.text)
        EssensysCard(title = "Connexion", description = "Choisissez comment joindre votre installation") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = session.mode == ConnectionMode.CLOUD,
                    onClick = { vm.setMode(ConnectionMode.CLOUD) },
                    label = { Text("Cloud") },
                    modifier = Modifier.testTag("mode-cloud"),
                )
                FilterChip(
                    selected = session.mode == ConnectionMode.LAN,
                    onClick = { vm.setMode(ConnectionMode.LAN) },
                    label = { Text("Réseau local") },
                    modifier = Modifier.testTag("mode-lan"),
                )
            }
            if (session.mode == ConnectionMode.LAN) {
                OutlinedTextField(
                    value = lanHost,
                    onValueChange = { lanHost = it },
                    label = { Text("Adresse de la gateway") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("lan-host"),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
            } else {
                Text("Serveur : ${SessionViewModel.CLOUD_LABEL}", style = MaterialTheme.typography.bodySmall, color = Essensys.colors.textMuted)
            }
            OutlinedTextField(
                value = email, onValueChange = { email = it }, label = { Text("Email") }, singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("email"),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            )
            OutlinedTextField(
                value = password, onValueChange = { password = it }, label = { Text("Mot de passe") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth().testTag("password"),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            )
            FeedbackBanner(error?.let { Feedback(it, Feedback.Kind.ERROR) } ?: info?.let { Feedback(it, Feedback.Kind.INFO) })
            ActionButton(
                label = "Se connecter",
                onClick = {
                    val hostOk = session.mode == ConnectionMode.CLOUD || vm.setLanHost(lanHost)
                    if (hostOk) vm.login(email, password)
                },
                modifier = Modifier.fillMaxWidth().testTag("login"),
                loading = busy,
                enabled = email.isNotBlank() && password.isNotBlank(),
            )
            if (session.mode == ConnectionMode.LAN && session.pinnedLanCertPem != null) {
                TextButton(onClick = vm::forgetLanCertificate) { Text("Oublier le certificat de la gateway") }
            }
        }
    }

    pending?.let { cert ->
        AlertDialog(
            onDismissRequest = { vm.confirmCertificate(false) },
            title = { Text("Confirmer la gateway") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Première connexion à ${cert.host}. Vérifiez que cette empreinte est identique à celle affichée par votre installateur ou par la page d'installation de la gateway :")
                    Text(cert.fingerprint, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("fingerprint"))
                    Text("Si elle diffère, refusez : quelqu'un pourrait intercepter la connexion.", color = Essensys.colors.danger)
                }
            },
            confirmButton = { TextButton(onClick = { vm.confirmCertificate(true) }, modifier = Modifier.testTag("cert-accept")) { Text("Elle est identique") } },
            dismissButton = { TextButton(onClick = { vm.confirmCertificate(false) }) { Text("Refuser") } },
        )
    }
}

/** Changement de mot de passe obligatoire : rien d'autre n'est accessible avant succès. */
@Composable
fun PasswordChangeScreen(vm: SessionViewModel) {
    val busy by vm.busy.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    var current by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    ScreenColumn {
        EssensysCard(title = "Changer votre mot de passe", description = "Obligatoire avant de continuer (8 caractères minimum)") {
            OutlinedTextField(current, { current = it }, label = { Text("Mot de passe actuel") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth().testTag("current-password"))
            OutlinedTextField(new, { new = it }, label = { Text("Nouveau mot de passe") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth().testTag("new-password"))
            FeedbackBanner(error?.let { Feedback(it, Feedback.Kind.ERROR) })
            ActionButton("Valider", { vm.changePassword(current, new) }, Modifier.fillMaxWidth().testTag("change-password"), loading = busy)
            ActionButton("Se déconnecter", { vm.logout() }, Modifier.fillMaxWidth(), Tone.SECONDARY)
        }
    }
}

/** Liaison de l'armoire au compte cloud (spec connection-modes « Liaison de l'armoire »). */
@Composable
fun LinkScreen(vm: SessionViewModel, state: LinkState) {
    val error by vm.error.collectAsStateWithLifecycle()
    var serial by rememberSaveable { mutableStateOf("") }
    var message by rememberSaveable { mutableStateOf("") }
    ScreenColumn {
        EssensysCard(title = "Liaison de votre armoire", description = "Aucune armoire n'est encore liée à ce compte") {
            when (state) {
                is LinkState.Requested -> {
                    val label = when (state.status) {
                        "pending" -> "Demande en cours d'examen"
                        "rejected" -> "Demande refusée"
                        "revoked" -> "Liaison révoquée"
                        else -> "Demande : ${state.status}"
                    }
                    Text("$label${state.machineSerial?.let { " (armoire $it)" } ?: ""}.", modifier = Modifier.testTag("link-status"))
                    if (state.status == "pending") {
                        ActionButton("Actualiser", { vm.refreshLink() }, Modifier.fillMaxWidth(), Tone.SECONDARY)
                    }
                }
                else -> Text("Indiquez le numéro de série de votre armoire pour demander la liaison.")
            }
            if (state !is LinkState.Requested || state.status != "pending") {
                OutlinedTextField(serial, { serial = it }, label = { Text("Numéro de série") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("serial"))
                OutlinedTextField(message, { message = it }, label = { Text("Message (facultatif)") },
                    modifier = Modifier.fillMaxWidth())
                ActionButton("Demander la liaison", { vm.requestLink(serial, message) }, Modifier.fillMaxWidth().testTag("request-link"))
            }
            FeedbackBanner(error?.let { Feedback(it, Feedback.Kind.ERROR) })
            ActionButton("Se déconnecter", { vm.logout() }, Modifier.fillMaxWidth(), Tone.SECONDARY)
        }
    }
}
