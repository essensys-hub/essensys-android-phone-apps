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
    val pinnedLanCaSha256: String? = null,
    /** Non modifiable par l'utilisateur ; surchargé uniquement par les tests (serveur simulé). */
    val cloudHost: String = Hosts.CLOUD,
) {
    val baseUrl: String get() = if (mode == ConnectionMode.CLOUD) cloudHost else lanHost
    val isAuthenticated: Boolean get() = if (mode == ConnectionMode.CLOUD) token != null else lanCookie != null
}

interface SessionStore {
    val state: StateFlow<SessionState>
    suspend fun update(transform: (SessionState) -> SessionState)

    /** Efface jeton, cookie et verrou ; garde les préférences (mode, hôte, thème, CA épinglée). */
    suspend fun clearSession() = update { it.copy(token = null, lanCookie = null, passwordChangeRequired = false) }
}

/** Implémentation mémoire : tests et previews. */
class InMemorySessionStore(initial: SessionState = SessionState()) : SessionStore {
    private val flow = MutableStateFlow(initial)
    override val state: StateFlow<SessionState> = flow.asStateFlow()
    override suspend fun update(transform: (SessionState) -> SessionState) = flow.update(transform)
}
