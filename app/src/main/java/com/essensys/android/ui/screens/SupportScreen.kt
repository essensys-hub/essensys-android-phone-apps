package com.essensys.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.essensys.android.data.support.AppLog
import com.essensys.android.data.support.CheckResult
import com.essensys.android.data.support.CheckStatus
import com.essensys.android.data.support.SupportBundle
import com.essensys.android.data.support.SupportDiagnostics
import com.essensys.android.data.support.SupportMail
import com.essensys.android.data.support.SupportPackage
import com.essensys.android.ui.components.ActionButton
import com.essensys.android.ui.components.EssensysCard
import com.essensys.android.ui.components.Tone
import com.essensys.android.ui.theme.Essensys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Bouton Support (app-support-bundle-2026-10-013) : tests au fil de l'eau, puis bundle prêt à envoyer. */
class SupportViewModel(
    private val diagnostics: SupportDiagnostics,
    private val bundle: SupportBundle,
    private val log: AppLog,
) : ViewModel() {
    private val _results = MutableStateFlow<List<CheckResult>>(emptyList())
    val results: StateFlow<List<CheckResult>> = _results.asStateFlow()
    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()
    private val _ready = MutableStateFlow<SupportPackage?>(null)
    /** Bundle prêt : son résumé est affiché, puis envoyé dans le corps du message. */
    val ready: StateFlow<SupportPackage?> = _ready.asStateFlow()

    fun run() {
        if (_running.value) return
        _running.value = true
        _results.value = emptyList()
        _ready.value = null
        viewModelScope.launch {
            log.event("support", "Diagnostic lancé")
            val report = diagnostics.run { r -> _results.value = _results.value + r }
            _results.value = report.checks
            _ready.value = withContext(Dispatchers.IO) { bundle.create(report) }
            _running.value = false
        }
    }

    fun sent() = log.event("support", "Bundle transmis à l'appli email")
}

@Composable
fun SupportScreen(vm: SupportViewModel, onSend: (SupportPackage) -> Unit) {
    val results by vm.results.collectAsStateWithLifecycle()
    val running by vm.running.collectAsStateWithLifecycle()
    val ready by vm.ready.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { if (results.isEmpty() && !running) vm.run() }

    ScreenColumn {
        EssensysCard(
            title = "Diagnostic",
            description = "Tests en lecture seule depuis ce téléphone : aucune commande n'est envoyée à l'armoire.",
        ) {
            if (running) Text("Tests en cours…", Modifier.testTag("support-running"), color = Essensys.colors.textMuted)
            results.groupBy { it.group }.forEach { (group, checks) ->
                Text(group, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = Essensys.colors.text)
                checks.forEach { CheckRow(it) }
            }
        }
        ready?.let { pkg ->
            EssensysCard(title = "Envoyer au support", description = "Votre appli email s'ouvre avec ce résumé et le fichier joint. Vous pouvez le relire avant d'envoyer.") {
                Text("Destinataire : ${SupportMail.ADDRESS}", color = Essensys.colors.text)
                Text(pkg.zip.name, Modifier.testTag("support-zip"), style = MaterialTheme.typography.bodySmall, color = Essensys.colors.textMuted)
                ActionButton("Envoyer au support", { onSend(pkg); vm.sent() }, Modifier.fillMaxWidth().testTag("support-send"))
                ActionButton("Relancer les tests", vm::run, Modifier.fillMaxWidth().testTag("support-rerun"), Tone.SECONDARY)
            }
        }
    }
}

@Composable
private fun CheckRow(check: CheckResult) {
    val status = check.checkStatus
    val color = when (status) {
        CheckStatus.OK -> Essensys.colors.success
        CheckStatus.WARNING -> Essensys.colors.warning
        CheckStatus.ERROR -> Essensys.colors.danger
        CheckStatus.NA -> Essensys.colors.textMuted
    }
    Row(Modifier.fillMaxWidth().testTag("check-${check.id}"), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(status.label, Modifier.testTag("check-${check.id}-status"), color = color, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
        Column(Modifier.weight(1f)) {
            Text(check.label, color = Essensys.colors.text, style = MaterialTheme.typography.bodyMedium)
            Text(check.detail, color = Essensys.colors.textMuted, style = MaterialTheme.typography.bodySmall)
        }
    }
}
