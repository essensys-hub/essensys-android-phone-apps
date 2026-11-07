package com.essensys.android.data.auth

import com.essensys.android.data.http.ApiCaller
import com.essensys.android.data.http.ApiError
import com.essensys.android.data.http.ApiResult
import com.essensys.android.data.http.CloudLoginResponse
import com.essensys.android.data.http.EssensysJson
import com.essensys.android.data.http.LanLoginResponse
import com.essensys.android.data.http.LoginRequest
import com.essensys.android.data.http.LogoutRequest
import com.essensys.android.data.http.PasswordChangeRequest
import com.essensys.android.data.http.SessionInterceptor
import com.essensys.android.data.session.ConnectionMode
import com.essensys.android.data.session.SessionStore
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement
import java.time.Instant

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

    /**
     * @param rememberMe case « Se souvenir de moi (30 jours) » : le serveur peut alors renvoyer une session mémorisée.
     * Si elle n'est pas renvoyée (serveur sans prise en charge), la connexion se fait comme case décochée.
     */
    suspend fun login(email: String, password: String, rememberMe: Boolean = false): LoginOutcome {
        val state = store.state.value
        val body = EssensysJson.encodeToJsonElement(LoginRequest(email.trim(), password, rememberMe.takeIf { it }))
        return when (val result = api.send(state.baseUrl, "POST", "/api/auth/login", body)) {
            is ApiResult.Err -> LoginOutcome.Failed(loginMessage(result.error))
            is ApiResult.Ok -> when (state.mode) {
                ConnectionMode.CLOUD -> {
                    val login = EssensysJson.decodeFromString(CloudLoginResponse.serializer(), result.value.body)
                    // Un mot de passe à changer révoque toute session mémorisée dès le changement : inutile de la garder.
                    val remember = login.rememberToken?.takeIf { rememberMe && it.isNotBlank() && !login.passwordChangeRequired }
                    store.update {
                        it.copy(
                            token = login.token, lanCookie = null, passwordChangeRequired = login.passwordChangeRequired,
                            rememberToken = remember, rememberExpiresAtMs = remember?.let { parseInstantMs(login.rememberExpiresAt) },
                            sessionEnded = false, isAdmin = AdminRole.isCloudAdmin(login.user?.role),
                        )
                    }
                    if (login.passwordChangeRequired) LoginOutcome.MustChangePassword else LoginOutcome.LoggedIn
                }
                ConnectionMode.LAN -> {
                    val cookie = extractLanCookie(result.value.setCookies)
                        ?: return LoginOutcome.Failed("La gateway n'a pas ouvert de session.")
                    val expiresAt = if (rememberMe) lanRememberExpiryMs(result.value.body) else null
                    val role = runCatching { EssensysJson.decodeFromString(LanLoginResponse.serializer(), result.value.body) }
                        .getOrNull()?.user?.role
                    val isAdmin = AdminRole.isLanAdmin(role, api, state.baseUrl)
                    store.update {
                        it.copy(
                            lanCookie = cookie, token = null, passwordChangeRequired = false,
                            rememberToken = null, rememberExpiresAtMs = expiresAt, sessionEnded = false, isAdmin = isAdmin,
                        )
                    }
                    LoginOutcome.LoggedIn
                }
            }
        }
    }

    /**
     * Cloud : même jeton conservé après succès, sauf s'il existait une session mémorisée. Le serveur les révoque
     * toutes à chaque changement de mot de passe, y compris celle de ce téléphone : la session est alors terminée,
     * comme la gateway le fait déjà en mode LAN.
     */
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
                if (state.mode == ConnectionMode.LAN || state.rememberToken != null) {
                    // La gateway détruit toutes les sessions de l'utilisateur, y compris la courante.
                    store.clearSession()
                } else {
                    store.update { it.copy(passwordChangeRequired = false) }
                }
                ApiResult.Ok(Unit)
            }
        }
    }

    /**
     * L'effacement local est immédiat et ne dépend pas du réseau : sans connexion, la déconnexion réussit quand même.
     * La révocation côté serveur (session mémorisée cloud, session de la gateway) part ensuite, au mieux et sans attente
     * longue ; la session mémorisée, de toute façon supprimée du téléphone, expire au plus tard au bout de 30 jours.
     */
    suspend fun logout() {
        val state = store.state.value
        store.clearSession()
        store.update { it.copy(sessionEnded = false) }
        var body: JsonElement? = null
        val headers = mutableMapOf<String, String>()
        when (state.mode) {
            // Cloud : JWT sans état, seul le jeton mémorisé est révoqué. LAN : la gateway supprime la session du cookie.
            ConnectionMode.CLOUD -> state.rememberToken?.let { body = EssensysJson.encodeToJsonElement(LogoutRequest(it)) }
            ConnectionMode.LAN -> state.lanCookie?.let { headers["Cookie"] = "${SessionInterceptor.LAN_COOKIE}=$it" }
        }
        api.sendOnce(state.baseUrl, "POST", "/api/auth/logout", body, headers, callTimeoutMs = LOGOUT_TIMEOUT_MS)
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
        private const val LOGOUT_TIMEOUT_MS = 3_000L

        /** « 2026-12-10T08:00:00Z » → epoch ms ; nul si absent ou illisible (la date n'est alors pas affichée). */
        internal fun parseInstantMs(raw: String?): Long? = raw?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }

        private fun lanRememberExpiryMs(body: String): Long? =
            runCatching { EssensysJson.decodeFromString(LanLoginResponse.serializer(), body).rememberExpiresAt }.getOrNull()
                .let(::parseInstantMs)

        fun extractLanCookie(setCookies: List<String>): String? = setCookies.firstNotNullOfOrNull { header ->
            header.split(';').first().trim()
                .takeIf { it.startsWith("${SessionInterceptor.LAN_COOKIE}=") }
                ?.substringAfter('=')
                ?.takeIf { it.isNotEmpty() }
        }
    }
}
