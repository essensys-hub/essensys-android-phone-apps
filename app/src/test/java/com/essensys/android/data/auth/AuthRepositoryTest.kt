package com.essensys.android.data.auth

import com.essensys.android.data.http.ApiResult
import com.essensys.android.data.session.ConnectionMode
import com.essensys.android.support.TestServer
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class AuthRepositoryTest {

    @Test
    fun cloud_login_stores_jwt_and_shows_home() = runTest {
        TestServer().use { t ->
            t.enqueue(200, t.fixture("cloud_login_ok.json"))
            val outcome = t.container.auth.login("demo@essensys.fr", "secret123")
            assertEquals(LoginOutcome.LoggedIn, outcome)
            assertEquals("jwt-demo", t.store.state.value.token)
            val request = t.server.takeRequest()
            assertEquals("/api/auth/login", request.url.encodedPath)
            assertTrue(request.body!!.utf8().contains("\"email\":\"demo@essensys.fr\""))
        }
    }

    @Test
    fun invalid_credentials_show_generic_message() = runTest {
        TestServer().use { t ->
            t.enqueue(401, "Invalid credentials\n")
            val outcome = t.container.auth.login("demo@essensys.fr", "bad")
            assertEquals(LoginOutcome.Failed("Email ou mot de passe incorrect."), outcome)
            assertNull(t.store.state.value.token)
        }
    }

    // NR: NR-android-2 essensys-hub/essensys-android-phone-apps#2
    @Test
    fun unauthorized_response_clears_session_and_shows_login_NR_android_2() = runTest {
        TestServer(token = "expired-jwt").use { t ->
            t.enqueue(401, "Unauthorized\n")
            val result = t.container.portal.gatewayOnline()
            assertTrue(result is ApiResult.Err)
            assertNull(t.store.state.value.token)
            assertFalse(t.store.state.value.isAuthenticated)
            assertEquals("Bearer expired-jwt", t.server.takeRequest().headers["Authorization"])
        }
    }

    // NR: NR-android-3 essensys-hub/essensys-android-phone-apps#2
    @Test
    fun password_change_required_blocks_app_until_changed_NR_android_3() = runTest {
        TestServer().use { t ->
            // Mot de passe temporaire : login 200 avec le drapeau (jamais 409 au login).
            t.enqueue(200, t.fixture("cloud_login_temp_password.json"))
            assertEquals(LoginOutcome.MustChangePassword, t.container.auth.login("demo@essensys.fr", "Tmp-pass-123"))
            assertTrue(t.store.state.value.passwordChangeRequired)

            // Verrou posé en cours de session : 409 sur une route portail.
            t.store.update { it.copy(passwordChangeRequired = false) }
            t.enqueue(409, """{"error":"password_change_required","redirect":"/change-password"}""")
            t.container.portal.gatewayOnline()
            assertTrue(t.store.state.value.passwordChangeRequired)
            assertEquals("jwt-temp", t.store.state.value.token)

            // Changement : même jeton conservé, verrou levé.
            t.enqueue(200, """{"message":"Mot de passe mis à jour."}""")
            assertEquals(ApiResult.Ok(Unit), t.container.auth.changePassword("Tmp-pass-123", "Nouveau-pass-456"))
            assertFalse(t.store.state.value.passwordChangeRequired)
            assertEquals("jwt-temp", t.store.state.value.token)
        }
    }

    @Test
    fun weak_password_error_is_readable() = runTest {
        TestServer(token = "jwt").use { t ->
            t.enqueue(400, """{"error":"weak_password","message":"Le mot de passe doit contenir au moins 8 caractères."}""")
            val result = t.container.auth.changePassword("a", "b")
            assertEquals("Le mot de passe doit contenir au moins 8 caractères.", (result as ApiResult.Err).error.userMessage)
        }
    }

    @Test
    fun lan_login_keeps_session_cookie_and_sends_it_back() = runTest {
        TestServer(mode = ConnectionMode.LAN).use { t ->
            t.enqueue(
                200, t.fixture("lan_login_ok.json"),
                "Set-Cookie" to "essensys_lan_session=abc123; Path=/; HttpOnly; Secure; SameSite=Lax; Max-Age=604800",
            )
            assertEquals(LoginOutcome.LoggedIn, t.container.auth.login("admin@essensys.local", "secret123"))
            assertEquals("abc123", t.store.state.value.lanCookie)
            t.enqueue(200, t.fixture("lan_login_ok.json"))
            t.container.portal.lanSessionValid()
            t.server.takeRequest()
            assertEquals("essensys_lan_session=abc123", t.server.takeRequest().headers["Cookie"])
        }
    }

    @Test
    fun lan_password_change_ends_session() = runTest {
        TestServer(mode = ConnectionMode.LAN).use { t ->
            t.store.update { it.copy(lanCookie = "abc123") }
            t.enqueue(200, """{"status":"ok"}""")
            assertEquals(ApiResult.Ok(Unit), t.container.auth.changePassword("old-pass-1", "new-pass-2"))
            assertNull(t.store.state.value.lanCookie)
            assertEquals("PUT", t.server.takeRequest().method)
        }
    }

    @Test
    fun forbidden_account_clears_session() = runTest {
        TestServer(token = "jwt").use { t ->
            t.enqueue(403, """{"error":"account_forbidden","redirect":"/maintenance/"}""")
            t.container.portal.gatewayOnline()
            assertNull(t.store.state.value.token)
        }
    }

    // --- Session mémorisée (tâche 1.2 de session-30-jours-2026-11-001) ---

    private val rememberExpiry = Instant.parse("2026-12-10T08:00:00Z").toEpochMilli()

    @Test
    fun cloud_login_with_remember_stores_session_and_sends_the_option() = runTest {
        TestServer().use { t ->
            t.enqueue(200, t.fixture("cloud_login_remember.json"))
            assertEquals(LoginOutcome.LoggedIn, t.container.auth.login("demo@essensys.fr", "secret123", rememberMe = true))
            val state = t.store.state.value
            assertEquals("jwt-demo", state.token)
            assertEquals("remember-demo-0123456789", state.rememberToken)
            assertEquals(rememberExpiry, state.rememberExpiresAtMs)
            assertTrue(t.server.takeRequest().body!!.utf8().contains("\"remember_me\":true"))
        }
    }

    @Test
    fun cloud_login_without_the_option_sends_nothing_and_keeps_nothing() = runTest {
        TestServer().use { t ->
            // Même si le serveur renvoyait une session mémorisée, l'app n'en a pas demandé : elle ne la garde pas.
            t.enqueue(200, t.fixture("cloud_login_remember.json"))
            assertEquals(LoginOutcome.LoggedIn, t.container.auth.login("demo@essensys.fr", "secret123"))
            val state = t.store.state.value
            assertNull(state.rememberToken)
            assertNull(state.rememberExpiresAtMs)
            assertFalse(t.server.takeRequest().body!!.utf8().contains("remember_me"))
        }
    }

    @Test
    fun server_without_remember_support_logs_in_like_an_unchecked_box() = runTest {
        TestServer().use { t ->
            t.enqueue(200, t.fixture("cloud_login_ok.json"))
            assertEquals(LoginOutcome.LoggedIn, t.container.auth.login("demo@essensys.fr", "secret123", rememberMe = true))
            val state = t.store.state.value
            assertEquals("jwt-demo", state.token)
            assertNull(state.rememberToken)
            assertNull(state.rememberExpiresAtMs)
        }
    }

    @Test
    fun remember_session_is_not_kept_when_the_password_must_change() = runTest {
        TestServer().use { t ->
            t.enqueue(200, t.fixture("cloud_login_remember_temp_password.json"))
            assertEquals(LoginOutcome.MustChangePassword, t.container.auth.login("demo@essensys.fr", "Tmp-pass-123", rememberMe = true))
            assertNull(t.store.state.value.rememberToken)
            assertEquals("jwt-temp", t.store.state.value.token)
        }
    }

    @Test
    fun lan_login_with_remember_keeps_the_end_date_and_the_cookie() = runTest {
        TestServer(mode = ConnectionMode.LAN).use { t ->
            t.enqueue(
                200, t.fixture("lan_login_remember.json"),
                "Set-Cookie" to "essensys_lan_session=abc123; Path=/; HttpOnly; Secure; SameSite=Lax; Max-Age=2592000",
            )
            assertEquals(LoginOutcome.LoggedIn, t.container.auth.login("admin@essensys.local", "secret123", rememberMe = true))
            val state = t.store.state.value
            assertEquals("abc123", state.lanCookie)
            assertEquals(rememberExpiry, state.rememberExpiresAtMs)
            assertNull(state.rememberToken)
            assertTrue(t.server.takeRequest().body!!.utf8().contains("\"remember_me\":true"))
        }
    }

    @Test
    fun lan_gateway_without_remember_storage_gives_a_classic_session() = runTest {
        TestServer(mode = ConnectionMode.LAN).use { t ->
            t.enqueue(
                200, t.fixture("lan_login_ok.json"),
                "Set-Cookie" to "essensys_lan_session=abc123; Path=/; HttpOnly; Secure; Max-Age=604800",
            )
            assertEquals(LoginOutcome.LoggedIn, t.container.auth.login("admin@essensys.local", "secret123", rememberMe = true))
            assertEquals("abc123", t.store.state.value.lanCookie)
            assertNull(t.store.state.value.rememberExpiresAtMs)
        }
    }

    @Test
    fun a_new_login_resets_the_session_ended_notice() = runTest {
        TestServer().use { t ->
            t.store.update { it.copy(sessionEnded = true) }
            t.enqueue(200, t.fixture("cloud_login_ok.json"))
            t.container.auth.login("demo@essensys.fr", "secret123")
            assertFalse(t.store.state.value.sessionEnded)
        }
    }
}
