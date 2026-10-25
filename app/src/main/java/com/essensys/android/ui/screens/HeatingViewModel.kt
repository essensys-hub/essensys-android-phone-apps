package com.essensys.android.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.essensys.android.data.control.HeatingRepository
import com.essensys.android.data.control.IndexTable
import com.essensys.android.data.control.InjectOutcome
import com.essensys.android.data.control.SaveEvent
import com.essensys.android.data.control.ScheduleCodec
import com.essensys.android.data.control.ScheduleGrid
import com.essensys.android.data.control.SyncEvent
import com.essensys.android.data.http.ApiResult
import com.essensys.android.ui.components.Feedback
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** État de l'écran Chauffage (spec heating-control). */
data class HeatingUiState(
    val zone: IndexTable.HeatingZone = IndexTable.heatingZones.first(),
    val grid: ScheduleGrid = ScheduleGrid.EMPTY,
    val baseline: ScheduleGrid = ScheduleGrid.EMPTY,
    val loaded: Boolean = false,
    val loading: Boolean = false,
    val stale: Boolean = false,
    val day: Int = 0,
    val brush: IndexTable.HeatingSlot = IndexTable.HeatingSlot.CONFORT,
    val saving: Boolean = false,
    val progress: String? = null,
    val syncing: Boolean = false,
    val modeInFlight: IndexTable.ImmediateMode? = null,
    val feedback: Feedback? = null,
) {
    val changes get() = ScheduleCodec.diff(grid, baseline, zone.scheduleStart)
    val dirty get() = grid != baseline

    /** « N octets · K envois armoire » (`formatInjectionLimitHint`). */
    val pendingHint: String? get() {
        val count = changes.size
        if (count == 0) return null
        val chunks = IndexTable.chunk(changes).size
        return if (chunks <= 1) "$count octet${if (count > 1) "s" else ""} · 1 envoi armoire"
        else "$count octets · $chunks envois armoire (max ${IndexTable.MAX_PARAMS_PER_ACTION}/envoi)"
    }
}

class HeatingViewModel(private val heating: HeatingRepository) : ViewModel() {

    private val _state = MutableStateFlow(HeatingUiState())
    val state: StateFlow<HeatingUiState> = _state.asStateFlow()
    private var loadJob: Job? = null

    fun load(zone: IndexTable.HeatingZone = _state.value.zone) {
        if (_state.value.saving || _state.value.syncing) return
        loadJob?.cancel()
        _state.update { it.copy(zone = zone, loading = true, feedback = null, progress = null) }
        loadJob = viewModelScope.launch {
            when (val result = heating.readSchedule(zone)) {
                is ApiResult.Ok -> _state.update {
                    it.copy(grid = result.value.grid, baseline = result.value.grid, stale = result.value.stale, loaded = true, loading = false)
                }
                is ApiResult.Err -> _state.update {
                    it.copy(loading = false, feedback = Feedback("Lecture du planning : ${result.error.userMessage}", Feedback.Kind.ERROR))
                }
            }
        }
    }

    fun selectDay(day: Int) = _state.update { it.copy(day = day) }
    fun selectBrush(slot: IndexTable.HeatingSlot) = _state.update { it.copy(brush = slot) }

    fun paint(hour: Int) = _state.update {
        if (it.saving) it else it.copy(grid = it.grid.with(it.day, hour, it.brush.value), progress = null)
    }

    fun copyDayToAll() = _state.update { if (it.saving) it else it.copy(grid = it.grid.copyDayToAll(it.day)) }
    fun revert() = _state.update { if (it.saving) it else it.copy(grid = it.baseline, progress = null, feedback = null) }

    fun save() {
        val start = _state.value
        if (start.saving || !start.dirty) return
        val zone = start.zone
        val sentGrid = start.grid
        _state.update { it.copy(saving = true, feedback = null) }
        viewModelScope.launch {
            heating.save(start.changes).collect { event ->
                when (event) {
                    is SaveEvent.Start -> _state.update { it.copy(progress = progressLabel(0, event.chunkCount)) }
                    is SaveEvent.Sending -> _state.update { it.copy(progress = progressLabel(event.chunkIndex, event.chunkCount)) }
                    is SaveEvent.ChunkSent -> Unit
                    is SaveEvent.Failed -> _state.update {
                        val sentNote = if (event.sent.isEmpty()) "" else " ${event.sent.size} octets déjà envoyés."
                        it.copy(
                            saving = false,
                            baseline = ScheduleCodec.acceptSent(it.baseline, sentGrid, zone.scheduleStart, event.sent),
                            progress = "Échec à l'envoi ${event.chunkIndex}/${event.chunkCount}",
                            feedback = Feedback("${event.error.userMessage}$sentNote Enregistrez de nouveau pour envoyer le reste.", Feedback.Kind.ERROR),
                        )
                    }
                    is SaveEvent.Done -> _state.update {
                        it.copy(
                            saving = false,
                            baseline = sentGrid,
                            progress = null,
                            feedback = when (event.outcome) {
                                InjectOutcome.DRY_RUN_OK -> Feedback("Planning validé (test, non exécuté)", Feedback.Kind.INFO)
                                InjectOutcome.SENT -> Feedback("Planning envoyé — comptez environ 20 s par envoi armoire", Feedback.Kind.SUCCESS)
                            },
                        )
                    }
                }
            }
        }
    }

    fun syncFromArmoire() {
        val start = _state.value
        if (start.saving || start.syncing) return
        _state.update { it.copy(syncing = true, feedback = null, progress = "Synchronisation…") }
        viewModelScope.launch {
            heating.syncFromArmoire(start.zone).collect { event ->
                when (event) {
                    is SyncEvent.Progress -> _state.update { it.copy(progress = "Lecture de l'armoire : ${event.received}/${event.total} octets") }
                    SyncEvent.Busy -> _state.update {
                        it.copy(syncing = false, progress = null, feedback = Feedback("Synchronisation déjà en cours", Feedback.Kind.INFO))
                    }
                    is SyncEvent.CacheOnly -> _state.update {
                        it.copy(
                            syncing = false, progress = null, grid = event.read.grid, baseline = event.read.grid, stale = event.read.stale,
                            feedback = Feedback("Dernières valeurs reçues de l'armoire", Feedback.Kind.INFO),
                        )
                    }
                    is SyncEvent.Done -> _state.update {
                        it.copy(
                            syncing = false, progress = null, grid = event.read.grid, baseline = event.read.grid, stale = false,
                            feedback = if (event.complete) Feedback("Planning relu depuis l'armoire", Feedback.Kind.SUCCESS)
                            else Feedback("Lecture partielle : l'armoire n'a pas tout renvoyé à temps", Feedback.Kind.ERROR),
                        )
                    }
                    is SyncEvent.Failed -> _state.update {
                        it.copy(syncing = false, progress = null, feedback = Feedback("Synchronisation : ${event.error.userMessage}", Feedback.Kind.ERROR))
                    }
                }
            }
        }
    }

    fun setMode(mode: IndexTable.ImmediateMode) {
        val start = _state.value
        if (start.modeInFlight != null) return
        _state.update { it.copy(modeInFlight = mode, feedback = null) }
        viewModelScope.launch {
            val result = heating.setMode(start.zone, mode)
            _state.update {
                it.copy(
                    modeInFlight = null,
                    feedback = when (result) {
                        is ApiResult.Ok -> if (result.value == InjectOutcome.DRY_RUN_OK) {
                            Feedback("${mode.label} : commande validée (test, non exécutée)", Feedback.Kind.INFO)
                        } else Feedback("${mode.label} : commande envoyée — l'armoire exécute sous ~5 s", Feedback.Kind.SUCCESS)
                        is ApiResult.Err -> Feedback("${mode.label} : ${result.error.userMessage}", Feedback.Kind.ERROR)
                    },
                )
            }
        }
    }

    private fun progressLabel(current: Int, total: Int) =
        if (total <= 1) "Envoi vers l'armoire…" else "Envoi $current/$total — max ${IndexTable.MAX_PARAMS_PER_ACTION} octets/envoi"
}
