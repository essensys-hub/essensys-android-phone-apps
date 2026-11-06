package com.essensys.android.data.http

import com.essensys.android.data.auth.Renewal
import com.essensys.android.data.auth.SessionRenewer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Exécute une requête JSON et convertit statut / corps en [ApiResult]. Aucun repli « démo » (spec connection-modes). */
class ApiCaller(private val clientProvider: () -> OkHttpClient) {

    private val jsonType = "application/json; charset=utf-8".toMediaType()

    /**
     * Renouvelle l'accès cloud expiré à partir de la session mémorisée (spec remember-me). Nul : aucun renouvellement,
     * comme dans les tests qui n'en ont pas besoin.
     */
    var renewer: SessionRenewer? = null

    suspend fun get(baseUrl: String, path: String): ApiResult<RawResponse> =
        withRenewal(path) { execute(baseUrl, Request.Builder().url(url(baseUrl, path)).get().build()) }

    suspend fun send(baseUrl: String, method: String, path: String, body: JsonElement?): ApiResult<RawResponse> {
        val request = Request.Builder().url(url(baseUrl, path)).method(method, requestBody(body)).build()
        return withRenewal(path) { execute(baseUrl, request) }
    }

    /** Comme [send], sans renouvellement après un 401, et avec des en-têtes explicites (déconnexion, renouvellement). */
    suspend fun sendOnce(
        baseUrl: String,
        method: String,
        path: String,
        body: JsonElement?,
        headers: Map<String, String> = emptyMap(),
        /** Durée maximale de l'appel entier ; nul : celle du client. */
        callTimeoutMs: Long? = null,
    ): ApiResult<RawResponse> {
        val builder = Request.Builder().url(url(baseUrl, path)).method(method, requestBody(body))
        headers.forEach { (name, value) -> builder.header(name, value) }
        return execute(baseUrl, builder.build(), callTimeoutMs)
    }

    private fun requestBody(body: JsonElement?) = (body?.toString() ?: "").toRequestBody(jsonType)

    private fun url(baseUrl: String, path: String) = (baseUrl.trimEnd('/') + path).toHttpUrl()

    /**
     * Un seul renouvellement, un seul rejeu : un 401 sur une route hors `/api/auth/` (le 401 d'un mot de passe actuel
     * erroné n'en est pas un) déclenche l'échange de la session mémorisée, puis la requête repart une fois.
     */
    private suspend fun withRenewal(path: String, call: suspend () -> ApiResult<RawResponse>): ApiResult<RawResponse> {
        val renewer = renewer
        if (renewer == null || path.startsWith(AUTH_PREFIX)) return call()
        val usedToken = renewer.accessToken()
        val first = call()
        if (first !is ApiResult.Err || first.error !is ApiError.Unauthorized) return first
        return when (val renewal = renewer.renew(usedToken)) {
            Renewal.Renewed -> call()
            Renewal.NotApplicable, Renewal.Ended -> first
            // Panne passagère du renouvellement : la session mémorisée reste, ce n'est pas un refus d'authentification.
            is Renewal.Unavailable -> ApiResult.Err(renewal.error)
        }
    }

    private suspend fun execute(baseUrl: String, request: Request, callTimeoutMs: Long? = null): ApiResult<RawResponse> = withContext(Dispatchers.IO) {
        try {
            val client = clientProvider().let { c -> if (callTimeoutMs == null) c else c.newBuilder().callTimeout(callTimeoutMs, TimeUnit.MILLISECONDS).build() }
            client.newCall(request).execute().use { response -> toResult(response) }
        } catch (e: ArmoireMutationBlocked) {
            throw e
        } catch (e: IOException) {
            ApiResult.Err(ApiError.Network(baseUrl.substringAfter("://").trimEnd('/'), e.message))
        }
    }

    private fun toResult(response: Response): ApiResult<RawResponse> {
        val body = response.body.string()
        return if (response.isSuccessful) {
            ApiResult.Ok(RawResponse(response.code, body, response.headers("Set-Cookie")))
        } else {
            ApiResult.Err(ErrorBody.toApiError(response.code, body))
        }
    }

    private companion object {
        const val AUTH_PREFIX = "/api/auth/"
    }
}

data class RawResponse(val status: Int, val body: String, val setCookies: List<String>)
