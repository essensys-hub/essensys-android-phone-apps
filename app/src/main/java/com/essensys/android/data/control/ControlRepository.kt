package com.essensys.android.data.control

import com.essensys.android.data.auth.AuthRepository
import com.essensys.android.data.http.ApiCaller
import com.essensys.android.data.http.ApiResult
import com.essensys.android.data.http.EssensysJson
import com.essensys.android.data.http.ExchangeResponse
import com.essensys.android.data.http.InjectRequest
import com.essensys.android.data.http.InjectResponse
import com.essensys.android.data.session.ConnectionMode
import com.essensys.android.data.session.SessionStore
import kotlinx.serialization.json.encodeToJsonElement

/** Issue d'une commande, vue par l'UI. */
enum class InjectOutcome { SENT, DRY_RUN_OK }

/**
 * Envoi des commandes domotiques selon le mode (spec domotic-controls « Envoi selon le mode ») :
 * cloud `POST /api/portal/inject` (JWT), LAN `POST /api/admin/inject` (session gateway).
 * Lecture : `GET …/exchange?keys=` (temps de course des volets).
 */
class ControlRepository(
    private val api: ApiCaller,
    private val store: SessionStore,
    private val auth: AuthRepository,
) {
    suspend fun inject(injection: Injection): ApiResult<InjectOutcome> {
        val state = store.state.value
        val path = if (state.mode == ConnectionMode.CLOUD) "/api/portal/inject" else "/api/admin/inject"
        val body = EssensysJson.encodeToJsonElement(InjectRequest(injection.k, injection.v))
        return when (val result = api.send(state.baseUrl, "POST", path, body)) {
            is ApiResult.Err -> result.also { auth.onApiError(it.error) }
            is ApiResult.Ok -> {
                val response = runCatching {
                    EssensysJson.decodeFromString(InjectResponse.serializer(), result.value.body)
                }.getOrNull()
                val dryRun = response?.status == "test_ok" || response?.dryRun == true
                ApiResult.Ok(if (dryRun) InjectOutcome.DRY_RUN_OK else InjectOutcome.SENT)
            }
        }
    }

    /** Commandes de groupe : séquentielles, une injection par sortie, arrêt à la première erreur (comme le portail). */
    suspend fun injectAll(injections: List<Injection>): ApiResult<InjectOutcome> {
        var outcome = InjectOutcome.SENT
        for (injection in injections) {
            when (val result = inject(injection)) {
                is ApiResult.Err -> return result
                is ApiResult.Ok -> if (result.value == InjectOutcome.DRY_RUN_OK) outcome = InjectOutcome.DRY_RUN_OK
            }
        }
        return ApiResult.Ok(outcome)
    }

    suspend fun readExchange(keys: List<Int>): ApiResult<Map<Int, String>> {
        val state = store.state.value
        val prefix = if (state.mode == ConnectionMode.CLOUD) "/api/portal" else "/api/admin"
        return when (val result = api.get(state.baseUrl, "$prefix/exchange?keys=${keys.joinToString(",")}")) {
            is ApiResult.Err -> result.also { auth.onApiError(it.error) }
            is ApiResult.Ok -> ApiResult.Ok(
                EssensysJson.decodeFromString(ExchangeResponse.serializer(), result.value.body)
                    .values.associate { it.k to it.v },
            )
        }
    }
}
