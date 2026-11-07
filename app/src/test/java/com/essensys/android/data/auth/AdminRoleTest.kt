package com.essensys.android.data.auth

import com.essensys.android.data.session.ConnectionMode
import com.essensys.android.support.TestServer
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Rôle administrateur décodé à la connexion (spec light-timers-clients « Administrateur seulement »). */
class AdminRoleTest {

    private val lanCookie = "Set-Cookie" to "essensys_lan_session=abc123; Path=/; HttpOnly; Max-Age=604800"

    private fun cloudLogin(role: String?) =
        """{"token":"jwt-x","user":{"id":1,"email":"a@b.c"${role?.let { ""","role":"$it"""" } ?: ""}},"password_change_required":false}"""

    private suspend fun cloudIsAdmin(body: String): Boolean = TestServer().use { t ->
        t.enqueue(200, body)
        assertEquals(LoginOutcome.LoggedIn, t.container.auth.login("a@b.c", "secret123"))
        t.store.state.value.isAdmin
    }

    @Test
    fun cloud_admin_local_and_admin_global_are_admins() = runTest {
        assertTrue(cloudIsAdmin(cloudLogin("admin_local")))
        assertTrue(cloudIsAdmin(cloudLogin("admin_global")))
    }

    @Test
    fun cloud_simple_user_or_missing_role_is_not_admin() = runTest {
        assertFalse(cloudIsAdmin(cloudLogin("guest_local")))
        assertFalse(cloudIsAdmin(cloudLogin("user")))
        assertFalse(cloudIsAdmin(cloudLogin(null)))
        assertFalse(cloudIsAdmin("""{"token":"jwt-x"}"""))
    }

    @Test
    fun lan_admin_is_admin_without_asking_health() = runTest {
        TestServer(mode = ConnectionMode.LAN).use { t ->
            t.enqueue(200, t.fixture("lan_login_ok.json"), lanCookie)
            assertEquals(LoginOutcome.LoggedIn, t.container.auth.login("admin@essensys.local", "secret123"))
            assertTrue(t.store.state.value.isAdmin)
            assertEquals(1, t.server.requestCount)
        }
    }

    @Test
    fun lan_other_roles_are_not_admin() = runTest {
        for (role in listOf("lan_user", "lan_guest", "autre")) {
            TestServer(mode = ConnectionMode.LAN).use { t ->
                t.enqueue(200, """{"user":{"id":2,"role":"$role"}}""", lanCookie)
                assertEquals(LoginOutcome.LoggedIn, t.container.auth.login("u@essensys.local", "secret123"))
                assertFalse(role, t.store.state.value.isAdmin)
                assertEquals(1, t.server.requestCount) // un rôle présent suffit : pas de /health
            }
        }
    }

    @Test
    fun lan_without_role_is_admin_only_when_health_says_iam_is_off() = runTest {
        TestServer(mode = ConnectionMode.LAN).use { t ->
            t.enqueue(200, """{"user":{"id":1,"email":"x"}}""", lanCookie)
            t.enqueue(200, """{"status":"ok","lan_iam_enabled":false,"auth_ready":true}""")
            t.container.auth.login("x", "secret123")
            assertTrue(t.store.state.value.isAdmin)
            t.server.takeRequest()
            assertEquals("/health", t.server.takeRequest().url.encodedPath)
        }
    }

    @Test
    fun lan_without_role_is_not_admin_when_iam_is_on_or_health_is_unclear() = runTest {
        val healths = listOf(
            200 to """{"status":"ok","lan_iam_enabled":true}""",
            200 to """{"status":"ok"}""", // champ absent
            200 to "pas du json",
            500 to "erreur",
        )
        for ((code, body) in healths) {
            TestServer(mode = ConnectionMode.LAN).use { t ->
                t.enqueue(200, """{"user":{"id":1}}""", lanCookie)
                t.enqueue(code, body)
                assertEquals(LoginOutcome.LoggedIn, t.container.auth.login("x", "secret123"))
                assertFalse(body, t.store.state.value.isAdmin)
            }
        }
    }

    @Test
    fun logout_and_session_clear_drop_the_admin_flag() = runTest {
        TestServer().use { t ->
            t.enqueue(200, cloudLogin("admin_local"))
            t.container.auth.login("a@b.c", "secret123")
            assertTrue(t.store.state.value.isAdmin)
            t.enqueue(200)
            t.container.auth.logout()
            assertFalse(t.store.state.value.isAdmin)
        }
    }

    @Test
    fun session_saved_before_roles_exist_is_not_admin() {
        // Une session déjà persistée n'a pas de rôle : valeur par défaut, jusqu'à la reconnexion.
        assertFalse(com.essensys.android.data.session.SessionState(token = "jwt-old").isAdmin)
    }
}
