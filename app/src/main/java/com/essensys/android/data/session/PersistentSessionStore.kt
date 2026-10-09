package com.essensys.android.data.session

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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
 * Session persistée (spec mobile-auth « Stockage sécurisé ») : préférences en clair, jeton et cookie
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
        prefs.putOrRemove(TOKEN, s.token?.let(cipher::encrypt))
        prefs.putOrRemove(LAN_COOKIE, s.lanCookie?.let(cipher::encrypt))
        prefs.putOrRemove(PINNED_CA, s.pinnedLanCertPem)
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
        private val TOKEN = stringPreferencesKey("token_enc")
        private val LAN_COOKIE = stringPreferencesKey("lan_cookie_enc")
        private val PINNED_CA = stringPreferencesKey("pinned_lan_cert_pem")

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
            val initial = SessionState(
                mode = prefs[MODE]?.let { runCatching { ConnectionMode.valueOf(it) }.getOrNull() } ?: ConnectionMode.CLOUD,
                lanHost = prefs[LAN_HOST] ?: Hosts.LAN_DEFAULT,
                theme = prefs[THEME]?.let { runCatching { ThemePreference.valueOf(it) }.getOrNull() } ?: ThemePreference.SYSTEM,
                testMode = prefs[TEST_MODE] ?: false,
                passwordChangeRequired = prefs[PASSWORD_CHANGE] ?: false,
                token = prefs[TOKEN]?.let(cipher::decrypt),
                lanCookie = prefs[LAN_COOKIE]?.let(cipher::decrypt),
                pinnedLanCertPem = prefs[PINNED_CA],
            )
            return PersistentSessionStore(dataStore, cipher, initial)
        }
    }
}
