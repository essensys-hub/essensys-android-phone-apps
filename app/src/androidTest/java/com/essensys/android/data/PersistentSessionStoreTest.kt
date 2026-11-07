package com.essensys.android.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.essensys.android.data.session.ConnectionMode
import com.essensys.android.data.session.KeystoreSecretCipher
import com.essensys.android.data.session.PersistentSessionStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PersistentSessionStoreTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private companion object {
        // Clés utilisées par la v1.0.0 dans EssensysPrefs.
        const val LEGACY_USER_KEY = "username"
        const val LEGACY_SECRET_KEY = "password"
    }

    @Test
    fun legacy_cleartext_credentials_are_purged() {
        // Valeurs générées à l'exécution : aucune paire identifiant/mot de passe littérale dans le source.
        context.getSharedPreferences(PersistentSessionStore.LEGACY_PREFS, Context.MODE_PRIVATE).edit()
            .putString(LEGACY_USER_KEY, UUID.randomUUID().toString())
            .putString(LEGACY_SECRET_KEY, UUID.randomUUID().toString())
            .commit()
        assertTrue(PersistentSessionStore.purgeLegacy(context))
        val legacy = context.getSharedPreferences(PersistentSessionStore.LEGACY_PREFS, Context.MODE_PRIVATE)
        assertFalse(legacy.contains(LEGACY_SECRET_KEY))
        assertFalse(PersistentSessionStore.purgeLegacy(context))
    }

    @Test
    fun token_survives_reopen_and_is_encrypted_at_rest() = runBlocking {
        val store = PersistentSessionStore.open(context)
        store.update { it.copy(mode = ConnectionMode.CLOUD, token = "jwt-secret-value") }
        val reopened = PersistentSessionStore.open(context)
        assertEquals("jwt-secret-value", reopened.state.value.token)

        val cipher = KeystoreSecretCipher()
        val sealed = cipher.encrypt("jwt-secret-value")
        assertNotEquals("jwt-secret-value", sealed)
        assertFalse(sealed.contains("jwt-secret-value"))
        assertEquals("jwt-secret-value", cipher.decrypt(sealed))

        reopened.clearSession()
        assertEquals(null, PersistentSessionStore.open(context).state.value.token)
    }

    @Test
    fun remember_session_survives_reopen_is_encrypted_and_cleared_with_the_session() = runBlocking {
        val secret = "remember-SECRET-0123456789"
        val store = PersistentSessionStore.open(context)
        store.update {
            it.copy(mode = ConnectionMode.CLOUD, token = "jwt-secret-value", rememberToken = secret, rememberExpiresAtMs = 1_796_889_600_000L)
        }
        val reopened = PersistentSessionStore.open(context)
        assertEquals(secret, reopened.state.value.rememberToken)
        assertEquals(1_796_889_600_000L, reopened.state.value.rememberExpiresAtMs)
        // Jamais écrit en clair : le fichier de préférences ne contient ni le jeton mémorisé ni le jeton d'accès.
        val prefsFile = File(context.filesDir, "datastore/essensys_session.preferences_pb")
        assertTrue(prefsFile.exists())
        val raw = prefsFile.readBytes().toString(Charsets.ISO_8859_1)
        assertFalse(raw.contains(secret))
        assertFalse(raw.contains("jwt-secret-value"))

        reopened.clearSession()
        val cleared = PersistentSessionStore.open(context).state.value
        assertEquals(null, cleared.rememberToken)
        assertEquals(null, cleared.rememberExpiresAtMs)
    }

    @Test
    fun the_session_ended_notice_is_not_persisted() = runBlocking {
        val store = PersistentSessionStore.open(context)
        store.update { it.copy(sessionEnded = true) }
        assertFalse(PersistentSessionStore.open(context).state.value.sessionEnded)
        store.clearSession()
    }
}
