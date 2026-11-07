package com.essensys.android.data.auth

import com.essensys.android.data.http.ApiCaller
import com.essensys.android.data.http.ApiError
import com.essensys.android.data.http.ApiResult
import com.essensys.android.data.http.CloudLoginResponse
import com.essensys.android.data.http.EssensysJson
import com.essensys.android.data.http.RememberRefreshRequest
import com.essensys.android.data.session.ConnectionMode
import com.essensys.android.data.session.SessionStore
import com.essensys.android.data.support.AppLog
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.encodeToJsonElement

/** Issue d'une demande de renouvellement de l'accès cloud. */
sealed interface Renewal {
    /** Un nouvel accès est en place : la requête peut être rejouée. */
    data object Renewed : Renewal

    /** Pas de session mémorisée à échanger (mode LAN, case décochée) : le 401 d'origine reste valable. */
    data object NotApplicable : Renewal

    /** Session mémorisée terminée (expirée, révoquée, compte désactivé) : la session locale est effacée. */
    data object Ended : Renewal

    /** Panne passagère (réseau, limitation, serveur) : la session mémorisée est conservée. */
    data class Unavailable(val error: ApiError) : Renewal
}

/**
 * Échange la session mémorisée contre un jeton d'accès de 24 h (spec remember-me « Renouvellement transparent »).
 * Un seul échange à la fois : les requêtes qui échouent ensemble attendent le même résultat.
 * Ne modifie jamais [com.essensys.android.data.session.SessionState.rememberExpiresAtMs] : les 30 jours sont fixes.
 */
class SessionRenewer(
    private val api: ApiCaller,
    private val store: SessionStore,
    private val log: AppLog? = null,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()

    fun accessToken(): String? = store.state.value.token

    /** @param staleToken jeton avec lequel la requête refusée est partie. */
    suspend fun renew(staleToken: String?): Renewal = mutex.withLock {
        val state = store.state.value
        val remember = state.rememberToken
        if (state.mode != ConnectionMode.CLOUD || remember == null) return Renewal.NotApplicable
        // Une requête concurrente a déjà renouvelé (ou mis fin à) la session pendant l'attente du verrou.
        if (state.token != staleToken) return if (state.token != null) Renewal.Renewed else Renewal.Ended
        // L'horloge locale n'évite qu'un appel inutile : l'autorité reste le serveur.
        state.rememberExpiresAtMs?.let { if (clock() >= it) return end() }

        val body = EssensysJson.encodeToJsonElement(RememberRefreshRequest(remember))
        when (val result = api.sendOnce(state.baseUrl, "POST", REFRESH_PATH, body)) {
            is ApiResult.Ok -> {
                val login = runCatching { EssensysJson.decodeFromString(CloudLoginResponse.serializer(), result.value.body) }.getOrNull()
                    ?: return Renewal.Unavailable(ApiError.Http(result.value.status, null, null))
                // Le rôle n'est relu que s'il est renvoyé : sinon celui de la connexion est conservé.
                store.update {
                    it.copy(
                        token = login.token, passwordChangeRequired = login.passwordChangeRequired,
                        isAdmin = login.user?.role?.let(AdminRole::isCloudAdmin) ?: it.isAdmin,
                    )
                }
                log?.event("session", "Accès renouvelé")
                Renewal.Renewed
            }
            is ApiResult.Err -> when (result.error) {
                is ApiError.Unauthorized, is ApiError.Forbidden -> end()
                else -> Renewal.Unavailable(result.error)
            }
        }
    }

    private suspend fun end(): Renewal {
        store.update {
            it.copy(
                token = null, rememberToken = null, rememberExpiresAtMs = null, passwordChangeRequired = false, sessionEnded = true,
                isAdmin = false,
            )
        }
        log?.event("session", "Session mémorisée terminée")
        return Renewal.Ended
    }

    companion object {
        const val REFRESH_PATH = "/api/auth/remember/refresh"
    }
}
