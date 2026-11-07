package com.essensys.android.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.essensys.android.data.control.ComfortRepository
import com.essensys.android.data.control.ShutterName
import com.essensys.android.data.control.ShutterNames
import com.essensys.android.data.http.ApiResult
import com.essensys.android.ui.components.Feedback
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ShutterNamesUiState(
    /** Les 16 volets et stores ; noms d'origine tant que la lecture n'a pas abouti (ou si elle échoue). */
    val names: Map<String, ShutterName> = ShutterNames.fallback(),
    /** Volets dont l'écriture est en vol. */
    val saving: Set<String> = emptySet(),
    val feedback: Feedback? = null,
)

/**
 * Noms personnalisés des volets et stores (spec shutter-names-clients). Partagé par la liste, la carte « Temps de
 * course » et les confirmations : [displayName] donne le nom à afficher (texte brut). Renommer n'envoie rien à l'armoire.
 */
class ShutterNamesViewModel(private val comfort: ComfortRepository) : ViewModel() {

    private val _state = MutableStateFlow(ShutterNamesUiState())
    val state: StateFlow<ShutterNamesUiState> = _state.asStateFlow()

    /** Relit les noms ; un échec laisse les noms en place (d'origine au premier démarrage), sans message bloquant. */
    fun load() = viewModelScope.launch {
        (comfort.readShutterNames() as? ApiResult.Ok)?.value?.let { names -> _state.update { it.copy(names = names) } }
    }

    fun displayName(id: String, default: String? = null): String =
        _state.value.names[id]?.display ?: default ?: id

    fun rename(id: String, raw: String) {
        val name = ShutterNames.validate(raw).getOrElse {
            _state.update { s -> s.copy(feedback = Feedback("${displayName(id)} : ${ShutterNames.INVALID}", Feedback.Kind.ERROR)) }
            return
        }
        save(id, name)
    }

    /** « Nom d'origine » : `name` repasse à null et le nom d'origine réapparaît. */
    fun reset(id: String) = save(id, null)

    fun clearFeedback() = _state.update { it.copy(feedback = null) }

    private fun save(id: String, name: String?) {
        var accepted = false
        _state.update { s -> if (id in s.saving) s else { accepted = true; s.copy(saving = s.saving + id, feedback = null) } }
        if (!accepted) return
        viewModelScope.launch {
            val result = comfort.setShutterName(id, name)
            _state.update { s ->
                val feedback = when (result) {
                    is ApiResult.Ok -> if (result.value.dryRun) {
                        Feedback("${s.names[id]?.display ?: id} : nom validé (test, non enregistré)", Feedback.Kind.INFO)
                    } else {
                        Feedback(if (name == null) "Nom d'origine rétabli" else "Nom enregistré", Feedback.Kind.SUCCESS)
                    }
                    is ApiResult.Err -> Feedback("${s.names[id]?.display ?: id} : ${ShutterNames.errorMessage(result.error)}", Feedback.Kind.ERROR)
                }
                val saved = (result as? ApiResult.Ok)?.value?.takeIf { !it.dryRun }
                val names = if (saved == null) s.names else {
                    val current = s.names[id] ?: ShutterNames.fallback()[id]
                    val updated = saved.shutter ?: current?.copy(name = name)
                    if (updated == null) s.names else s.names + (id to updated)
                }
                s.copy(saving = s.saving - id, names = names, feedback = feedback)
            }
        }
    }
}
