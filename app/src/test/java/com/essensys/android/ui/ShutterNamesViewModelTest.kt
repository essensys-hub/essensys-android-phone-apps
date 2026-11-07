package com.essensys.android.ui

import com.essensys.android.data.control.IndexTable
import com.essensys.android.data.control.ShutterNames
import com.essensys.android.support.TestServer
import com.essensys.android.ui.components.Feedback
import com.essensys.android.ui.screens.ControlViewModel
import com.essensys.android.ui.screens.ShutterNamesViewModel
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

/** Écran des noms de volets : repli, renommage, remise à zéro, validation, nom dans les messages (spec shutter-names-clients). */
@OptIn(ExperimentalCoroutinesApi::class)
class ShutterNamesViewModelTest {

    @Before fun setUp() = Dispatchers.setMain(Dispatchers.Unconfined)
    @After fun tearDown() = Dispatchers.resetMain()

    private suspend fun waitUntil(block: () -> Boolean) = withTimeout(10_000) { while (!block()) delay(10) }

    private fun saved(name: String?, version: Int) =
        """{"id":"volet1salon","default":"Volet 1 Salon","name":${name?.let { "\"$it\"" } ?: "null"},"version":$version,"updated_at":"2026-11-07T10:15:30.123Z","status":"saved"}"""

    @Test
    fun before_and_after_a_failed_read_the_original_names_are_shown() = runBlocking {
        TestServer(token = "jwt").use { t ->
            val vm = ShutterNamesViewModel(t.container.comfort)
            assertEquals("Volet 1 Salon", vm.displayName("volet1salon"))
            t.enqueue(500, "boom")
            vm.load()
            waitUntil { t.server.requestCount == 1 }
            delay(50)
            assertEquals("Volet 1 Salon", vm.displayName("volet1salon"))
            assertEquals(null, vm.state.value.feedback) // pas de message bloquant
        }
    }

    @Test
    fun loaded_custom_name_is_displayed() = runBlocking {
        TestServer(token = "jwt").use { t ->
            t.enqueue(200, """{"shutters":[{"id":"volet1salon","default":"Volet 1 Salon","name":"Volet baie vitrée","version":2,"updated_at":"2026-11-07T10:15:30.123Z"}]}""")
            val vm = ShutterNamesViewModel(t.container.comfort)
            vm.load()
            waitUntil { vm.displayName("volet1salon") == "Volet baie vitrée" }
            assertEquals("Volet Bureau", vm.displayName("voletbureau"))
        }
    }

    @Test
    fun rename_updates_the_display_without_reload() = runBlocking {
        TestServer(token = "jwt").use { t ->
            t.enqueue(200, saved("Volet baie vitrée", 2))
            val vm = ShutterNamesViewModel(t.container.comfort)
            vm.rename("volet1salon", " Volet baie vitrée ")
            waitUntil { vm.displayName("volet1salon") == "Volet baie vitrée" && vm.state.value.saving.isEmpty() }
            assertEquals(Feedback.Kind.SUCCESS, vm.state.value.feedback!!.kind)
            assertEquals(1, t.server.requestCount) // pas de relecture
        }
    }

    @Test
    fun reset_brings_back_the_original_name() = runBlocking {
        TestServer(token = "jwt").use { t ->
            t.enqueue(200, saved("Volet baie vitrée", 2))
            t.enqueue(200, saved(null, 3))
            val vm = ShutterNamesViewModel(t.container.comfort)
            vm.rename("volet1salon", "Volet baie vitrée")
            waitUntil { vm.displayName("volet1salon") == "Volet baie vitrée" }
            vm.reset("volet1salon")
            waitUntil { vm.displayName("volet1salon") == "Volet 1 Salon" && vm.state.value.saving.isEmpty() }
            t.server.takeRequest()
            assertEquals("""{"name":null}""", t.server.takeRequest().body!!.utf8())
        }
    }

    @Test
    fun invalid_name_is_refused_with_a_message_and_no_request() = runBlocking {
        TestServer(token = "jwt").use { t ->
            val vm = ShutterNamesViewModel(t.container.comfort)
            for (bad in listOf("", "a".repeat(41), "<x>")) vm.rename("volet1salon", bad)
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
            val vm = ShutterNamesViewModel(t.container.comfort)
            t.enqueue(403, """{"error":"forbidden"}""")
            vm.rename("volet1salon", "Volet baie vitrée")
            waitUntil { vm.state.value.feedback?.text?.contains("administrateur") == true }
            t.enqueue(503, """{"error":"names_unavailable"}""")
            vm.rename("volet1salon", "Volet baie vitrée")
            waitUntil { vm.state.value.feedback?.text?.contains("indisponible") == true }
            assertEquals("Volet 1 Salon", vm.displayName("volet1salon"))
        }
    }

    @Test
    fun travel_time_feedback_uses_the_displayed_name() = runBlocking {
        TestServer(token = "jwt", testMode = true).use { t ->
            t.enqueue(200, """{"ok":true}""")
            val shutter = IndexTable.shutters.first { it.id == "store" }
            val vm = ControlViewModel(t.container.control, nameOf = { if (it.id == "store") "Store terrasse" else it.name })
            assertTrue(vm.saveTravelTime(shutter, "40"))
            waitUntil { vm.feedback.value != null }
            assertTrue(vm.feedback.value!!.text, vm.feedback.value!!.text.startsWith("Store terrasse : "))
            // saisie invalide : le message cite aussi le nom affiché
            vm.saveTravelTime(shutter, "999")
            assertTrue(vm.feedback.value!!.text, vm.feedback.value!!.text.startsWith("Store terrasse : "))
        }
        assertEquals(40, ShutterNames.MAX_LENGTH)
    }
}
