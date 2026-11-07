package com.essensys.android.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.essensys.android.data.control.ComfortRepository
import com.essensys.android.data.control.EffectiveLayout
import com.essensys.android.data.control.LayoutKind
import com.essensys.android.data.control.ObjectLayout
import com.essensys.android.data.control.ObjectLayouts
import com.essensys.android.data.http.ApiResult
import com.essensys.android.ui.components.Feedback
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Modifications locales de l'écran de gestion, envoyées en une seule écriture par « Enregistrer ». */
data class LayoutDraft(val layout: EffectiveLayout, val reset: Boolean = false)

data class ObjectLayoutUiState(
    /** Dernière disposition lue ; par défaut (catalogue complet, ordre d'origine) tant qu'aucune lecture n'a réussi. */
    val layout: ObjectLayout = ObjectLayout.DEFAULT,
    /** Une lecture a abouti, par succès ou par échec : les écrans qui dépendent de la liste active peuvent démarrer. */
    val settled: Boolean = false,
    val draft: LayoutDraft? = null,
    val saving: Boolean = false,
    val feedback: Feedback? = null,
)

/**
 * Disposition d'un genre d'objets (spec object-layout-clients). Partagé par les listes, les commandes de groupe, les
 * Temporisations et « Temps de course » : [effective] donne la disposition à appliquer. La lecture ne remplace jamais
 * une disposition déjà lue par un échec. Ne commande jamais l'armoire.
 */
open class ObjectLayoutViewModel(private val kind: LayoutKind, private val comfort: ComfortRepository) : ViewModel() {

    private val _state = MutableStateFlow(ObjectLayoutUiState())
    val state: StateFlow<ObjectLayoutUiState> = _state.asStateFlow()

    fun effective(): EffectiveLayout = ObjectLayouts.effective(kind, _state.value.layout)

    /** Relit la disposition ; un échec (404, coupure) garde la dernière valeur lue, sans message bloquant. */
    fun load() = viewModelScope.launch {
        val result = comfort.readObjectLayout(kind)
        _state.update { s -> if (result is ApiResult.Ok) s.copy(layout = result.value, settled = true) else s.copy(settled = true) }
    }

    /** Ouvre l'écran de gestion sur la disposition effective (tous les objets, désactivés compris). */
    fun startEdit() = _state.update { it.copy(draft = LayoutDraft(effective()), feedback = null) }

    fun cancelEdit() = _state.update { it.copy(draft = null, feedback = null) }

    fun toggle(id: String) = edit { draft -> draft.copy(layout = draft.layout.toggled(id), reset = false) }

    fun move(id: String, delta: Int) = edit { draft -> draft.copy(layout = draft.layout.move(id, delta, kind::groupOf), reset = false) }

    /** « Ordre d'origine » : ordre du catalogue, tout réactivé ; l'écriture sera `{"reset":true}`. */
    fun resetToOrigin() = edit { LayoutDraft(ObjectLayouts.effective(kind, ObjectLayout.DEFAULT), reset = true) }

    private fun edit(change: (LayoutDraft) -> LayoutDraft) = _state.update { s -> s.draft?.let { s.copy(draft = change(it)) } ?: s }

    fun clearFeedback() = _state.update { it.copy(feedback = null) }

    /** « Enregistrer » : une seule écriture. En cas d'erreur l'écran reste ouvert avec les modifications. */
    fun save() {
        val draft = _state.value.draft ?: return
        if (_state.value.saving) return
        _state.update { it.copy(saving = true, feedback = null) }
        viewModelScope.launch {
            val order = draft.layout.ordered
            val disabled = draft.layout.ordered.filter { it in draft.layout.disabled }
            val result = comfort.writeObjectLayout(kind, order, disabled, draft.reset)
            _state.update { s ->
                when (result) {
                    is ApiResult.Err -> s.copy(saving = false, feedback = Feedback(ObjectLayouts.errorMessage(result.error), Feedback.Kind.ERROR))
                    is ApiResult.Ok ->
                        if (result.value.dryRun) s.copy(saving = false, feedback = Feedback("Disposition validée (test, non enregistrée)", Feedback.Kind.INFO))
                        else s.copy(
                            saving = false, draft = null,
                            layout = result.value.layout ?: ObjectLayout(if (draft.reset) emptyList() else order, if (draft.reset) emptyList() else disabled),
                            feedback = Feedback("Disposition enregistrée", Feedback.Kind.SUCCESS),
                        )
                }
            }
        }
    }
}

class LightLayoutViewModel(comfort: ComfortRepository) : ObjectLayoutViewModel(LayoutKind.LIGHTS, comfort)

class ShutterLayoutViewModel(comfort: ComfortRepository) : ObjectLayoutViewModel(LayoutKind.SHUTTERS, comfort)
