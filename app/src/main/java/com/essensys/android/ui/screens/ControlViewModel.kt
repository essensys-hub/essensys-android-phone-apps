package com.essensys.android.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.essensys.android.data.control.ControlRepository
import com.essensys.android.data.control.InjectOutcome
import com.essensys.android.data.control.Injection
import com.essensys.android.data.http.ApiResult
import com.essensys.android.ui.components.Feedback
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Envoi des commandes avec anti-rebond (spec domotic-controls « Retour et anti-rebond ») :
 * une seule commande en vol par cible ; les autres appuis sur la même cible sont ignorés.
 */
class ControlViewModel(private val control: ControlRepository) : ViewModel() {

    private val _inFlight = MutableStateFlow<Set<String>>(emptySet())
    val inFlight: StateFlow<Set<String>> = _inFlight.asStateFlow()

    private val _feedback = MutableStateFlow<Feedback?>(null)
    val feedback: StateFlow<Feedback?> = _feedback.asStateFlow()

    private val _travelTimes = MutableStateFlow<Map<Int, String>>(emptyMap())
    val travelTimes: StateFlow<Map<Int, String>> = _travelTimes.asStateFlow()

    /** @return false si la commande a été ignorée (déjà en vol). */
    fun send(key: String, label: String, injections: List<Injection>): Boolean {
        var accepted = false
        _inFlight.update { current -> if (key in current) current else { accepted = true; current + key } }
        if (!accepted) return false
        _feedback.value = null
        viewModelScope.launch {
            val result = if (injections.size == 1) control.inject(injections.single()) else control.injectAll(injections)
            _feedback.value = when (result) {
                is ApiResult.Ok -> when (result.value) {
                    InjectOutcome.DRY_RUN_OK -> Feedback("$label : commande validée (test, non exécutée)", Feedback.Kind.INFO)
                    InjectOutcome.SENT -> Feedback("$label : commande envoyée — l'armoire exécute sous ~5 s", Feedback.Kind.SUCCESS)
                }
                is ApiResult.Err -> Feedback("$label : ${result.error.userMessage}", Feedback.Kind.ERROR)
            }
            _inFlight.update { it - key }
        }
        return true
    }

    fun loadTravelTimes(keys: List<Int>) {
        viewModelScope.launch {
            (control.readExchange(keys) as? ApiResult.Ok)?.let { _travelTimes.value = it.value }
        }
    }
}
