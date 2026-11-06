package com.essensys.android.data.control

import com.essensys.android.data.http.ApiError
import com.essensys.android.data.http.ApiResult
import com.essensys.android.data.session.ConnectionMode
import com.essensys.android.support.TestServer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Temporisations des lumières contre un serveur simulé (spec light-timers-clients). */
class LightTimersRepositoryTest {

    private val sample = """{"timers":[
        {"id":"dressing","label":"Dressing","key":521,"minutes":15},
        {"id":"wc1","label":"WC 1","key":555,"minutes":null},
        {"id":"wc2","label":"WC 2","key":556,"minutes":0},
        {"id":"pieceserv","label":"Service","key":557,"minutes":60},
        {"id":"ann1","label":"Annexe 1","key":561,"minutes":null},
        {"id":"ann2","label":"Annexe 2","key":562,"minutes":7}
    ]}"""

    @Test
    fun read_in_cloud_uses_portal_path_and_keeps_unknown_as_null() = runBlocking {
        TestServer(token = "jwt").use { t ->
            t.enqueue(200, sample)
            val timers = (t.container.comfort.readLightTimers() as ApiResult.Ok).value
            val request = t.server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/api/portal/lights/timers", request.url.encodedPath)
            assertEquals(15, timers[LightTimer.DRESSING])
            assertNull(timers[LightTimer.WC1]) // jamais lue : inconnue, pas 0
            assertEquals(0, timers[LightTimer.WC2])
            assertEquals(60, timers[LightTimer.SERVICE])
            assertEquals(7, timers[LightTimer.ANNEXE2])
            assertEquals(LightTimer.entries, timers.keys.toList())
        }
    }

    @Test
    fun read_in_lan_uses_admin_path() = runBlocking {
        TestServer(mode = ConnectionMode.LAN).use { t ->
            t.store.update { it.copy(lanCookie = "abc") }
            t.enqueue(200, sample)
            assertTrue(t.container.comfort.readLightTimers() is ApiResult.Ok)
            val request = t.server.takeRequest()
            assertEquals("/api/admin/lights/timers", request.url.encodedPath)
            assertEquals("essensys_lan_session=abc", request.headers["Cookie"])
        }
    }

    @Test
    fun missing_light_and_out_of_range_values_are_unknown() = runBlocking {
        TestServer(token = "jwt").use { t ->
            t.enqueue(200, """{"timers":[{"id":"dressing","minutes":300},{"id":"wc1","minutes":-1},{"id":"inconnue","minutes":5}]}""")
            val timers = (t.container.comfort.readLightTimers() as ApiResult.Ok).value
            LightTimer.entries.forEach { assertNull(it.id, timers[it]) }
        }
    }

    // NR: NR-android-24 essensys-hub/essensys-android-phone-apps#43
    @Test
    fun write_in_cloud_sends_minutes_to_admin_path_NR_android_24() = runBlocking {
        TestServer(token = "jwt").use { t ->
            t.enqueue(200, """{"id":"dressing","minutes":30,"status":"queued"}""")
            assertEquals(ApiResult.Ok(InjectOutcome.SENT), t.container.comfort.setLightTimer(LightTimer.DRESSING, 30))
            val request = t.server.takeRequest()
            assertEquals("PUT", request.method)
            assertEquals("/api/portal/admin/lights/timers/dressing", request.url.encodedPath)
            assertEquals("""{"minutes":30}""", request.body!!.utf8())
            assertEquals("Bearer jwt", request.headers["Authorization"])
        }
    }

    @Test
    fun write_in_lan_uses_admin_path_and_zero_means_never() = runBlocking {
        TestServer(mode = ConnectionMode.LAN).use { t ->
            t.store.update { it.copy(lanCookie = "abc") }
            t.enqueue(200, """{"id":"ann2","minutes":0,"status":"queued"}""")
            assertEquals(ApiResult.Ok(InjectOutcome.SENT), t.container.comfort.setLightTimer(LightTimer.ANNEXE2, 0))
            val request = t.server.takeRequest()
            assertEquals("/api/admin/lights/timers/ann2", request.url.encodedPath)
            assertEquals("""{"minutes":0}""", request.body!!.utf8())
        }
    }

    @Test
    fun write_in_test_mode_is_a_dry_run() = runBlocking {
        TestServer(token = "jwt", testMode = true).use { t ->
            t.enqueue(200, """{"id":"wc2","minutes":45,"status":"dry_run"}""")
            assertEquals(ApiResult.Ok(InjectOutcome.DRY_RUN_OK), t.container.comfort.setLightTimer(LightTimer.WC2, 45))
            assertEquals("dry-run", t.server.takeRequest().headers["X-Essensys-Test-Mode"])
        }
    }

    @Test
    fun write_is_blocked_by_the_no_armoire_guard_without_test_mode() = runBlocking {
        // Le garde-fou ne laisse passer que le serveur simulé : l'écriture vers un hôte réel lèverait ArmoireMutationBlocked.
        assertTrue(com.essensys.android.data.http.NoArmoireInterceptor.isArmoireMutation("PUT", "/api/portal/admin/lights/timers/wc1"))
        assertTrue(com.essensys.android.data.http.NoArmoireInterceptor.isArmoireMutation("PUT", "/api/admin/lights/timers/wc1"))
        assertEquals(false, com.essensys.android.data.http.NoArmoireInterceptor.isArmoireMutation("GET", "/api/portal/lights/timers"))
    }

    @Test
    fun out_of_range_value_never_reaches_the_server() = runBlocking {
        TestServer(token = "jwt").use { t ->
            for (bad in listOf(256, 300, -1)) {
                try {
                    t.container.comfort.setLightTimer(LightTimer.DRESSING, bad)
                    fail("$bad aurait dû être refusé")
                } catch (_: IllegalArgumentException) {
                }
            }
            assertEquals(0, t.server.requestCount)
        }
    }

    @Test
    fun forbidden_is_reported_and_keeps_the_session() = runBlocking {
        TestServer(token = "jwt").use { t ->
            t.enqueue(403, """{"error":"forbidden"}""")
            val result = t.container.comfort.setLightTimer(LightTimer.WC1, 15)
            assertTrue((result as ApiResult.Err).error is ApiError.Forbidden)
            assertEquals("jwt", t.store.state.value.token)
        }
    }
}
