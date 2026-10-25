package com.essensys.android.data.control

import com.essensys.android.data.http.AlarmRequest
import com.essensys.android.data.http.ApiError
import com.essensys.android.data.http.ApiResult
import com.essensys.android.data.session.ConnectionMode
import com.essensys.android.support.TestServer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ComfortRepositoryTest {

    @Test
    fun current_modes_are_read_from_firmware_indices() = runBlocking {
        TestServer(token = "jwt").use { t ->
            t.enqueue(200, """{"values":[{"k":353,"v":"1"},{"k":363,"v":"30"}],"stale":false,"source":"gateway_cache"}""")
            t.enqueue(200, """{"values":[],"stale":false,"source":"none"}""")
            val modes = (t.container.comfort.readModes() as ApiResult.Ok).value
            assertEquals(IndexTable.WaterHeaterMode.OFF_PEAK, modes.waterHeater)
            assertEquals("Forcé 30 minutes", modes.sprinklerLabel)
            assertTrue(t.server.takeRequest().url.toString().endsWith("keys=353,363"))
            val unknown = (t.container.comfort.readModes() as ApiResult.Ok).value
            assertNull(unknown.waterHeater)
            assertNull(unknown.sprinklerLabel)
        }
    }

    @Test
    fun water_heater_and_sprinkler_commands_hit_inject() = runBlocking {
        TestServer(token = "jwt").use { t ->
            repeat(2) { t.enqueue(200, t.fixture("inject_ok.json")) }
            t.container.comfort.setWaterHeater(IndexTable.WaterHeaterMode.OFF_PEAK)
            t.container.comfort.setSprinkler(IndexTable.SprinklerMode.MINUTES_30)
            assertEquals("""{"k":353,"v":"1"}""", t.server.takeRequest().body!!.utf8())
            assertEquals("""{"k":363,"v":"30"}""", t.server.takeRequest().body!!.utf8())
        }
    }

    @Test
    fun alarm_posts_web_actions_in_cloud() = runBlocking {
        TestServer(token = "jwt").use { t ->
            t.enqueue(200, t.fixture("web_actions_ok.json"))
            assertEquals(ApiResult.Ok(InjectOutcome.SENT), t.container.comfort.alarm(on = true, code = "1234"))
            val request = t.server.takeRequest()
            assertEquals("/api/portal/web/actions", request.url.encodedPath)
            assertEquals("""{"alarme":"on","codealarme":"1234"}""", request.body!!.utf8())
        }
    }

    @Test
    fun alarm_is_cloud_only() = runBlocking {
        TestServer(mode = ConnectionMode.LAN).use { t ->
            t.store.update { it.copy(lanCookie = "abc") }
            assertEquals(ApiResult.Err(ApiError.CloudOnly), t.container.comfort.alarm(on = false, code = "1234"))
            assertEquals(0, t.server.requestCount)
        }
    }

    // NR: NR-android-16 essensys-hub/essensys-android-phone-apps#14
    @Test
    fun alarm_code_is_never_logged_or_stored_NR_android_16() = runBlocking {
        TestServer(token = "jwt", testMode = true).use { t ->
            // Réponse dry-run qui répète le code, puis erreur serveur qui le cite.
            t.enqueue(200, """{"status":"test_ok","dry_run":true,"validated_params":[{"k":410,"v":"12"},{"k":411,"v":"34"}],"message":"code 1234 ok"}""")
            t.enqueue(400, "invalid code 1234\n")
            val ok = t.container.comfort.alarm(on = true, code = "1234")
            assertEquals(ApiResult.Ok(InjectOutcome.DRY_RUN_OK), ok) // statut seul, rien du corps
            val err = t.container.comfort.alarm(on = false, code = "1234") as ApiResult.Err
            assertFalse(err.error.userMessage, err.error.userMessage.contains("1234"))
            assertFalse(err.toString(), err.toString().contains("1234"))
            // Aucune trace dans l'état de session (persisté sur l'appareil).
            assertFalse(t.store.state.value.toString().contains("1234"))
            assertFalse(AlarmRequest("on", "1234").toString().contains("1234"))
        }
        assertTrue(runCatching { TestServer(token = "jwt").use { it.container.comfort.alarm(true, "12a4") } }.isFailure)
    }
}
