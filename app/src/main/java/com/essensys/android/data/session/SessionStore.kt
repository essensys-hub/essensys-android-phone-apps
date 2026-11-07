package com.essensys.android.data.session

import com.essensys.android.ui.theme.ThemePreference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class ConnectionMode { CLOUD, LAN }

/** Hôtes par défaut (spec connection-modes). L'hôte LAN est modifiable. */
object Hosts {
    const val CLOUD = "https://mon.essensys.fr"
    const val LAN_DEFAULT = "https://mon.essensys.local"
}

/** État persistant de l'app. Les secrets (jeton, cookie) sont chiffrés par l'implémentation persistante. */
data class SessionState(
    val mode: ConnectionMode = ConnectionMode.CLOUD,
    val lanHost: String = Hosts.LAN_DEFAULT,
    val token: String? = null,
    val lanCookie: String? = null,
    val passwordChangeRequired: Boolean = false,
    val theme: ThemePreference = ThemePreference.SYSTEM,
    val testMode: Boolean = false,
    /** Certificat épinglé de la gateway LAN (PEM), confirmé par l'utilisateur (design D4). */
    val pinnedLanCertPem: String? = null,
    /** Non modifiable par l'utilisateur ; surchargé uniquement par les tests (serveur simulé). */
    val cloudHost: String = Hosts.CLOUD,
    /** Session mémorisée de 30 jours (cloud) : échangée contre un jeton d'accès sans redemander le mot de passe. */
    val rememberToken: String? = null,
    /** Fin de la session mémorisée (epoch ms), telle que renvoyée par le serveur ; jamais prolongée par l'usage. */
    val rememberExpiresAtMs: Long? = null,
    /** La session mémorisée a pris fin côté serveur : l'écran de connexion l'explique. Non persisté. */
    val sessionEnded: Boolean = false,
    /**
     * Administrateur de l'armoire, décodé à la connexion (voir `AdminRole`). Faux par défaut : une session enregistrée
     * avant cette notion n'a pas de rôle et reste non administrateur jusqu'à la reconnexion.
     */
    val isAdmin: Boolean = false,
) {
    val baseUrl: String get() = if (mode == ConnectionMode.CLOUD) cloudHost else lanHost
    val isAuthenticated: Boolean get() = if (mode == ConnectionMode.CLOUD) token != null else lanCookie != null
}

interface SessionStore {
    val state: StateFlow<SessionState>
    suspend fun update(transform: (SessionState) -> SessionState)

    /**
     * Efface jeton, cookie, session mémorisée et verrou ; garde les préférences (mode, hôte, thème, CA épinglée)
     * et l'indicateur [SessionState.sessionEnded], que seule une nouvelle connexion ou une déconnexion remet à zéro.
     */
    suspend fun clearSession() = update {
        it.copy(
            token = null, lanCookie = null, passwordChangeRequired = false, rememberToken = null, rememberExpiresAtMs = null,
            isAdmin = false,
        )
    }
}

/** Implémentation mémoire : tests et previews. */
class InMemorySessionStore(initial: SessionState = SessionState()) : SessionStore {
    private val flow = MutableStateFlow(initial)
    override val state: StateFlow<SessionState> = flow.asStateFlow()
    override suspend fun update(transform: (SessionState) -> SessionState) = flow.update(transform)
}
