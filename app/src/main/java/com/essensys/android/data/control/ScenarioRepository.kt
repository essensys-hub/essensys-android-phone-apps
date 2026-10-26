package com.essensys.android.data.control

import com.essensys.android.data.auth.AuthRepository
import com.essensys.android.data.http.ApiCaller
import com.essensys.android.data.http.ApiError
import com.essensys.android.data.http.ApiResult
import com.essensys.android.data.http.BitmaskField
import com.essensys.android.data.http.BitmaskMeta
import com.essensys.android.data.http.EssensysJson
import com.essensys.android.data.http.InjectResponse
import com.essensys.android.data.http.ScenarioList
import com.essensys.android.data.http.ScenarioSaveRequest
import com.essensys.android.data.http.ScenarioSlot
import com.essensys.android.data.session.ConnectionMode
import com.essensys.android.data.session.SessionStore
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.encodeToJsonElement

/** Définition d'un scénario : valeur de chacun des 41 indices de son bloc. */
data class ScenarioDefinition(val slot: Int, val values: Map<Int, Int>) {
    /** Le backend réécrit les 41 octets (manquant = 0) : on n'édite qu'une définition complète. */
    val complete: Boolean get() = IndexTable.scenarioBlock(slot).all { it in values }

    fun isSet(metaIndex: Int, bitValue: Int): Boolean =
        (values[IndexTable.shiftIntoScenario(metaIndex, slot)] ?: 0) and bitValue != 0

    /** Choix « Prises de sécurité » du scénario (décalage 31 du bloc, spec security-outlets). */
    val security: IndexTable.ScenarioSecurity get() = IndexTable.ScenarioSecurity.of(values[IndexTable.scenarioSecurityIndex(slot)])

    fun withSecurity(choice: IndexTable.ScenarioSecurity): ScenarioDefinition =
        copy(values = values + (IndexTable.scenarioSecurityIndex(slot) to choice.value))

    fun toggled(metaIndex: Int, bitValue: Int, on: Boolean): ScenarioDefinition {
        val index = IndexTable.shiftIntoScenario(metaIndex, slot)
        val current = values[index] ?: 0
        return copy(values = values + (index to if (on) current or bitValue else current and bitValue.inv() and 0xFF))
    }
}

/**
 * Scénarios 2 à 8 (spec scenarios) : cloud `/api/portal/scenarios*` (JWT), LAN `/api/scenarios*` (session).
 * L'en-tête dry-run est ajouté par le client HTTP à toutes les requêtes, en cloud comme en LAN.
 */
class ScenarioRepository(
    private val api: ApiCaller,
    private val store: SessionStore,
    private val auth: AuthRepository,
) {
    private val prefix get() = if (store.state.value.mode == ConnectionMode.CLOUD) "/api/portal/scenarios" else "/api/scenarios"

    suspend fun list(): ApiResult<List<ScenarioSlot>> = get(prefix, ScenarioList.serializer()) { list ->
        list.slots.filter { it.slot in IndexTable.scenarioSlots }.sortedBy { it.slot }
    }

    suspend fun definition(slot: Int): ApiResult<ScenarioDefinition> = get("$prefix/$slot", ScenarioSlot.serializer()) { detail ->
        val block = IndexTable.scenarioBlock(slot)
        ScenarioDefinition(
            slot,
            detail.params.filter { it.k in block }.mapNotNull { p -> p.v.trim().toIntOrNull()?.let { p.k to it } }.toMap(),
        )
    }

    suspend fun bitmasks(): ApiResult<List<BitmaskField>> = get("$prefix/meta/bitmasks", BitmaskMeta.serializer()) { it.fields }

    suspend fun launch(slot: Int): ApiResult<InjectOutcome> {
        require(slot in IndexTable.scenarioSlots) { "scénario $slot non lançable" }
        return mutate("POST", "$prefix/$slot/launch", null)
    }

    suspend fun restore(slot: Int): ApiResult<InjectOutcome> {
        require(slot in IndexTable.restorableScenarioSlots) { "scénario $slot sans réglages d'usine" }
        return mutate("POST", "$prefix/$slot/restore", null)
    }

    /** Enregistre la définition complète ; tout indice hors du bloc est refusé avant l'appel. */
    suspend fun save(definition: ScenarioDefinition): ApiResult<InjectOutcome> {
        val block = IndexTable.scenarioBlock(definition.slot)
        require(definition.complete) { "définition incomplète du scénario ${definition.slot}" }
        require(definition.values.keys.all { it in block }) { "indice hors du bloc du scénario ${definition.slot}" }
        require(definition.values.values.all { it in 0..255 }) { "valeur hors octet" }
        val body = ScenarioSaveRequest(definition.values.toSortedMap().map { (k, v) -> k.toString() to v.toString() }.toMap())
        return mutate("PUT", "$prefix/${definition.slot}", EssensysJson.encodeToJsonElement(body))
    }

    private suspend fun mutate(method: String, path: String, body: kotlinx.serialization.json.JsonElement?): ApiResult<InjectOutcome> =
        when (val result = api.send(store.state.value.baseUrl, method, path, body)) {
            is ApiResult.Err -> result.also { auth.onApiError(it.error) }
            is ApiResult.Ok -> {
                val response = runCatching { EssensysJson.decodeFromString(InjectResponse.serializer(), result.value.body) }.getOrNull()
                ApiResult.Ok(if (response?.status == "test_ok" || response?.dryRun == true) InjectOutcome.DRY_RUN_OK else InjectOutcome.SENT)
            }
        }

    private suspend fun <T, R> get(path: String, serializer: KSerializer<T>, map: (T) -> R): ApiResult<R> =
        when (val result = api.get(store.state.value.baseUrl, path)) {
            is ApiResult.Err -> result.also { auth.onApiError(it.error) }
            is ApiResult.Ok -> runCatching { map(EssensysJson.decodeFromString(serializer, result.value.body)) }
                .fold({ ApiResult.Ok(it) }, { ApiResult.Err(ApiError.Http(result.value.status, null, "Réponse illisible")) })
        }
}
