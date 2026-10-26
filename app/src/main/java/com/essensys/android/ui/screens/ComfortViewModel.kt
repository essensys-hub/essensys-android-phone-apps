package com.essensys.android.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.essensys.android.data.control.ComfortModes
import com.essensys.android.data.control.ComfortRepository
import com.essensys.android.data.control.IndexTable
import com.essensys.android.data.control.InjectOutcome
import com.essensys.android.data.http.ApiResult
import com.essensys.android.ui.components.Feedback
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ComfortUiState(
    val modes: ComfortModes? = null,
    val outlets: IndexTable.SecurityOutletsState = IndexTable.SecurityOutletsState.UNKNOWN,
    /** Commande des prises envoyée : état relu jusqu'à recevoir la nouvelle valeur. */
    val outletsWaiting: Boolean = false,
    val outletsSending: IndexTable.SecurityOutletsAction? = null,
    val inFlight: Set<String> = emptySet(),
    val feedback: Feedback? = null,
)

/**
 * Chauffe-eau, arrosage, prises de sécurité et alarme. Le code d'alarme ne transite que par [alarm] : il n'est jamais
 * gardé dans l'état du ViewModel (spec alarm-control « Protection du code »).
 */
class ComfortViewModel(private val comfort: ComfortRepository) : ViewModel() {

    private val _state = MutableStateFlow(ComfortUiState())
    val state: StateFlow<ComfortUiState> = _state.asStateFlow()

    fun loadModes() = viewModelScope.launch {
        (comfort.readModes() as? ApiResult.Ok)?.let { r -> _state.update { it.copy(modes = r.value) } }
    }

    fun setWaterHeater(mode: IndexTable.WaterHeaterMode) = run("heater-${mode.name}", mode.label) { comfort.setWaterHeater(mode) }

    fun setSprinkler(mode: IndexTable.SprinklerMode) = run("sprinkler-${mode.name}", "Arrosage ${mode.label.lowercase()}") { comfort.setSprinkler(mode) }

    fun loadSecurityOutlets() = viewModelScope.launch { refreshOutlets() }

    /** Une seule commande des prises en vol à la fois ; après un envoi réel, l'état est relu. */
    fun securityOutlets(action: IndexTable.SecurityOutletsAction) {
        if (_state.value.outletsSending != null) return
        _state.update { it.copy(outletsSending = action) }
        run(OUTLETS_KEY, action.label, onSent = { waitForOutlets(action.expectedState) }) { comfort.securityOutlets(action) }
    }

    private var outletsRecheck: Job? = null

    private fun waitForOutlets(expected: IndexTable.SecurityOutletsState) {
        outletsRecheck?.cancel()
        _state.update { it.copy(outletsWaiting = true) }
        outletsRecheck = viewModelScope.launch {
            for (pause in OUTLETS_RECHECK_MS) {
                delay(pause)
                if (refreshOutlets() == expected) break
            }
            _state.update { it.copy(outletsWaiting = false) }
        }
    }

    private suspend fun refreshOutlets(): IndexTable.SecurityOutletsState? =
        (comfort.readSecurityOutlets() as? ApiResult.Ok)?.value?.also { v -> _state.update { it.copy(outlets = v) } }

    fun alarm(on: Boolean, code: String) =
        run(if (on) "alarm-on" else "alarm-off", if (on) "Mise en service de l'alarme" else "Arrêt de l'alarme") { comfort.alarm(on, code) }

    private fun run(key: String, label: String, onSent: () -> Unit = {}, action: suspend () -> ApiResult<InjectOutcome>) {
        var accepted = false
        _state.update { s -> if (key in s.inFlight) s else { accepted = true; s.copy(inFlight = s.inFlight + key, feedback = null) } }
        if (!accepted) return
        viewModelScope.launch {
            val result = action()
            if (result is ApiResult.Ok && result.value == InjectOutcome.SENT) onSent()
            _state.update {
                it.copy(
                    inFlight = it.inFlight - key,
                    outletsSending = if (key == OUTLETS_KEY) null else it.outletsSending,
                    feedback = when (result) {
                        is ApiResult.Ok -> when (result.value) {
                            InjectOutcome.DRY_RUN_OK -> Feedback("$label : commande validée (test, non exécutée)", Feedback.Kind.INFO)
                            InjectOutcome.SENT -> Feedback("$label : commande envoyée — l'armoire exécute sous ~5 s", Feedback.Kind.SUCCESS)
                        }
                        is ApiResult.Err -> Feedback("$label : ${result.error.userMessage}", Feedback.Kind.ERROR)
                    },
                )
            }
        }
    }

    private companion object {
        const val OUTLETS_KEY = "outlets"
        /** Relectures de l'état 440 : 5, 10 puis 20 s après l'envoi (l'armoire exécute en ~5 s). */
        val OUTLETS_RECHECK_MS = listOf(5_000L, 5_000L, 10_000L)
    }
}
