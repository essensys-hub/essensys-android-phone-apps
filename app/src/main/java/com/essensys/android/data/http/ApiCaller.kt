package com.essensys.android.data.http

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

/** Exécute une requête JSON et convertit statut / corps en [ApiResult]. Aucun repli « démo » (spec connection-modes). */
class ApiCaller(private val clientProvider: () -> OkHttpClient) {

    private val jsonType = "application/json; charset=utf-8".toMediaType()

    suspend fun get(baseUrl: String, path: String): ApiResult<RawResponse> =
        execute(baseUrl, Request.Builder().url(url(baseUrl, path)).get().build())

    suspend fun send(baseUrl: String, method: String, path: String, body: JsonElement?): ApiResult<RawResponse> {
        val requestBody = (body?.toString() ?: "").toRequestBody(jsonType)
        return execute(baseUrl, Request.Builder().url(url(baseUrl, path)).method(method, requestBody).build())
    }

    private fun url(baseUrl: String, path: String) = (baseUrl.trimEnd('/') + path).toHttpUrl()

    private suspend fun execute(baseUrl: String, request: Request): ApiResult<RawResponse> = withContext(Dispatchers.IO) {
        try {
            clientProvider().newCall(request).execute().use { response -> toResult(response) }
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
}

data class RawResponse(val status: Int, val body: String, val setCookies: List<String>)
