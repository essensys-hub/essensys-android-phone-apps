package com.essensys.android.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.essensys.android.data.control.ComfortRepository
import com.essensys.android.data.control.InjectOutcome
import com.essensys.android.data.control.LightTimer
import com.essensys.android.data.http.ApiResult
import com.essensys.android.ui.components.Feedback
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LightTimersUiState(
    /** Dernière valeur lue pour chaque lumière ; null = inconnue (jamais lue ou lecture impossible). */
    val minutes: Map<LightTimer, Int?> = LightTimer.entries.associateWith { null },
    /** Lumières dont l'envoi est en vol. */
    val sending: Set<LightTimer> = emptySet(),
    /** Lumières dont la valeur est relue après un envoi (5, 10 puis 20 s). */
    val waiting: Set<LightTimer> = emptySet(),
    val feedback: Feedback? = null,
)

/**
 * Temporisations des six lumières (spec light-timers-clients). Après un envoi réel, la valeur est relue à 5, 10 puis
 * 20 s comme les prises de sécurité, jusqu'à recevoir celle demandée ; en mode test, rien n'est exécuté ni relu.
 */
class LightTimersViewModel(
    private val comfort: ComfortRepository,
    private val recheckMs: List<Long> = RECHECK_MS,
) : ViewModel() {

    private val _state = MutableStateFlow(LightTimersUiState())
    val state: StateFlow<LightTimersUiState> = _state.asStateFlow()
    private val rechecks = mutableMapOf<LightTimer, Job>()

    fun load() = viewModelScope.launch { refresh() }

    /** @param minutes 0 à 255 (la validation de la saisie libre est faite par l'écran, `LightTimer.parseFree`). */
    fun set(timer: LightTimer, minutes: Int) {
        if (!LightTimer.isValid(minutes)) return
        var accepted = false
        _state.update { s -> if (timer in s.sending) s else { accepted = true; s.copy(sending = s.sending + timer, feedback = null) } }
        if (!accepted) return
        viewModelScope.launch {
            val result = comfort.setLightTimer(timer, minutes)
            val label = "${timer.label} : ${LightTimer.describe(minutes)}"
            _state.update {
                it.copy(
                    sending = it.sending - timer,
                    feedback = when (result) {
                        is ApiResult.Ok -> when (result.value) {
                            InjectOutcome.DRY_RUN_OK -> Feedback("$label — commande validée (test, non exécutée)", Feedback.Kind.INFO)
                            InjectOutcome.SENT -> Feedback("$label — commande envoyée, l'armoire exécute sous ~5 s", Feedback.Kind.SUCCESS)
                        }
                        is ApiResult.Err -> Feedback("${timer.label} : ${result.error.userMessage}", Feedback.Kind.ERROR)
                    },
                )
            }
            if (result is ApiResult.Ok && result.value == InjectOutcome.SENT) recheck(timer, minutes)
        }
    }

    private fun recheck(timer: LightTimer, expected: Int) {
        rechecks[timer]?.cancel()
        _state.update { it.copy(waiting = it.waiting + timer) }
        rechecks[timer] = viewModelScope.launch {
            for (pause in recheckMs) {
                delay(pause)
                if (refresh()?.get(timer) == expected) break
            }
            _state.update { it.copy(waiting = it.waiting - timer) }
        }
    }

    private suspend fun refresh(): Map<LightTimer, Int?>? =
        (comfort.readLightTimers() as? ApiResult.Ok)?.value?.also { v -> _state.update { it.copy(minutes = v) } }

    companion object {
        /** Relectures 5, 10 puis 20 s après l'envoi (pauses cumulées). */
        val RECHECK_MS = listOf(5_000L, 5_000L, 10_000L)
    }
}
