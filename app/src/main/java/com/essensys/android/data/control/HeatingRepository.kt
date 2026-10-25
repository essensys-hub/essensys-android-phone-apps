package com.essensys.android.data.control

import com.essensys.android.data.auth.AuthRepository
import com.essensys.android.data.http.ApiCaller
import com.essensys.android.data.http.ApiError
import com.essensys.android.data.http.ApiResult
import com.essensys.android.data.http.EssensysJson
import com.essensys.android.data.http.HeatingSyncRequest
import com.essensys.android.data.http.HeatingSyncStarted
import com.essensys.android.data.http.HeatingSyncStatus
import com.essensys.android.data.session.ConnectionMode
import com.essensys.android.data.session.SessionStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.encodeToJsonElement

/** Planning lu ; [stale] = cache cloud ancien ou vide. */
data class ScheduleRead(val grid: ScheduleGrid, val stale: Boolean)

/** Progression de l'enregistrement du planning (design D5), indices de lot à partir de 1. */
sealed interface SaveEvent {
    data class Start(val totalParams: Int, val chunkCount: Int) : SaveEvent
    data class Sending(val chunkIndex: Int, val chunkCount: Int, val indexMin: Int, val indexMax: Int) : SaveEvent
    data class ChunkSent(val chunkIndex: Int, val chunkCount: Int) : SaveEvent
    /** Arrêt de l'envoi : [sent] = couples déjà acceptés par le serveur, [pending] = reste à envoyer. */
    data class Failed(val chunkIndex: Int, val chunkCount: Int, val error: ApiError, val sent: List<Injection>, val pending: List<Injection>) : SaveEvent
    data class Done(val outcome: InjectOutcome) : SaveEvent
}

/** Synchronisation du planning depuis l'armoire (spec heating-control). */
sealed interface SyncEvent {
    data class Progress(val received: Int, val total: Int) : SyncEvent
    data object Busy : SyncEvent
    /** Cloud : pas de lecture réelle, seulement le dernier cache reçu de la gateway. */
    data class CacheOnly(val read: ScheduleRead) : SyncEvent
    data class Done(val read: ScheduleRead, val complete: Boolean) : SyncEvent
    data class Failed(val error: ApiError) : SyncEvent
}

/**
 * Chauffage : planning des zones et mode immédiat. Lecture et écriture via [ControlRepository]
 * (exchange, lots de 30) ; synchronisation réelle `heating/sync` en LAN seulement.
 */
class HeatingRepository(
    private val api: ApiCaller,
    private val store: SessionStore,
    private val auth: AuthRepository,
    private val control: ControlRepository,
    private val syncPollMs: Long = 3_000,
    private val syncTimeoutMs: Long = 90_000,
) {
    suspend fun readSchedule(zone: IndexTable.HeatingZone): ApiResult<ScheduleRead> =
        when (val result = control.readExchangeSnapshot(zone.scheduleIndices.toList())) {
            is ApiResult.Err -> result
            is ApiResult.Ok -> ApiResult.Ok(ScheduleRead(ScheduleCodec.decode(result.value.values, zone.scheduleStart), result.value.stale))
        }

    /** Envoi séquentiel des lots ; s'arrête au premier échec (le portail fait de même). */
    fun save(changes: List<Injection>): Flow<SaveEvent> = flow {
        val chunks = IndexTable.chunk(changes)
        emit(SaveEvent.Start(changes.size, chunks.size))
        var outcome = InjectOutcome.SENT
        chunks.forEachIndexed { i, chunk ->
            emit(SaveEvent.Sending(i + 1, chunks.size, chunk.minOf { it.k }, chunk.maxOf { it.k }))
            when (val result = control.injectBatch(chunk)) {
                is ApiResult.Err -> {
                    emit(SaveEvent.Failed(i + 1, chunks.size, result.error, chunks.take(i).flatten(), chunks.drop(i).flatten()))
                    return@flow
                }
                is ApiResult.Ok -> {
                    if (result.value == InjectOutcome.DRY_RUN_OK) outcome = InjectOutcome.DRY_RUN_OK
                    emit(SaveEvent.ChunkSent(i + 1, chunks.size))
                }
            }
        }
        emit(SaveEvent.Done(outcome))
    }

    suspend fun setMode(zone: IndexTable.HeatingZone, mode: IndexTable.ImmediateMode): ApiResult<InjectOutcome> =
        control.inject(zone.command(mode))

    fun syncFromArmoire(zone: IndexTable.HeatingZone): Flow<SyncEvent> = flow {
        val state = store.state.value
        if (state.mode == ConnectionMode.CLOUD) {
            when (val read = readSchedule(zone)) {
                is ApiResult.Err -> emit(SyncEvent.Failed(read.error))
                is ApiResult.Ok -> emit(SyncEvent.CacheOnly(read.value))
            }
            return@flow
        }
        val body = EssensysJson.encodeToJsonElement(HeatingSyncRequest(zone.scheduleStart, IndexTable.SCHEDULE_BYTES))
        when (val started = api.send(state.baseUrl, "POST", "/api/admin/heating/sync", body)) {
            is ApiResult.Err -> {
                val error = started.error
                if (error is ApiError.Http && error.status == 409) emit(SyncEvent.Busy) else {
                    auth.onApiError(error)
                    emit(SyncEvent.Failed(error))
                }
                return@flow
            }
            is ApiResult.Ok -> runCatching {
                EssensysJson.decodeFromString(HeatingSyncStarted.serializer(), started.value.body).sync
            }.getOrNull()?.let { emit(SyncEvent.Progress(it.received, it.total)) }
        }
        val statusPath = "/api/admin/heating/sync/status?startIndex=${zone.scheduleStart}&byteCount=${IndexTable.SCHEDULE_BYTES}"
        var elapsed = 0L
        var complete = false
        while (elapsed <= syncTimeoutMs) {
            val status = when (val result = api.get(state.baseUrl, statusPath)) {
                is ApiResult.Err -> { auth.onApiError(result.error); emit(SyncEvent.Failed(result.error)); return@flow }
                is ApiResult.Ok -> EssensysJson.decodeFromString(HeatingSyncStatus.serializer(), result.value.body)
            }
            emit(SyncEvent.Progress(status.received, status.total))
            if (!status.active && status.received >= status.total) { complete = status.total > 0; break }
            delay(syncPollMs)
            elapsed += syncPollMs
        }
        when (val read = readSchedule(zone)) {
            is ApiResult.Err -> emit(SyncEvent.Failed(read.error))
            is ApiResult.Ok -> emit(SyncEvent.Done(read.value, complete))
        }
    }
}
