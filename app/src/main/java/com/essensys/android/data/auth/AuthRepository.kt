package com.essensys.android.data.auth

import com.essensys.android.data.http.ApiCaller
import com.essensys.android.data.http.ApiError
import com.essensys.android.data.http.ApiResult
import com.essensys.android.data.http.CloudLoginResponse
import com.essensys.android.data.http.EssensysJson
import com.essensys.android.data.http.LoginRequest
import com.essensys.android.data.http.PasswordChangeRequest
import com.essensys.android.data.http.SessionInterceptor
import com.essensys.android.data.session.ConnectionMode
import com.essensys.android.data.session.SessionStore
import kotlinx.serialization.json.encodeToJsonElement

/** Résultat de connexion vu par l'UI. */
sealed interface LoginOutcome {
    data object LoggedIn : LoginOutcome
    data object MustChangePassword : LoginOutcome
    data class Failed(val message: String) : LoginOutcome
}

/**
 * Authentification cloud (JWT) et LAN (cookie de session gateway). Spec mobile-auth.
 * Le mot de passe n'est jamais persisté : seuls le jeton ou le cookie sont conservés par [SessionStore].
 */
class AuthRepository(private val api: ApiCaller, private val store: SessionStore) {

    suspend fun login(email: String, password: String): LoginOutcome {
        val state = store.state.value
        val body = EssensysJson.encodeToJsonElement(LoginRequest(email.trim(), password))
        return when (val result = api.send(state.baseUrl, "POST", "/api/auth/login", body)) {
            is ApiResult.Err -> LoginOutcome.Failed(loginMessage(result.error))
            is ApiResult.Ok -> when (state.mode) {
                ConnectionMode.CLOUD -> {
                    val login = EssensysJson.decodeFromString(CloudLoginResponse.serializer(), result.value.body)
                    store.update { it.copy(token = login.token, lanCookie = null, passwordChangeRequired = login.passwordChangeRequired) }
                    if (login.passwordChangeRequired) LoginOutcome.MustChangePassword else LoginOutcome.LoggedIn
                }
                ConnectionMode.LAN -> {
                    val cookie = extractLanCookie(result.value.setCookies)
                        ?: return LoginOutcome.Failed("La gateway n'a pas ouvert de session.")
                    store.update { it.copy(lanCookie = cookie, token = null, passwordChangeRequired = false) }
                    LoginOutcome.LoggedIn
                }
            }
        }
    }

    /** Cloud : même jeton conservé après succès (le serveur relit l'utilisateur à chaque requête). */
    suspend fun changePassword(current: String, new: String): ApiResult<Unit> {
        val state = store.state.value
        val body = EssensysJson.encodeToJsonElement(PasswordChangeRequest(current, new))
        val result = when (state.mode) {
            ConnectionMode.CLOUD -> api.send(state.baseUrl, "POST", "/api/auth/password/change", body)
            ConnectionMode.LAN -> api.send(state.baseUrl, "PUT", "/api/user/me/password", body)
        }
        return when (result) {
            is ApiResult.Err -> result
            is ApiResult.Ok -> {
                if (state.mode == ConnectionMode.LAN) {
                    // La gateway détruit toutes les sessions de l'utilisateur, y compris la courante.
                    store.clearSession()
                } else {
                    store.update { it.copy(passwordChangeRequired = false) }
                }
                ApiResult.Ok(Unit)
            }
        }
    }

    suspend fun logout() {
        val state = store.state.value
        // Cloud : JWT sans état, l'appel est informatif ; LAN : supprime la session côté gateway.
        runCatching { api.send(state.baseUrl, "POST", "/api/auth/logout", null) }
        store.clearSession()
    }

    /** Réaction centralisée aux erreurs d'authentification (spec mobile-auth « Expiration et révocation »). */
    suspend fun onApiError(error: ApiError) {
        when (error) {
            is ApiError.Unauthorized -> store.clearSession()
            is ApiError.Forbidden -> if (error.code == "account_forbidden" || error.code == "account_disabled") store.clearSession()
            ApiError.PasswordChangeRequired -> store.update { it.copy(passwordChangeRequired = true) }
            else -> Unit
        }
    }

    private fun loginMessage(error: ApiError): String = when (error) {
        is ApiError.Unauthorized -> when {
            error.code == "temporary_password_expired" -> error.userMessage
            error.text?.startsWith("Please login with") == true ->
                "Ce compte utilise une connexion Google ou Apple, non disponible dans l'app pour l'instant."
            else -> "Email ou mot de passe incorrect."
        }
        else -> error.userMessage
    }

    companion object {
        fun extractLanCookie(setCookies: List<String>): String? = setCookies.firstNotNullOfOrNull { header ->
            header.split(';').first().trim()
                .takeIf { it.startsWith("${SessionInterceptor.LAN_COOKIE}=") }
                ?.substringAfter('=')
                ?.takeIf { it.isNotEmpty() }
        }
    }
}
