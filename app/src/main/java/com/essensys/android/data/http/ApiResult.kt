package com.essensys.android.data.http

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Résultat d'un appel API : succès typé ou erreur lisible par l'utilisateur. */
sealed interface ApiResult<out T> {
    data class Ok<T>(val value: T) : ApiResult<T>
    data class Err(val error: ApiError) : ApiResult<Nothing>
}

/**
 * Erreurs normalisées (design D5 bis). Les backends renvoient tantôt du JSON `{"error"|"message"}`,
 * tantôt du `text/plain` : [ErrorBody.parse] gère les deux.
 */
sealed interface ApiError {
    val userMessage: String

    data class Unauthorized(val code: String?, val text: String? = null) : ApiError {
        override val userMessage = when (code) {
            "temporary_password_expired" -> "Mot de passe temporaire expiré. Demandez-en un nouveau."
            else -> "Session expirée, reconnectez-vous."
        }
    }
    data class Forbidden(val code: String?, val text: String?) : ApiError {
        override val userMessage = when (code) {
            "account_forbidden", "account_disabled" -> "Ce compte est désactivé."
            else -> if (text?.contains("Portal access not approved") == true) "Accès portail non approuvé." else "Accès refusé."
        }
    }
    data object PasswordChangeRequired : ApiError {
        override val userMessage = "Vous devez changer votre mot de passe."
    }
    data object RateLimited : ApiError {
        override val userMessage = "Trop de commandes, patientez."
    }
    data class Http(val status: Int, val code: String?, val text: String?) : ApiError {
        override val userMessage = text?.takeIf { it.isNotBlank() } ?: "Erreur serveur ($status)."
    }
    data class Network(val host: String, val cause: String?) : ApiError {
        /** Échec TLS en LAN : certificat non confirmé ou différent de celui épinglé (design D4). */
        val isCertificateProblem: Boolean
            get() = cause?.let { Regex("certif|trust anchor|CertPath|non confirmé", RegexOption.IGNORE_CASE).containsMatchIn(it) } == true
        override val userMessage = if (isCertificateProblem) {
            "Le certificat de la gateway ($host) n'est pas celui confirmé. Connexion bloquée."
        } else "Connexion impossible à $host"
    }
}

object ErrorBody {
    private val json = Json { ignoreUnknownKeys = true }

    /** Retourne (code `error`, message lisible) depuis un corps JSON ou texte brut. */
    fun parse(body: String?): Pair<String?, String?> {
        val raw = body?.trim().orEmpty()
        if (raw.startsWith("{")) {
            val obj = runCatching { json.parseToJsonElement(raw) as? JsonObject }.getOrNull()
            if (obj != null) {
                val code = obj["error"]?.jsonPrimitive?.contentOrNull
                val message = obj["message"]?.jsonPrimitive?.contentOrNull
                return code to (message ?: code)
            }
        }
        return null to raw.ifEmpty { null }
    }

    fun toApiError(status: Int, body: String?): ApiError {
        val (code, text) = parse(body)
        return when {
            status == 401 -> ApiError.Unauthorized(code, text)
            status == 403 -> ApiError.Forbidden(code, text)
            status == 409 && code == "password_change_required" -> ApiError.PasswordChangeRequired
            status == 429 -> ApiError.RateLimited
            else -> ApiError.Http(status, code, text)
        }
    }
}
