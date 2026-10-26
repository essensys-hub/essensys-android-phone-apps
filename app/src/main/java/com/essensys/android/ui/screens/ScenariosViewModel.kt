package com.essensys.android.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.essensys.android.data.control.IndexTable
import com.essensys.android.data.control.InjectOutcome
import com.essensys.android.data.control.ScenarioDefinition
import com.essensys.android.data.control.ScenarioRepository
import com.essensys.android.data.http.ApiResult
import com.essensys.android.data.http.BitmaskField
import com.essensys.android.data.http.ScenarioSlot
import com.essensys.android.ui.components.Feedback
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Éditeur ouvert sur un scénario ; [definition] nulle tant qu'elle n'est pas lue ou si elle est incomplète. */
data class ScenarioEditor(
    val slot: ScenarioSlot,
    val definition: ScenarioDefinition? = null,
    val fields: List<BitmaskField> = emptyList(),
    val loading: Boolean = true,
    val incomplete: Boolean = false,
    val saving: Boolean = false,
)

data class ScenariosUiState(
    val slots: List<ScenarioSlot> = emptyList(),
    val loading: Boolean = false,
    val inFlight: Set<String> = emptySet(),
    val confirmRestore: ScenarioSlot? = null,
    val editor: ScenarioEditor? = null,
    val feedback: Feedback? = null,
)

/** Scénarios (spec scenarios) : liste, lancement, réglages d'usine, édition. Anti-rebond par action. */
class ScenariosViewModel(private val scenarios: ScenarioRepository) : ViewModel() {

    private val _state = MutableStateFlow(ScenariosUiState())
    val state: StateFlow<ScenariosUiState> = _state.asStateFlow()

    fun load() {
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            when (val result = scenarios.list()) {
                is ApiResult.Ok -> _state.update { it.copy(slots = result.value, loading = false) }
                is ApiResult.Err -> _state.update {
                    it.copy(loading = false, feedback = Feedback("Scénarios : ${result.error.userMessage}", Feedback.Kind.ERROR))
                }
            }
        }
    }

    /** Lancement, aussi utilisé par les scénarios rapides de l'accueil. */
    fun launch(slot: Int, label: String) = run("launch-$slot", label) {
        scenarios.launch(slot).also { if (it is ApiResult.Ok) markLaunched(slot) }
    }

    fun askRestore(slot: ScenarioSlot) = _state.update { it.copy(confirmRestore = slot) }
    fun cancelRestore() = _state.update { it.copy(confirmRestore = null) }

    fun confirmRestore() {
        val slot = _state.value.confirmRestore ?: return
        _state.update { it.copy(confirmRestore = null) }
        run("restore-${slot.slot}", "${slot.label} (réglages d'usine)") { scenarios.restore(slot.slot) }
    }

    fun openEditor(slot: ScenarioSlot) {
        _state.update { it.copy(editor = ScenarioEditor(slot), feedback = null) }
        viewModelScope.launch {
            val definition = scenarios.definition(slot.slot)
            val fields = scenarios.bitmasks()
            _state.update { s ->
                val editor = s.editor?.takeIf { it.slot.slot == slot.slot } ?: return@update s
                when {
                    definition is ApiResult.Err -> s.copy(editor = null, feedback = Feedback("${slot.label} : ${definition.error.userMessage}", Feedback.Kind.ERROR))
                    fields is ApiResult.Err -> s.copy(editor = null, feedback = Feedback("${slot.label} : ${fields.error.userMessage}", Feedback.Kind.ERROR))
                    else -> {
                        val def = (definition as ApiResult.Ok).value
                        s.copy(editor = editor.copy(
                            definition = def.takeIf { it.complete },
                            fields = (fields as ApiResult.Ok).value,
                            loading = false,
                            incomplete = !def.complete,
                        ))
                    }
                }
            }
        }
    }

    fun toggle(metaIndex: Int, bitValue: Int, on: Boolean) = _state.update { s ->
        val editor = s.editor ?: return@update s
        val def = editor.definition ?: return@update s
        s.copy(editor = editor.copy(definition = def.toggled(metaIndex, bitValue, on)))
    }

    fun setSecurity(choice: IndexTable.ScenarioSecurity) = _state.update { s ->
        val editor = s.editor ?: return@update s
        val def = editor.definition ?: return@update s
        s.copy(editor = editor.copy(definition = def.withSecurity(choice)))
    }

    fun closeEditor() = _state.update { it.copy(editor = null) }

    fun saveEditor() {
        val editor = _state.value.editor ?: return
        val def = editor.definition ?: return
        if (editor.saving) return
        _state.update { it.copy(editor = editor.copy(saving = true)) }
        viewModelScope.launch {
            val result = scenarios.save(def)
            _state.update { s ->
                s.copy(
                    editor = if (result is ApiResult.Ok) null else s.editor?.copy(saving = false),
                    feedback = feedbackFor("${editor.slot.label} (enregistrement)", result),
                )
            }
        }
    }

    private fun markLaunched(slot: Int) = _state.update { s ->
        s.copy(slots = s.slots.map { it.copy(lastLaunched = if (it.slot == slot) slot else null) })
    }

    private fun run(key: String, label: String, action: suspend () -> ApiResult<InjectOutcome>) {
        var accepted = false
        _state.update { s -> if (key in s.inFlight) s else { accepted = true; s.copy(inFlight = s.inFlight + key, feedback = null) } }
        if (!accepted) return
        viewModelScope.launch {
            val result = action()
            _state.update { it.copy(inFlight = it.inFlight - key, feedback = feedbackFor(label, result)) }
        }
    }

    private fun feedbackFor(label: String, result: ApiResult<InjectOutcome>) = when (result) {
        is ApiResult.Ok -> when (result.value) {
            InjectOutcome.DRY_RUN_OK -> Feedback("$label : commande validée (test, non exécutée)", Feedback.Kind.INFO)
            InjectOutcome.SENT -> Feedback("$label : commande envoyée — l'armoire exécute sous ~5 s", Feedback.Kind.SUCCESS)
        }
        is ApiResult.Err -> Feedback("$label : ${result.error.userMessage}", Feedback.Kind.ERROR)
    }
}
