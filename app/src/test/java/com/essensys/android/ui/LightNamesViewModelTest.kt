package com.essensys.android.ui

import com.essensys.android.data.control.LightNames
import com.essensys.android.support.TestServer
import com.essensys.android.ui.components.Feedback
import com.essensys.android.ui.screens.LightNamesViewModel
import com.essensys.android.ui.screens.LightTimersViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Écran des noms : repli, renommage, remise à zéro, validation (spec light-names-clients). */
@OptIn(ExperimentalCoroutinesApi::class)
class LightNamesViewModelTest {

    @Before fun setUp() = Dispatchers.setMain(Dispatchers.Unconfined)
    @After fun tearDown() = Dispatchers.resetMain()

    private suspend fun waitUntil(block: () -> Boolean) = withTimeout(10_000) { while (!block()) delay(10) }

    private fun saved(name: String?, version: Int) =
        """{"id":"dressing","default":"Dressing","name":${name?.let { "\"$it\"" } ?: "null"},"version":$version,"updated_at":"2026-11-07T10:15:30.123Z","status":"saved"}"""

    @Test
    fun before_and_after_a_failed_read_the_original_names_are_shown() = runBlocking {
        TestServer(token = "jwt").use { t ->
            val vm = LightNamesViewModel(t.container.comfort)
            assertEquals("Dressing", vm.displayName("dressing"))
            t.enqueue(500, "boom")
            vm.load()
            waitUntil { t.server.requestCount == 1 }
            delay(50)
            assertEquals("Dressing", vm.displayName("dressing"))
            assertEquals(null, vm.state.value.feedback) // pas de message bloquant
        }
    }

    @Test
    fun loaded_custom_name_is_displayed() = runBlocking {
        TestServer(token = "jwt").use { t ->
            t.enqueue(200, """{"lights":[{"id":"dressing","default":"Dressing","name":"WC 3","version":2,"updated_at":"2026-11-07T10:15:30.123Z"}]}""")
            val vm = LightNamesViewModel(t.container.comfort)
            vm.load()
            waitUntil { vm.displayName("dressing") == "WC 3" }
            assertEquals("Salon", vm.displayName("salon"))
        }
    }

    @Test
    fun rename_updates_the_display_without_reload() = runBlocking {
        TestServer(token = "jwt").use { t ->
            t.enqueue(200, saved("WC 3", 2))
            val vm = LightNamesViewModel(t.container.comfort)
            vm.rename("dressing", " WC 3 ")
            waitUntil { vm.displayName("dressing") == "WC 3" && vm.state.value.saving.isEmpty() }
            assertEquals(Feedback.Kind.SUCCESS, vm.state.value.feedback!!.kind)
            assertEquals(1, t.server.requestCount) // pas de relecture
        }
    }

    @Test
    fun reset_brings_back_the_original_name() = runBlocking {
        TestServer(token = "jwt").use { t ->
            t.enqueue(200, saved("WC 3", 2))
            t.enqueue(200, saved(null, 3))
            val vm = LightNamesViewModel(t.container.comfort)
            vm.rename("dressing", "WC 3")
            waitUntil { vm.displayName("dressing") == "WC 3" }
            vm.reset("dressing")
            waitUntil { vm.displayName("dressing") == "Dressing" && vm.state.value.saving.isEmpty() }
            t.server.takeRequest()
            assertEquals("""{"name":null}""", t.server.takeRequest().body!!.utf8())
        }
    }

    @Test
    fun invalid_name_is_refused_with_a_message_and_no_request() = runBlocking {
        TestServer(token = "jwt").use { t ->
            val vm = LightNamesViewModel(t.container.comfort)
            for (bad in listOf("", "a".repeat(41), "<x>")) vm.rename("dressing", bad)
            delay(100)
            assertEquals(0, t.server.requestCount)
            val feedback = vm.state.value.feedback!!
            assertEquals(Feedback.Kind.ERROR, feedback.kind)
            assertTrue(feedback.text, feedback.text.contains("1 à 40"))
        }
    }

    @Test
    fun forbidden_and_unavailable_show_their_message_and_keep_the_name() = runBlocking {
        TestServer(token = "jwt").use { t ->
            val vm = LightNamesViewModel(t.container.comfort)
            t.enqueue(403, """{"error":"forbidden"}""")
            vm.rename("dressing", "WC 3")
            waitUntil { vm.state.value.feedback?.text?.contains("administrateur") == true }
            t.enqueue(503, """{"error":"names_unavailable"}""")
            vm.rename("dressing", "WC 3")
            waitUntil { vm.state.value.feedback?.text?.contains("indisponible") == true }
            assertEquals("Dressing", vm.displayName("dressing"))
        }
    }

    @Test
    fun timers_feedback_uses_the_displayed_name() = runBlocking {
        TestServer(token = "jwt", testMode = true).use { t ->
            t.enqueue(200, """{"id":"wc2","minutes":45,"status":"dry_run"}""")
            val vm = LightTimersViewModel(t.container.comfort, recheckMs = listOf(20L), nameOf = { if (it.id == "wc2") "WC Haut" else it.label })
            vm.set(com.essensys.android.data.control.LightTimer.WC2, 45)
            waitUntil { vm.state.value.feedback != null }
            assertTrue(vm.state.value.feedback!!.text, vm.state.value.feedback!!.text.startsWith("WC Haut : "))
        }
        assertEquals(40, LightNames.MAX_LENGTH)
    }
}
