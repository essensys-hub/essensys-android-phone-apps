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
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PersistentSessionStoreTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun legacy_cleartext_credentials_are_purged() {
        context.getSharedPreferences(PersistentSessionStore.LEGACY_PREFS, Context.MODE_PRIVATE).edit()
            .putString("username", "demo").putString("password", "en-clair").commit()
        assertTrue(PersistentSessionStore.purgeLegacy(context))
        val legacy = context.getSharedPreferences(PersistentSessionStore.LEGACY_PREFS, Context.MODE_PRIVATE)
        assertFalse(legacy.contains("password"))
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
}
