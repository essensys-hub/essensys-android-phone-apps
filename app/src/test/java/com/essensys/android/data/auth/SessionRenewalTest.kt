package com.essensys.android.data.auth

import com.essensys.android.data.http.ApiError
import com.essensys.android.data.http.ApiResult
import com.essensys.android.data.session.ConnectionMode
import com.essensys.android.support.RememberServer
import com.essensys.android.support.RememberServer.Companion.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import mockwebserver3.MockResponse
import mockwebserver3.SocketEffect
import java.time.Instant

/** Renouvellement transparent et fin de session (tâches 2.1 et 2.2 de session-30-jours-2026-11-001). */
class SessionRenewalTest {

    private val now = Instant.parse("2026-11-20T08:00:00Z").toEpochMilli()
    private val expiry = Instant.parse("2026-12-10T08:00:00Z").toEpochMilli()

    @Test
    fun expired_access_is_renewed_and_the_request_replayed_once() = runBlocking {
        RememberServer(rememberExpiresAtMs = expiry, clock = { now }).use { s ->
            val result = s.container.portal.session()
            assertTrue(result is ApiResult.Ok)
            assertEquals(1, s.refreshCount.get())
            assertEquals("jwt-new", s.store.state.value.token)
            // Les 30 jours ne bougent pas : ni la session mémorisée ni sa date de fin ne sont touchées.
            assertEquals("remember-old", s.store.state.value.rememberToken)
            assertEquals(expiry, s.store.state.value.rememberExpiresAtMs)
            assertEquals(
                listOf("GET /api/portal/session", "POST /api/auth/remember/refresh", "GET /api/portal/session"),
                s.paths(),
            )
            assertEquals("Bearer jwt-old", s.requests[0].headers["Authorization"])
            assertEquals("Bearer jwt-new", s.requests[2].headers["Authorization"])
            assertTrue(s.refreshRequests().single().body!!.utf8().contains("\"remember_token\":\"remember-old\""))
        }
    }

    @Test
    fun four_simultaneous_requests_cause_a_single_renewal() = runBlocking {
        RememberServer(rememberExpiresAtMs = expiry, clock = { now }).use { s ->
            s.refreshDelayMs = 300
            val results = (1..4).map { async(Dispatchers.IO) { s.container.portal.session() } }.awaitAll()
            assertTrue(results.all { it is ApiResult.Ok })
            assertEquals(1, s.refreshCount.get())
            assertEquals("jwt-new", s.store.state.value.token)
        }
    }

    @Test
    fun a_refused_renewal_ends_the_session_and_explains_it() = runBlocking {
        RememberServer(rememberExpiresAtMs = expiry, clock = { now }).use { s ->
            s.refresh = { json(401, """{"error":"remember_invalid"}""") }
            val result = s.container.portal.session()
            assertTrue((result as ApiResult.Err).error is ApiError.Unauthorized)
            val state = s.store.state.value
            assertNull(state.token)
            assertNull(state.rememberToken)
            assertNull(state.rememberExpiresAtMs)
            assertFalse(state.isAuthenticated)
            assertTrue(state.sessionEnded)
            // Une seule tentative : le 401 d'origine n'est pas rejoué.
            assertEquals(1, s.refreshCount.get())
        }
    }

    @Test
    fun a_disabled_account_ends_the_session() = runBlocking {
        RememberServer(rememberExpiresAtMs = expiry, clock = { now }).use { s ->
            s.refresh = { json(403, """{"error":"account_forbidden","redirect":"/maintenance/"}""") }
            s.container.portal.session()
            assertFalse(s.store.state.value.isAuthenticated)
            assertTrue(s.store.state.value.sessionEnded)
        }
    }

    @Test
    fun a_passing_server_failure_keeps_the_remembered_session() = runBlocking {
        RememberServer(rememberExpiresAtMs = expiry, clock = { now }).use { s ->
            s.refresh = { json(503, """{"error":"unavailable"}""") }
            val result = s.container.portal.session()
            // Ce n'est pas un refus d'authentification : l'utilisateur n'est pas déconnecté.
            assertTrue((result as ApiResult.Err).error !is ApiError.Unauthorized)
            val state = s.store.state.value
            assertEquals("jwt-old", state.token)
            assertEquals("remember-old", state.rememberToken)
            assertFalse(state.sessionEnded)
            assertTrue(state.isAuthenticated)
        }
    }

    @Test
    fun a_locally_expired_session_ends_without_calling_the_server() = runBlocking {
        RememberServer(rememberExpiresAtMs = expiry, clock = { expiry + 1 }).use { s ->
            s.container.portal.session()
            assertEquals(0, s.refreshCount.get())
            assertFalse(s.store.state.value.isAuthenticated)
            assertTrue(s.store.state.value.sessionEnded)
        }
    }

    @Test
    fun without_a_remembered_session_a_401_ends_the_session_as_before() = runBlocking {
        RememberServer(rememberToken = null, clock = { now }).use { s ->
            s.container.portal.session()
            assertEquals(0, s.refreshCount.get())
            assertFalse(s.store.state.value.isAuthenticated)
            // Pas de message « session mémorisée » : il n'y en avait pas.
            assertFalse(s.store.state.value.sessionEnded)
        }
    }

    @Test
    fun a_lan_session_is_never_renewed() = runBlocking {
        RememberServer(
            state = com.essensys.android.data.session.SessionState(
                mode = ConnectionMode.LAN, lanCookie = "abc", rememberExpiresAtMs = expiry,
            ),
            clock = { now },
        ).use { s ->
            s.container.portal.lanSessionValid()
            assertEquals(0, s.refreshCount.get())
            // Comme avant : la gateway a refusé le cookie, la session est effacée.
            assertNull(s.store.state.value.lanCookie)
        }
    }

    @Test
    fun a_wrong_current_password_is_not_an_expired_session() = runBlocking {
        RememberServer(rememberExpiresAtMs = expiry, clock = { now }).use { s ->
            s.passwordChangeCode = 401
            val result = s.container.auth.changePassword("mauvais", "Nouveau-pass-456")
            assertTrue(result is ApiResult.Err)
            assertEquals(0, s.refreshCount.get())
        }
    }

    @Test
    fun a_failed_login_never_triggers_a_renewal() = runBlocking {
        RememberServer(rememberExpiresAtMs = expiry, clock = { now }).use { s ->
            val outcome = s.container.auth.login("demo@essensys.fr", "mauvais")
            assertEquals(LoginOutcome.Failed("Email ou mot de passe incorrect."), outcome)
            assertEquals(0, s.refreshCount.get())
        }
    }

    @Test
    fun logout_clears_the_phone_at_once_and_revokes_the_remembered_session() = runBlocking {
        RememberServer(rememberExpiresAtMs = expiry, clock = { now }).use { s ->
            s.container.auth.logout()
            val state = s.store.state.value
            assertNull(state.token)
            assertNull(state.rememberToken)
            assertNull(state.rememberExpiresAtMs)
            assertFalse(state.sessionEnded)
            val logout = s.requests.single { it.url.encodedPath == "/api/auth/logout" }
            assertTrue(logout.body!!.utf8().contains("\"remember_token\":\"remember-old\""))
        }
    }

    @Test
    fun logout_without_network_succeeds_and_clears_everything() = runBlocking {
        val s = RememberServer(rememberExpiresAtMs = expiry, clock = { now })
        s.close()
        val start = System.nanoTime()
        s.container.auth.logout()
        val state = s.store.state.value
        assertNull(state.token)
        assertNull(state.rememberToken)
        assertNull(state.rememberExpiresAtMs)
        // Borné par le délai court de la révocation : jamais le délai complet du client HTTP.
        assertTrue((System.nanoTime() - start) / 1_000_000 < 5_000)
    }

    @Test
    fun lan_logout_sends_the_session_cookie_to_the_gateway() = runBlocking {
        RememberServer(
            state = com.essensys.android.data.session.SessionState(
                mode = ConnectionMode.LAN, lanCookie = "abc123", rememberExpiresAtMs = expiry,
            ),
            clock = { now },
        ).use { s ->
            s.container.auth.logout()
            assertNull(s.store.state.value.lanCookie)
            assertNull(s.store.state.value.rememberExpiresAtMs)
            val logout = s.requests.single { it.url.encodedPath == "/api/auth/logout" }
            assertEquals("essensys_lan_session=abc123", logout.headers["Cookie"])
        }
    }

    @Test
    fun a_password_change_with_a_remembered_session_ends_the_cloud_session() = runBlocking {
        RememberServer(rememberExpiresAtMs = expiry, clock = { now }).use { s ->
            val result = s.container.auth.changePassword("ancien-pass-1", "Nouveau-pass-456")
            assertEquals(ApiResult.Ok(Unit), result)
            // Le serveur a révoqué toutes les sessions mémorisées, y compris celle-ci.
            assertFalse(s.store.state.value.isAuthenticated)
            assertNull(s.store.state.value.rememberToken)
        }
    }

    @Test
    fun a_password_change_without_a_remembered_session_keeps_the_token() = runBlocking {
        RememberServer(rememberToken = null, clock = { now }).use { s ->
            assertEquals(ApiResult.Ok(Unit), s.container.auth.changePassword("ancien-pass-1", "Nouveau-pass-456"))
            assertEquals("jwt-old", s.store.state.value.token)
        }
    }

    @Test
    fun an_unreachable_server_during_renewal_keeps_the_session() = runBlocking {
        val s = RememberServer(rememberExpiresAtMs = expiry, clock = { now })
        s.refresh = { MockResponse.Builder().onRequestStart(SocketEffect.CloseSocket()).build() }
        s.use {
            // Le portail répond 401 ; le renouvellement échoue ensuite faute de réponse : pas de déconnexion.
            val result = s.container.portal.session()
            assertTrue(result is ApiResult.Err)
            assertTrue(s.store.state.value.isAuthenticated)
            assertEquals("remember-old", s.store.state.value.rememberToken)
        }
    }
}
