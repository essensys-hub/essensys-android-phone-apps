package com.essensys.android.data.session

import com.essensys.android.data.auth.AuthRepository
import com.essensys.android.data.http.ApiCaller
import com.essensys.android.data.http.ApiResult
import com.essensys.android.data.http.EssensysJson
import com.essensys.android.data.http.HistoryLatest
import com.essensys.android.data.http.LastAction
import com.essensys.android.data.http.LanUserResponse
import com.essensys.android.data.http.LinkRequestBody
import com.essensys.android.data.http.LinkStatusResponse
import com.essensys.android.data.http.PortalSession
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.encodeToJsonElement

/** État de liaison armoire ↔ compte cloud (spec connection-modes « Liaison de l'armoire »). */
sealed interface LinkState {
    data object Linked : LinkState
    data object None : LinkState
    data class Requested(val status: String, val machineSerial: String?) : LinkState
}

/** Lectures de session : liaison, état gateway, dernière action (cloud) ; validité de session (LAN). */
class PortalRepository(
    private val api: ApiCaller,
    private val store: SessionStore,
    private val auth: AuthRepository,
) {
    private val baseUrl get() = store.state.value.baseUrl

    suspend fun linkState(): ApiResult<LinkState> = get("/api/portal/link-request/status", LinkStatusResponse.serializer()) { s ->
        when {
            s.portalAccess -> LinkState.Linked
            s.linkRequest != null -> LinkState.Requested(s.linkRequest.status ?: "pending", s.linkRequest.machineSerial)
            else -> LinkState.None
        }
    }

    suspend fun requestLink(machineSerial: String, message: String): ApiResult<Unit> {
        val body = EssensysJson.encodeToJsonElement(LinkRequestBody(machineSerial.trim(), message.trim()))
        return when (val r = api.send(baseUrl, "POST", "/api/portal/link-request", body)) {
            is ApiResult.Err -> r.also { auth.onApiError(it.error) }
            is ApiResult.Ok -> ApiResult.Ok(Unit)
        }
    }

    suspend fun gatewayOnline(): ApiResult<Boolean> =
        get("/api/portal/session", PortalSession.serializer()) { it.gateway?.online == true }

    suspend fun lastAction(): ApiResult<LastAction?> =
        get("/api/portal/history/latest", HistoryLatest.serializer()) { it.lastAction }

    /** LAN : la gateway n'expose ni état ni historique ; on vérifie seulement la session. */
    suspend fun lanSessionValid(): ApiResult<Boolean> =
        get("/api/user/me", LanUserResponse.serializer()) { it.user != null }

    private suspend fun <T, R> get(path: String, serializer: KSerializer<T>, map: (T) -> R): ApiResult<R> =
        when (val r = api.get(baseUrl, path)) {
            is ApiResult.Err -> r.also { auth.onApiError(it.error) }
            is ApiResult.Ok -> ApiResult.Ok(map(EssensysJson.decodeFromString(serializer, r.value.body)))
        }
}
