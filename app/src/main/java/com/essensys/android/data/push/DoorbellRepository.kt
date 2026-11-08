package com.essensys.android.data.push

import com.essensys.android.BuildConfig
import com.essensys.android.data.http.ApiResult
import com.essensys.android.data.session.ConnectionMode
import com.essensys.android.data.session.PortalRepository
import com.essensys.android.data.session.SessionStore

/** Résultat d'un changement de l'interrupteur « Sonnette ». */
sealed interface DoorbellOutcome {
    data class Done(val enabled: Boolean) : DoorbellOutcome
    /** Pas de jeton : Firebase non configuré dans ce build. */
    data object Unavailable : DoorbellOutcome
    data class Failed(val message: String) : DoorbellOutcome
}

/** Option « Sonnette » : enregistrement de l'appareil et choix `doorbell_notify` (contrat cloud figé). */
class DoorbellRepository(
    private val portal: PortalRepository,
    private val store: SessionStore,
    private val tokens: PushTokenSource,
    private val appVersion: String = BuildConfig.VERSION_NAME,
) {
    /** Relit l'état à l'ouverture : l'appareil est activé si le cloud le connaît avec `doorbell_notify=true`. */
    suspend fun refresh(): Boolean? = when (val r = portal.doorbellPreferences()) {
        is ApiResult.Err -> null
        is ApiResult.Ok -> {
            val id = store.state.value.pushDeviceId
            val enabled = r.value.devices.firstOrNull { it.id == id }?.doorbellNotify ?: false
            if (id != null && r.value.devices.none { it.id == id }) store.update { it.copy(pushDeviceId = null, doorbellNotify = false) }
            else if (id != null) store.update { it.copy(doorbellNotify = enabled) }
            enabled
        }
    }

    suspend fun set(enabled: Boolean): DoorbellOutcome {
        val token = tokens.token() ?: return DoorbellOutcome.Unavailable
        return when (val r = portal.registerPushDevice(token, appVersion, enabled)) {
            is ApiResult.Ok -> DoorbellOutcome.Done(r.value.doorbellNotify)
            is ApiResult.Err -> DoorbellOutcome.Failed(r.error.userMessage)
        }
    }

    suspend fun onTokenRefreshed(token: String) {
        val s = store.state.value
        if (s.mode != ConnectionMode.CLOUD || s.token == null || s.pushDeviceId == null) return
        portal.registerPushDevice(token, appVersion, s.doorbellNotify)
    }
}
