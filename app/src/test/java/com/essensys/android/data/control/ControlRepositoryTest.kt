package com.essensys.android.data.control

import com.essensys.android.data.http.ApiError
import com.essensys.android.data.http.ApiResult
import com.essensys.android.data.session.ConnectionMode
import com.essensys.android.support.TestServer
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ControlRepositoryTest {

    private val salonOn = IndexTable.lights.first { it.id == "salon" }.command(on = true)

    @Test
    fun cloud_inject_posts_portal_endpoint_with_bearer() = runTest {
        TestServer(token = "jwt").use { t ->
            t.enqueue(200, t.fixture("inject_ok.json"))
            assertEquals(ApiResult.Ok(InjectOutcome.SENT), t.container.control.inject(salonOn))
            val request = t.server.takeRequest()
            assertEquals("/api/portal/inject", request.url.encodedPath)
            assertEquals("""{"k":612,"v":"128"}""", request.body!!.utf8())
            assertEquals("Bearer jwt", request.headers["Authorization"])
            assertNull(request.headers["X-Essensys-Test-Mode"])
        }
    }

    @Test
    fun lan_inject_posts_admin_endpoint_with_cookie() = runTest {
        TestServer(mode = ConnectionMode.LAN).use { t ->
            t.store.update { it.copy(lanCookie = "abc") }
            t.enqueue(200, """{"status":"ok","guid":"g","guids":["g"]}""")
            assertEquals(ApiResult.Ok(InjectOutcome.SENT), t.container.control.inject(salonOn))
            val request = t.server.takeRequest()
            assertEquals("/api/admin/inject", request.url.encodedPath)
            assertEquals("essensys_lan_session=abc", request.headers["Cookie"])
        }
    }

    // NR: NR-android-7 essensys-hub/essensys-android-phone-apps#4
    @Test
    fun dry_run_header_sent_in_test_mode_NR_android_7() = runTest {
        TestServer(token = "jwt", testMode = true).use { t ->
            t.enqueue(200, t.fixture("inject_dry_run.json"))
            assertEquals(ApiResult.Ok(InjectOutcome.DRY_RUN_OK), t.container.control.inject(salonOn))
            assertEquals("dry-run", t.server.takeRequest().headers["X-Essensys-Test-Mode"])
        }
    }

    @Test
    fun rate_limit_is_reported() = runTest {
        TestServer(token = "jwt").use { t ->
            t.enqueue(429, "Rate limit exceeded\n")
            val result = t.container.control.inject(salonOn)
            assertEquals(ApiError.RateLimited, (result as ApiResult.Err).error)
        }
    }

    @Test
    fun group_command_is_sequential_and_stops_on_error() = runTest {
        TestServer(token = "jwt").use { t ->
            t.enqueue(200, t.fixture("inject_ok.json"))
            t.enqueue(403, "Portal access not approved\n")
            val all = IndexTable.lights.take(3).map { it.command(on = false) }
            val result = t.container.control.injectAll(all)
            assertEquals("Accès portail non approuvé.", (result as ApiResult.Err).error.userMessage)
            assertEquals(2, t.server.requestCount)
        }
    }

    @Test
    fun exchange_read_returns_travel_times() = runTest {
        TestServer(token = "jwt").use { t ->
            t.enqueue(200, """{"values":[{"k":566,"v":"25"},{"k":567,"v":"30"}],"stale":false,"source":"gateway_cache"}""")
            val result = t.container.control.readExchange(listOf(566, 567))
            assertEquals(ApiResult.Ok(mapOf(566 to "25", 567 to "30")), result)
            assertTrue(t.server.takeRequest().url.toString().endsWith("/api/portal/exchange?keys=566,567"))
        }
    }

    // NR: NR-android-4 essensys-hub/essensys-android-phone-apps#3
    @Test
    fun no_demo_fallback_when_server_unreachable_NR_android_4() = runTest {
        val t = TestServer(token = "jwt")
        t.close() // serveur arrêté : connexion refusée
        val result = t.container.control.inject(salonOn)
        assertTrue(result is ApiResult.Err && result.error is ApiError.Network)
        assertTrue((result as ApiResult.Err).error.userMessage.startsWith("Connexion impossible à"))
    }
}
