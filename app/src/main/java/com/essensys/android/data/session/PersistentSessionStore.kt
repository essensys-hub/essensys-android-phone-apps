package com.essensys.android.data.session

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.essensys.android.ui.theme.ThemePreference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val Context.essensysDataStore: DataStore<Preferences> by preferencesDataStore(name = "essensys_session")

/**
 * Session persistée (spec mobile-auth « Stockage sécurisé ») : préférences en clair, jeton, cookie et session mémorisée
 * chiffrés par [SecretCipher]. Le mot de passe n'est jamais stocké.
 */
class PersistentSessionStore private constructor(
    private val dataStore: DataStore<Preferences>,
    private val cipher: SecretCipher,
    initial: SessionState,
) : SessionStore {

    private val flow = MutableStateFlow(initial)
    private val mutex = Mutex()
    override val state: StateFlow<SessionState> = flow.asStateFlow()

    override suspend fun update(transform: (SessionState) -> SessionState) = mutex.withLock {
        val next = transform(flow.value)
        dataStore.edit { write(it, next) }
        flow.value = next
    }

    private fun write(prefs: MutablePreferences, s: SessionState) {
        prefs[MODE] = s.mode.name
        prefs[LAN_HOST] = s.lanHost
        prefs[THEME] = s.theme.name
        prefs[TEST_MODE] = s.testMode
        prefs[PASSWORD_CHANGE] = s.passwordChangeRequired
        prefs[IS_ADMIN] = s.isAdmin
        prefs.putOrRemove(TOKEN, s.token?.let(cipher::encrypt))
        prefs.putOrRemove(LAN_COOKIE, s.lanCookie?.let(cipher::encrypt))
        prefs.putOrRemove(REMEMBER_TOKEN, s.rememberToken?.let(cipher::encrypt))
        if (s.rememberExpiresAtMs == null) prefs.remove(REMEMBER_EXPIRES_AT) else prefs[REMEMBER_EXPIRES_AT] = s.rememberExpiresAtMs
        prefs.putOrRemove(PINNED_CA, s.pinnedLanCertPem)
        prefs.putOrRemove(PUSH_DEVICE_ID, s.pushDeviceId)
        prefs[DOORBELL_NOTIFY] = s.doorbellNotify
    }

    private fun MutablePreferences.putOrRemove(key: Preferences.Key<String>, value: String?) {
        if (value == null) remove(key) else this[key] = value
    }

    companion object {
        /** Préférences v1.0.0, mot de passe compris, en clair : à supprimer (spec « Migration depuis la v1.0.0 »). */
        const val LEGACY_PREFS = "EssensysPrefs"

        private val MODE = stringPreferencesKey("mode")
        private val LAN_HOST = stringPreferencesKey("lan_host")
        private val THEME = stringPreferencesKey("theme")
        private val TEST_MODE = booleanPreferencesKey("test_mode")
        private val PASSWORD_CHANGE = booleanPreferencesKey("password_change_required")
        private val IS_ADMIN = booleanPreferencesKey("is_admin")
        private val TOKEN = stringPreferencesKey("token_enc")
        private val LAN_COOKIE = stringPreferencesKey("lan_cookie_enc")
        private val REMEMBER_TOKEN = stringPreferencesKey("remember_token_enc")
        private val REMEMBER_EXPIRES_AT = longPreferencesKey("remember_expires_at_ms")
        private val PINNED_CA = stringPreferencesKey("pinned_lan_cert_pem")
        private val PUSH_DEVICE_ID = stringPreferencesKey("push_device_id")
        private val DOORBELL_NOTIFY = booleanPreferencesKey("doorbell_notify")

        /** Purge le stockage v1.0.0 ; retourne true si des identifiants en clair ont été supprimés. */
        fun purgeLegacy(context: Context): Boolean {
            val legacy = context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
            val hadData = legacy.all.isNotEmpty()
            legacy.edit().clear().commit()
            context.deleteSharedPreferences(LEGACY_PREFS)
            return hadData
        }

        suspend fun open(context: Context, cipher: SecretCipher = KeystoreSecretCipher()): PersistentSessionStore {
            val dataStore = context.applicationContext.essensysDataStore
            val prefs = dataStore.data.first()
            val token = prefs[TOKEN]?.let(cipher::decrypt)
            val lanCookie = prefs[LAN_COOKIE]?.let(cipher::decrypt)
            val rememberToken = prefs[REMEMBER_TOKEN]?.let(cipher::decrypt)
            val initial = SessionState(
                mode = prefs[MODE]?.let { runCatching { ConnectionMode.valueOf(it) }.getOrNull() } ?: ConnectionMode.CLOUD,
                lanHost = prefs[LAN_HOST] ?: Hosts.LAN_DEFAULT,
                theme = prefs[THEME]?.let { runCatching { ThemePreference.valueOf(it) }.getOrNull() } ?: ThemePreference.SYSTEM,
                testMode = prefs[TEST_MODE] ?: false,
                passwordChangeRequired = prefs[PASSWORD_CHANGE] ?: false,
                // Absent d'une session enregistrée avant la gestion du rôle : non administrateur jusqu'à la reconnexion.
                isAdmin = (prefs[IS_ADMIN] ?: false) && (token != null || lanCookie != null),
                token = token,
                lanCookie = lanCookie,
                rememberToken = rememberToken,
                // Une date sans session derrière (secret illisible après un changement de clé) ne doit rien afficher.
                rememberExpiresAtMs = prefs[REMEMBER_EXPIRES_AT]?.takeIf { rememberToken != null || lanCookie != null },
                pinnedLanCertPem = prefs[PINNED_CA],
                pushDeviceId = prefs[PUSH_DEVICE_ID],
                doorbellNotify = prefs[DOORBELL_NOTIFY] ?: false,
            )
            return PersistentSessionStore(dataStore, cipher, initial)
        }
    }
}
