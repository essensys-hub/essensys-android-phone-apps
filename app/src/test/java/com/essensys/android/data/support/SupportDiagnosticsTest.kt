package com.essensys.android.data.support

import com.essensys.android.data.session.ConnectionMode
import com.essensys.android.data.session.SessionState
import com.essensys.android.data.http.ArmoireInfo
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** Tests de diagnostic en lecture seule (tâche 1.2 de app-support-bundle-2026-10-013), contre un portail simulé. */
class SupportDiagnosticsTest {

    private fun DiagnosticReport.status(id: String) = checks.single { it.id == id }.checkStatus
    private fun DiagnosticReport.detail(id: String) = checks.single { it.id == id }.detail

    private fun run(server: SupportServer) = runBlocking { server.container.supportDiagnostics.run() }

    @Test
    fun cloud_all_good_reports_every_check_in_order() = SupportServer(SupportServer.cloud()).use { s ->
        val r = run(s)
        assertEquals(SupportDiagnostics.CHECKS.keys.toList(), r.checks.map { it.id })
        assertEquals("android", r.platform)
        assertTrue(r.checks.none { it.checkStatus == CheckStatus.ERROR })
        assertEquals(CheckStatus.OK, r.status("online"))
        assertTrue(r.detail("online"), r.detail("online").contains("gateway hors ligne · armoire vue"))
        assertTrue(r.detail("session").contains("demo@essensys.fr"))
        assertTrue(r.detail("last-action").contains("Lumière salon"))
        assertTrue(r.detail("latency").contains("5/5 réponses"))
        assertEquals(CheckStatus.NA, r.status("lan"))
    }

    @Test
    fun diagnostics_never_send_a_command() = SupportServer(SupportServer.cloud()).use { s ->
        run(s)
        assertTrue(s.paths().all { it.startsWith("GET ") })
        assertTrue(s.paths().none { it.contains("inject") || it.contains("web/actions") })
    }

    @Test
    fun test_mode_is_a_warning() = SupportServer(SupportServer.cloud(testMode = true)).use { s ->
        val r = run(s)
        assertEquals(CheckStatus.WARNING, r.status("test-mode"))
        assertTrue(r.detail("test-mode").contains("Mode test actif"))
    }

    @Test
    fun unreachable_portal_fails_network_checks_but_keeps_device_ones() {
        val s = SupportServer(SupportServer.cloud())
        s.close()
        val r = run(s)
        assertEquals(CheckStatus.ERROR, r.status("health"))
        assertEquals(CheckStatus.ERROR, r.status("latency"))
        assertTrue(r.detail("health"), r.detail("health").contains("Connexion refusée"))
        listOf("app", "os", "model", "locale", "mode", "test-mode").forEach { assertEquals(it, CheckStatus.OK, r.status(it)) }
    }

    @Test
    fun expired_session_is_reported() = SupportServer(SupportServer.cloud(), sessionCode = 401).use { s ->
        val r = run(s)
        assertEquals(CheckStatus.ERROR, r.status("session"))
        assertEquals("Session expirée : reconnectez-vous", r.detail("session"))
        assertEquals(CheckStatus.NA, r.status("online"))
    }

    @Test
    fun not_logged_in_skips_portal_checks() = SupportServer(SupportServer.cloud(token = null)).use { s ->
        val r = run(s)
        listOf("session", "armoire", "online", "last-action").forEach { assertEquals(it, CheckStatus.NA, r.status(it)) }
        assertEquals(CheckStatus.OK, r.status("health"))
        assertTrue(s.paths().none { it.contains("/api/portal/session") })
    }

    @Test
    fun offline_armoire_is_an_error_with_the_rule() = SupportServer(
        SupportServer.cloud(),
        sessionJson = """{"user":{"id":1},"portal_access":true,"armoire":{"no_serie":"X","last_seen":"2026-10-29T10:00:00Z","online":false}}""",
    ).use { s ->
        val r = run(s)
        assertEquals(CheckStatus.ERROR, r.status("online"))
        assertTrue(r.detail("online"), r.detail("online").contains("gateway absente · armoire non vue"))
    }

    @Test
    fun lan_gateway_reachable_or_not() {
        SupportServer(SessionState(mode = ConnectionMode.LAN, lanCookie = "c")).use { s ->
            val r = run(s)
            assertEquals(CheckStatus.OK, r.status("lan"))
            assertEquals(CheckStatus.NA, r.status("session"))
        }
        SupportServer(SessionState(mode = ConnectionMode.LAN, lanCookie = "c"), lanHealthCode = 502).use { s ->
            assertEquals(CheckStatus.ERROR, run(s).status("lan"))
        }
    }

    @Test
    fun lan_mode_on_mobile_data_is_a_warning() =
        SupportServer(SessionState(mode = ConnectionMode.LAN, lanCookie = "c"), network = NetworkType.CELLULAR).use { s ->
            val r = run(s)
            assertEquals(CheckStatus.WARNING, r.status("network"))
            assertTrue(r.detail("network").startsWith("Données mobiles"))
        }

    @Test
    fun results_are_streamed_as_they_come() = SupportServer(SupportServer.cloud()).use { s ->
        val seen = mutableListOf<String>()
        val r = runBlocking { s.container.supportDiagnostics.run { synchronized(seen) { seen += it.id } } }
        assertEquals(r.checks.map { it.id }.toSet(), seen.toSet())
    }

    private val now = Instant.parse("2026-10-30T14:00:00Z")
    private val paris = ZoneId.of("Europe/Paris")
    private fun rule(lastSeen: String?, online: Boolean = false, gateway: Boolean = false) =
        SupportDiagnostics.armoireCheck(ArmoireInfo("SC944D-0001", lastSeen, online), gateway, now, paris)

    @Test
    fun armoire_last_seen_is_local_with_elapsed_time() {
        val (status, detail) = rule("2026-10-30T13:59:30Z", online = true)
        assertEquals(CheckStatus.OK, status)
        assertEquals("Armoire SC944D-0001 · dernier contact 30/10/2026 14:59 (il y a moins d'1 min)", detail)
    }

    @Test
    fun armoire_last_seen_rule_follows_gateway_and_age() {
        val old = "2026-01-22T21:53:02.966462Z"
        assertEquals(CheckStatus.OK to "Armoire SC944D-0001 · dernier contact 22/01/2026 22:53 (il y a 280 j) · contact assuré par la gateway", rule(old, gateway = true))
        assertEquals(CheckStatus.WARNING to "Armoire SC944D-0001 · dernier contact 22/01/2026 22:53 (il y a 280 j)", rule(old))
        assertEquals(CheckStatus.OK, rule("2026-10-30T09:00:00Z").first)
        assertEquals(CheckStatus.WARNING, rule("2026-10-29T13:00:00Z").first)
        assertEquals(CheckStatus.OK, rule("2026-10-29T13:00:00+00:00", online = true).first)
    }

    @Test
    fun armoire_last_seen_unknown_is_a_warning() {
        assertEquals(CheckStatus.WARNING to "Armoire SC944D-0001 · dernier contact inconnu", rule(null, online = true))
        assertEquals(CheckStatus.WARNING, rule("hier").first)
    }

    @Test
    fun elapsed_labels_match_the_iphone() {
        assertEquals("il y a moins d'1 min", SupportDiagnostics.elapsed(Duration.ofSeconds(59)))
        assertEquals("il y a 3 min", SupportDiagnostics.elapsed(Duration.ofMinutes(3)))
        assertEquals("il y a 5 h", SupportDiagnostics.elapsed(Duration.ofHours(5)))
        assertEquals("il y a 47 h", SupportDiagnostics.elapsed(Duration.ofHours(47)))
        assertEquals("il y a 2 j", SupportDiagnostics.elapsed(Duration.ofHours(48)))
    }

    // NR: NR-android-21 essensys-hub/essensys-android-phone-apps#33
    // Une armoire vue il y a 9 mois ne doit plus être « OK » sans explication.
    @Test
    fun stale_armoire_last_seen_NR_android_21() {
        val seen = Instant.now().minus(Duration.ofDays(281)).toString()
        fun session(gateway: Boolean) = """{"user":{"id":1},"portal_access":true,"gateway":{"id":"gw","online":$gateway},""" +
            """"armoire":{"no_serie":"X","last_seen":"$seen","online":false}}"""
        SupportServer(SupportServer.cloud(), sessionJson = session(gateway = true)).use { s ->
            val r = run(s)
            assertEquals(CheckStatus.OK, r.status("armoire"))
            assertTrue(r.detail("armoire"), r.detail("armoire").endsWith("(il y a 281 j) · contact assuré par la gateway"))
        }
        SupportServer(SupportServer.cloud(), sessionJson = session(gateway = false)).use { s ->
            val r = run(s)
            assertEquals(CheckStatus.WARNING, r.status("armoire"))
            assertTrue(r.detail("armoire"), r.detail("armoire").endsWith("(il y a 281 j)"))
        }
    }
}
