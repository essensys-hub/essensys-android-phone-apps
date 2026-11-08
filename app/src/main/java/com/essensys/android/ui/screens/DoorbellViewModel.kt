package com.essensys.android.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.essensys.android.data.http.GatewayPresence
import com.essensys.android.data.push.DoorbellOutcome
import com.essensys.android.data.push.DoorbellRepository
import com.essensys.android.data.session.ConnectionMode
import com.essensys.android.ui.components.Feedback
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DoorbellUiState(val enabled: Boolean = false, val busy: Boolean = false, val feedback: Feedback? = null)

/** Interrupteur « Me prévenir quand quelqu'un sonne » (spec doorbell-clients « Option Sonnette »). */
class DoorbellViewModel(private val doorbell: DoorbellRepository) : ViewModel() {

    private val _state = MutableStateFlow(DoorbellUiState())
    val state: StateFlow<DoorbellUiState> = _state.asStateFlow()

    /** À chaque ouverture des réglages : l'état vient du cloud ; en cas d'échec on garde le dernier état connu. */
    fun refresh() = viewModelScope.launch {
        doorbell.refresh()?.let { enabled -> _state.update { it.copy(enabled = enabled) } }
    }

    /** L'utilisateur coupe l'interrupteur : `doorbell_notify=false`, rien d'autre n'est touché (lumières comprises). */
    fun disable() = apply(false)

    /** La permission système est accordée : on enregistre l'appareil avec `doorbell_notify=true`. */
    fun enable() = apply(true)

    /** Le système a refusé la permission : l'interrupteur reste à zéro et un message renvoie aux réglages du système. */
    fun permissionDenied() {
        _state.update { it.copy(enabled = false, feedback = Feedback(PERMISSION_DENIED, Feedback.Kind.ERROR)) }
    }

    fun clearFeedback() = _state.update { it.copy(feedback = null) }

    private fun apply(enabled: Boolean) = viewModelScope.launch {
        _state.update { it.copy(busy = true, feedback = null) }
        when (val r = doorbell.set(enabled)) {
            is DoorbellOutcome.Done -> _state.update { it.copy(enabled = r.enabled, busy = false) }
            DoorbellOutcome.Unavailable -> _state.update {
                it.copy(enabled = false, busy = false, feedback = Feedback(UNAVAILABLE, Feedback.Kind.ERROR))
            }
            is DoorbellOutcome.Failed -> _state.update { it.copy(busy = false, feedback = Feedback(r.message, Feedback.Kind.ERROR)) }
        }
    }

    companion object {
        /** L'option n'existe que si le cloud annonce une gateway (en ligne ou non) ; un cloud sans le champ la masque. */
        fun optionVisible(mode: ConnectionMode, presence: GatewayPresence?): Boolean =
            mode == ConnectionMode.CLOUD && presence != null && presence != GatewayPresence.NONE

        const val PERMISSION_DENIED =
            "Les notifications sont refusées. Autorisez-les pour Mon Essensys dans les réglages du système (Applications, Notifications), puis réessayez."
        const val UNAVAILABLE = "Les notifications ne sont pas disponibles dans cette version de l'application."
        const val HELP = "La sonnette doit être activée sur la gateway pour que la notification arrive."
    }
}
