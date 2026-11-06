package com.essensys.android.data.auth

import com.essensys.android.data.http.ApiCaller
import com.essensys.android.data.http.ApiResult
import com.essensys.android.data.http.EssensysJson
import com.essensys.android.data.http.GatewayHealth

/**
 * Rôle administrateur de l'armoire (spec light-timers-clients « Administrateur seulement »). Décodé à la connexion
 * et conservé dans `SessionState.isAdmin`. Ce n'est qu'un masquage d'interface : le serveur refuse (403) les autres.
 *
 * Règle :
 * - cloud : `user.role` = `admin_local` ou `admin_global` ; tout autre rôle, ou aucun, n'est pas administrateur ;
 * - LAN avec IAM : `user.role` = `lan_admin` ; `lan_user`, `lan_guest` ou tout autre rôle, non ;
 * - LAN sans IAM : la gateway ne renvoie aucun rôle et laisse tout utilisateur écrire. Le client le traite comme
 *   administrateur seulement si le login ne fournit aucun rôle ET si `GET /health` répond explicitement
 *   `lan_iam_enabled: false`. Réponse absente, illisible ou `true` : non administrateur.
 */
object AdminRole {
    private val CLOUD_ADMINS = setOf("admin_local", "admin_global")
    const val LAN_ADMIN = "lan_admin"

    fun isCloudAdmin(role: String?): Boolean = role?.trim() in CLOUD_ADMINS

    /** @param role rôle renvoyé par le login ; null s'il n'y en a pas. */
    suspend fun isLanAdmin(role: String?, api: ApiCaller, baseUrl: String): Boolean {
        if (role != null) return role.trim() == LAN_ADMIN
        return when (val result = api.get(baseUrl, "/health")) {
            is ApiResult.Err -> false
            is ApiResult.Ok -> runCatching { EssensysJson.decodeFromString(GatewayHealth.serializer(), result.value.body) }
                .getOrNull()?.lanIamEnabled == false
        }
    }
}
