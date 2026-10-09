package com.essensys.android.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.essensys.android.data.auth.AuthRepository
import com.essensys.android.data.auth.LoginOutcome
import com.essensys.android.data.http.ApiResult
import com.essensys.android.data.http.LanTrust
import com.essensys.android.data.http.LastAction
import com.essensys.android.data.session.ConnectionMode
import com.essensys.android.data.session.Hosts
import com.essensys.android.data.session.LinkState
import com.essensys.android.data.session.PortalRepository
import com.essensys.android.data.session.SessionStore
import com.essensys.android.ui.theme.ThemePreference
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.security.cert.X509Certificate

/** Certificat LAN présenté, en attente de confirmation par l'utilisateur (design D4). */
data class PendingCertificate(val host: String, val fingerprint: String, val pem: String)

/** État de session vu par l'UI : connexion, liaison, gateway, dernière action (spec connection-modes). */
class SessionViewModel(
    private val store: SessionStore,
    private val auth: AuthRepository,
    private val portal: PortalRepository,
    private val probeCertificate: suspend (String) -> ApiResult<X509Certificate> = { LanTrust.probe(it) },
    private val sessionPeriodMs: Long = SESSION_PERIOD_MS,
    private val lastActionPeriodMs: Long = LAST_ACTION_PERIOD_MS,
) : ViewModel() {

    val session = store.state

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    private val _pendingCertificate = MutableStateFlow<PendingCertificate?>(null)
    val pendingCertificate: StateFlow<PendingCertificate?> = _pendingCertificate.asStateFlow()

    private val _link = MutableStateFlow<LinkState?>(null)
    val link: StateFlow<LinkState?> = _link.asStateFlow()
    private val _gatewayOnline = MutableStateFlow<Boolean?>(null)
    val gatewayOnline: StateFlow<Boolean?> = _gatewayOnline.asStateFlow()
    private val _lastAction = MutableStateFlow<LastAction?>(null)
    val lastAction: StateFlow<LastAction?> = _lastAction.asStateFlow()
    private val _info = MutableStateFlow<String?>(null)
    val info: StateFlow<String?> = _info.asStateFlow()

    fun setMode(mode: ConnectionMode) = viewModelScope.launch {
        if (mode != store.state.value.mode) {
            store.update { it.copy(mode = mode, token = null, lanCookie = null, passwordChangeRequired = false) }
            _link.value = null
            _error.value = null
        }
    }

    /** Refuse le HTTP en clair (spec « HTTPS uniquement »). */
    fun setLanHost(raw: String): Boolean {
        val host = raw.trim().trimEnd('/')
        if (host.startsWith("http://", ignoreCase = true)) {
            _error.value = "Seule une connexion sécurisée (https://) est possible."
            return false
        }
        val normalized = if (host.startsWith("https://", ignoreCase = true)) host else "https://$host"
        viewModelScope.launch {
            if (normalized != store.state.value.lanHost) {
                store.update { it.copy(lanHost = normalized, pinnedLanCertPem = null) }
            }
        }
        _error.value = null
        return true
    }

    fun login(email: String, password: String) = viewModelScope.launch {
        _error.value = null
        val state = store.state.value
        if (state.mode == ConnectionMode.LAN && state.pinnedLanCertPem == null) {
            _busy.value = true
            when (val probed = probeCertificate(state.lanHost)) {
                is ApiResult.Ok -> _pendingCertificate.value = PendingCertificate(
                    host = state.lanHost.substringAfter("://"),
                    fingerprint = LanTrust.sha256(probed.value),
                    pem = LanTrust.toPem(probed.value),
                )
                is ApiResult.Err -> _error.value = probed.error.userMessage
            }
            _busy.value = false
            return@launch
        }
        _busy.value = true
        when (val outcome = auth.login(email, password)) {
            is LoginOutcome.Failed -> _error.value = outcome.message
            LoginOutcome.LoggedIn, LoginOutcome.MustChangePassword -> refreshLink()
        }
        _busy.value = false
    }

    fun confirmCertificate(accept: Boolean) = viewModelScope.launch {
        val pending = _pendingCertificate.value ?: return@launch
        _pendingCertificate.value = null
        if (accept) {
            store.update { it.copy(pinnedLanCertPem = pending.pem) }
            _info.value = "Certificat de la gateway confirmé. Vous pouvez vous connecter."
        }
    }

    /** Oublie le certificat épinglé (changement légitime de CA, à confirmer de nouveau). */
    fun forgetLanCertificate() = viewModelScope.launch { store.update { it.copy(pinnedLanCertPem = null) } }

    fun changePassword(current: String, new: String) = viewModelScope.launch {
        if (new.length < 8) {
            _error.value = "Le mot de passe doit contenir au moins 8 caractères."
            return@launch
        }
        _busy.value = true
        val wasLan = store.state.value.mode == ConnectionMode.LAN
        when (val r = auth.changePassword(current, new)) {
            is ApiResult.Err -> _error.value = r.error.userMessage
            is ApiResult.Ok -> {
                _error.value = null
                _info.value = if (wasLan) "Mot de passe changé. La gateway a fermé vos sessions : reconnectez-vous." else "Mot de passe mis à jour."
            }
        }
        _busy.value = false
    }

    fun refreshLink() = viewModelScope.launch {
        if (store.state.value.mode == ConnectionMode.LAN) {
            _link.value = LinkState.Linked
            return@launch
        }
        _link.value = when (val r = portal.linkState()) {
            is ApiResult.Ok -> r.value
            is ApiResult.Err -> { _error.value = r.error.userMessage; null }
        }
    }

    fun requestLink(serial: String, message: String) = viewModelScope.launch {
        if (serial.isBlank()) { _error.value = "Le numéro de série est obligatoire."; return@launch }
        when (val r = portal.requestLink(serial, message)) {
            is ApiResult.Ok -> refreshLink()
            is ApiResult.Err -> _error.value = r.error.userMessage
        }
    }

    /**
     * Rafraîchissements au premier plan (design D6) : appelé dans `repeatOnLifecycle(STARTED)`.
     * Cloud : session 60 s, dernière action 2 s. LAN : vérification de session à l'ouverture seulement.
     */
    suspend fun pollWhileVisible() {
        if (store.state.value.mode == ConnectionMode.LAN) {
            portal.lanSessionValid()
            _gatewayOnline.value = true
            return
        }
        coroutineScope {
            launch {
                while (true) {
                    (portal.gatewayOnline() as? ApiResult.Ok)?.let { _gatewayOnline.value = it.value }
                    delay(sessionPeriodMs)
                }
            }
            launch {
                while (true) {
                    when (val r = portal.lastAction()) {
                        is ApiResult.Ok -> _lastAction.value = r.value
                        is ApiResult.Err -> Unit
                    }
                    delay(lastActionPeriodMs)
                }
            }
        }
    }

    fun setTheme(theme: ThemePreference) = viewModelScope.launch { store.update { it.copy(theme = theme) } }
    fun setTestMode(enabled: Boolean) = viewModelScope.launch { store.update { it.copy(testMode = enabled) } }
    fun logout() = viewModelScope.launch { auth.logout(); _link.value = null; _lastAction.value = null }
    fun clearMessages() { _error.value = null; _info.value = null }

    companion object {
        const val SESSION_PERIOD_MS = 60_000L
        const val LAST_ACTION_PERIOD_MS = 2_000L
        val CLOUD_LABEL = Hosts.CLOUD.substringAfter("://")
    }
}
