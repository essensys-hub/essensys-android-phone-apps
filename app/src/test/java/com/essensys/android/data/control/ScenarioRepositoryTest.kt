package com.essensys.android.data.control

import com.essensys.android.data.http.ApiResult
import com.essensys.android.data.session.ConnectionMode
import com.essensys.android.support.TestServer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScenarioRepositoryTest {

    @Test
    fun list_hides_server_slot_and_marks_last_launched() = runBlocking {
        TestServer(token = "jwt").use { t ->
            t.enqueue(200, t.fixture("scenarios_list.json"))
            val slots = (t.container.scenarios.list() as ApiResult.Ok).value
            assertEquals((2..8).toList(), slots.map { it.slot })
            assertEquals("Je sors", slots.first().label)
            assertEquals(2, slots.first().lastLaunched)
            assertEquals("/api/portal/scenarios", t.server.takeRequest().url.encodedPath)
        }
    }

    @Test
    fun launch_uses_cloud_then_lan_routes() = runBlocking {
        TestServer(token = "jwt").use { t ->
            t.enqueue(200, t.fixture("scenario_launch_ok.json"))
            assertEquals(ApiResult.Ok(InjectOutcome.SENT), t.container.scenarios.launch(2))
            val request = t.server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/api/portal/scenarios/2/launch", request.url.encodedPath)
        }
        TestServer(mode = ConnectionMode.LAN).use { t ->
            t.store.update { it.copy(lanCookie = "abc") }
            t.enqueue(200, t.fixture("scenario_launch_ok.json"))
            t.container.scenarios.launch(4)
            assertEquals("/api/scenarios/4/launch", t.server.takeRequest().url.encodedPath)
        }
    }

    // Spec : en mode test, lancement, restauration et enregistrement portent l'en-tête dry-run, en LAN aussi.
    @Test
    fun lan_mutations_carry_dry_run_header_in_test_mode() = runBlocking {
        TestServer(mode = ConnectionMode.LAN, testMode = true).use { t ->
            t.store.update { it.copy(lanCookie = "abc") }
            repeat(3) { t.enqueue(200, t.fixture("inject_dry_run.json")) }
            val definition = ScenarioDefinition(4, IndexTable.scenarioBlock(4).associateWith { 0 })
            assertEquals(ApiResult.Ok(InjectOutcome.DRY_RUN_OK), t.container.scenarios.launch(6))
            assertEquals(ApiResult.Ok(InjectOutcome.DRY_RUN_OK), t.container.scenarios.restore(3))
            assertEquals(ApiResult.Ok(InjectOutcome.DRY_RUN_OK), t.container.scenarios.save(definition))
            repeat(3) { assertEquals("dry-run", t.server.takeRequest().headers["X-Essensys-Test-Mode"]) }
        }
    }

    // NR: NR-android-15 essensys-hub/essensys-android-phone-apps#13
    @Test
    fun scenario_bitmask_index_is_shifted_into_slot_block_NR_android_15() = runBlocking {
        assertEquals(728, IndexTable.shiftIntoScenario(605, 4))
        assertEquals(633 + 25, IndexTable.shiftIntoScenario(617, 2))
        TestServer(token = "jwt").use { t ->
            t.enqueue(200, t.fixture("scenario_4.json"))
            t.enqueue(200, t.fixture("scenario_save_ok.json"))
            val definition = (t.container.scenarios.definition(4) as ApiResult.Ok).value
            assertTrue(definition.complete)
            val edited = definition.toggled(metaIndex = 605, bitValue = 2, on = true)
            assertTrue(edited.isSet(605, 2))
            t.container.scenarios.save(edited)
            t.server.takeRequest()
            val body = t.server.takeRequest().body!!.utf8()
            assertTrue(body, body.contains(""""728":"2""""))
            assertFalse(body, body.contains(""""605""""))
            assertEquals(41, Regex(""""\d+":""").findAll(body).count()) // les 41 octets du bloc 715–755
        }
    }

    @Test
    fun incomplete_definition_or_out_of_block_index_is_refused_locally() = runBlocking {
        TestServer(token = "jwt").use { t ->
            val partial = ScenarioDefinition(4, mapOf(715 to 0))
            assertFalse(partial.complete)
            assertTrue(runCatching { t.container.scenarios.save(partial) }.isFailure)
            val outside = ScenarioDefinition(4, IndexTable.scenarioBlock(4).associateWith { 0 } + (605 to 1))
            assertTrue(runCatching { t.container.scenarios.save(outside) }.isFailure)
            assertTrue(runCatching { t.container.scenarios.restore(7) }.isFailure)
            assertEquals(0, t.server.requestCount)
        }
    }

    @Test
    fun test_failed_from_server_is_reported() = runBlocking {
        TestServer(token = "jwt", testMode = true).use { t ->
            t.enqueue(422, t.fixture("test_failed.json"))
            val result = t.container.scenarios.launch(2)
            assertTrue((result as ApiResult.Err).error.userMessage.contains("outside slot range"))
        }
    }
}
