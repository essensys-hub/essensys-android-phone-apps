package com.essensys.android.data.auth

import com.essensys.android.data.http.ApiResult
import com.essensys.android.data.support.SupportBundle
import com.essensys.android.support.RememberServer
import com.essensys.android.support.RememberServer.Companion.json
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.util.zip.ZipFile

/** Non-régression de la session mémorisée (tâche 3.1 de session-30-jours-2026-11-001), horloge simulée, sans armoire. */
class RememberSessionNrTest {

    @get:Rule val tmp = TemporaryFolder()

    private val day = 24 * 3_600_000L
    private val t0 = Instant.parse("2026-11-10T08:00:00Z").toEpochMilli()
    private val expiry = t0 + 30 * day

    // NR: NR-android-22 essensys-hub/essensys-android-phone-apps#37
    // Les 30 jours partent de la saisie du mot de passe : l'usage quotidien ne les prolonge jamais.
    @Test
    fun usage_never_extends_the_remember_session_NR_android_22() = runBlocking {
        var now = t0
        RememberServer(token = "jwt-0", rememberExpiresAtMs = expiry, clock = { now }).use { s ->
            var issued = 0
            s.refresh = {
                if (now >= expiry) json(401, """{"error":"remember_invalid"}""")
                else {
                    issued++
                    s.validAccess = setOf("jwt-$issued")
                    json(200, """{"token":"jwt-$issued","user":{"id":1},"password_change_required":false}""")
                }
            }
            for (daysSince in 1..29) {
                now = t0 + daysSince * day
                s.validAccess = emptySet() // l'accès de 24 h de la veille a expiré : chaque jour exige un renouvellement
                assertTrue("jour $daysSince", s.container.portal.session() is ApiResult.Ok)
                assertEquals("jour $daysSince", expiry, s.store.state.value.rememberExpiresAtMs)
                assertEquals("remember-old", s.store.state.value.rememberToken)
            }
            assertEquals(29, issued)

            now = t0 + 31 * day
            s.validAccess = emptySet()
            assertTrue(s.container.portal.session() is ApiResult.Err)
            assertFalse(s.store.state.value.isAuthenticated)
            assertTrue(s.store.state.value.sessionEnded)
        }
    }

    // Même scénario quand l'app ne connaît pas la date de fin : c'est alors le serveur qui refuse, le 31e jour.
    @Test
    fun the_server_refusal_on_day_31_asks_for_the_password_again_NR_android_22() = runBlocking {
        var now = t0
        RememberServer(token = "jwt-0", rememberExpiresAtMs = null, clock = { now }).use { s ->
            s.refresh = {
                if (now >= expiry) json(401, """{"error":"remember_invalid"}""")
                else json(200, """{"token":"jwt-new","user":{"id":1},"password_change_required":false}""")
            }
            now = t0 + 29 * day
            assertTrue(s.container.portal.session() is ApiResult.Ok)
            s.store.update { it.copy(token = "jwt-0") }
            now = t0 + 31 * day
            assertTrue(s.container.portal.session() is ApiResult.Err)
            assertFalse(s.store.state.value.isAuthenticated)
            assertTrue(s.store.state.value.sessionEnded)
        }
    }

    private fun unzip(zip: File): Map<String, String> = ZipFile(zip).use { z ->
        z.entries().toList().associate { it.name to z.getInputStream(it).readBytes().toString(Charsets.UTF_8) }
    }

    // NR: NR-android-23 essensys-hub/essensys-android-phone-apps#37
    // Le journal et le bundle partent par email : ni mot de passe, ni jeton, ni session mémorisée ne doivent s'y trouver.
    @Test
    fun no_password_token_or_remember_session_in_journal_and_bundle_NR_android_23() = runBlocking {
        val password = "mot-de-passe-fictif!"
        val firstJwt = "jwt-SECRET-login-0123456789"
        val renewedJwt = "jwt-SECRET-renewed-9876543210"
        val rememberToken = "remember-SECRET-token-abcdef0123"
        RememberServer(token = null, rememberToken = null, clock = { t0 }).use { s ->
            s.login = {
                json(
                    200,
                    """{"token":"$firstJwt","user":{"id":1,"email":"demo@essensys.fr"},"password_change_required":false,""" +
                        """"remember_token":"$rememberToken","remember_expires_at":"2026-12-10T08:00:00Z"}""",
                )
            }
            s.refresh = {
                s.validAccess = setOf(renewedJwt)
                json(200, """{"token":"$renewedJwt","user":{"id":1},"password_change_required":false}""")
            }
            assertEquals(LoginOutcome.LoggedIn, s.container.auth.login("demo@essensys.fr", password, rememberMe = true))
            assertEquals(rememberToken, s.store.state.value.rememberToken)
            // L'accès expire : le renouvellement passe par la session mémorisée.
            assertTrue(s.container.portal.session() is ApiResult.Ok)
            assertEquals(renewedJwt, s.store.state.value.token)

            val report = s.container.supportDiagnostics.run()
            val files = unzip(SupportBundle(tmp.root, s.store, s.container.appLog, ZoneId.of("Europe/Paris")).create(report).zip)
            assertTrue(files.getValue("journal.jsonl").contains("POST /api/auth/remember/refresh"))
            val everything = files + ("journal-memoire" to s.container.appLog.toJsonLines())
            everything.forEach { (name, text) ->
                listOf(password, firstJwt, renewedJwt, rememberToken, "Authorization", "Cookie", "remember_token").forEach { secret ->
                    assertFalse("$secret dans $name", text.contains(secret))
                }
            }
        }
    }
}
