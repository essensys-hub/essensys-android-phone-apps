package com.essensys.android.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.essensys.android.data.control.ComfortModes
import com.essensys.android.data.control.ComfortRepository
import com.essensys.android.data.control.IndexTable
import com.essensys.android.data.control.InjectOutcome
import com.essensys.android.data.http.ApiResult
import com.essensys.android.ui.components.Feedback
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ComfortUiState(
    val modes: ComfortModes? = null,
    val inFlight: Set<String> = emptySet(),
    val feedback: Feedback? = null,
)

/**
 * Chauffe-eau, arrosage et alarme. Le code d'alarme ne transite que par [alarm] : il n'est jamais
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

    fun alarm(on: Boolean, code: String) =
        run(if (on) "alarm-on" else "alarm-off", if (on) "Mise en service de l'alarme" else "Arrêt de l'alarme") { comfort.alarm(on, code) }

    private fun run(key: String, label: String, action: suspend () -> ApiResult<InjectOutcome>) {
        var accepted = false
        _state.update { s -> if (key in s.inFlight) s else { accepted = true; s.copy(inFlight = s.inFlight + key, feedback = null) } }
        if (!accepted) return
        viewModelScope.launch {
            val result = action()
            _state.update {
                it.copy(
                    inFlight = it.inFlight - key,
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
}
