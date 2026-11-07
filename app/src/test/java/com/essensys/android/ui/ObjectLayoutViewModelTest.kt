package com.essensys.android.ui

import com.essensys.android.data.control.IndexTable
import com.essensys.android.support.TestServer
import com.essensys.android.ui.components.Feedback
import com.essensys.android.ui.screens.LightLayoutViewModel
import com.essensys.android.ui.screens.ShutterLayoutViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * États de la disposition : repli, dernière lecture conservée, brouillon de l'écran de gestion, écriture unique
 * (spec object-layout-clients). NR-android-27 : essensys-hub/essensys-feature-lifecycle#41, sub-issue
 * essensys-hub/essensys-android-phone-apps#49.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ObjectLayoutViewModelTest {

    @Before fun setUp() = Dispatchers.setMain(Dispatchers.Unconfined)
    @After fun tearDown() = Dispatchers.resetMain()

    private suspend fun waitUntil(block: () -> Boolean) = withTimeout(10_000) { while (!block()) delay(10) }

    private fun doc(order: String, disabled: String, version: Int, status: String? = null) =
        """{"order":[$order],"disabled":[$disabled],"version":$version,"updated_at":"2026-11-07T10:15:30.123Z"${status?.let { ""","status":"$it"""" }.orEmpty()}}"""

    @Test
    fun a_404_shows_the_full_catalogue_in_the_original_order_without_a_message() = runBlocking {
        TestServer(token = "jwt").use { t ->
            val vm = LightLayoutViewModel(t.container.comfort)
            t.enqueue(404, "")
            vm.load()
            waitUntil { vm.state.value.settled }
            assertEquals(IndexTable.lights.map { it.id }, vm.effective().ordered)
            assertTrue(vm.effective().disabled.isEmpty())
            assertNull(vm.state.value.feedback)
        }
    }

    @Test
    fun the_last_successful_read_is_kept_when_a_later_read_fails() = runBlocking {
        TestServer(token = "jwt").use { t ->
            val vm = LightLayoutViewModel(t.container.comfort)
            t.enqueue(200, doc("", "\"wc2\"", 1))
            vm.load()
            waitUntil { "wc2" in vm.effective().disabled }
            t.enqueue(500, "boom")
            vm.load()
            waitUntil { t.server.requestCount == 2 }
            delay(50)
            assertTrue("un objet masqué ne réapparaît pas sur une coupure", "wc2" in vm.effective().disabled)
        }
    }

    @Test
    fun editing_is_local_until_save_then_one_single_put_closes_the_screen() = runBlocking {
        TestServer(token = "jwt").use { t ->
            val vm = LightLayoutViewModel(t.container.comfort)
            vm.startEdit()
            vm.toggle("wc2")
            vm.move("sam", -1)
            assertEquals(0, t.server.requestCount) // rien n'est envoyé avant « Enregistrer »
            assertEquals(setOf("wc2"), vm.state.value.draft!!.layout.disabled)
            t.enqueue(200, doc("\"sam\",\"salon\"", "\"wc2\"", 1, "saved"))
            vm.save()
            waitUntil { vm.state.value.draft == null }
            assertEquals(1, t.server.requestCount)
            val request = t.server.takeRequest()
            assertEquals("/api/portal/admin/lights/layout", request.url.encodedPath)
            val body = request.body!!.utf8()
            assertTrue(body, body.contains(""""order":["terrasse","entree","escalier","deg1","deg2","pieceserv","ann1","ann2","sam","salon""""))
            assertTrue(body, body.endsWith(""""disabled":["wc2"]}"""))
            assertEquals(Feedback.Kind.SUCCESS, vm.state.value.feedback!!.kind)
            assertTrue("wc2" in vm.effective().disabled)
        }
    }

    @Test
    fun origin_order_is_saved_as_reset_true_and_a_later_edit_cancels_the_reset() = runBlocking {
        TestServer(token = "jwt").use { t ->
            val vm = ShutterLayoutViewModel(t.container.comfort)
            vm.startEdit()
            vm.toggle("store")
            vm.resetToOrigin()
            assertTrue(vm.state.value.draft!!.reset)
            assertTrue(vm.state.value.draft!!.layout.disabled.isEmpty())
            t.enqueue(200, doc("", "", 2, "saved"))
            vm.save()
            waitUntil { vm.state.value.draft == null }
            assertEquals("""{"reset":true}""", t.server.takeRequest().body!!.utf8())
            // Après « Ordre d'origine », un déplacement envoie bien le document complet.
            vm.startEdit()
            vm.resetToOrigin()
            vm.toggle("store")
            assertFalse(vm.state.value.draft!!.reset)
        }
    }

    @Test
    fun errors_403_400_and_503_keep_the_screen_open_with_the_changes() = runBlocking {
        TestServer(token = "jwt").use { t ->
            val vm = ShutterLayoutViewModel(t.container.comfort)
            vm.startEdit()
            vm.toggle("voletbureau")
            t.enqueue(403, """{"error":"forbidden"}""")
            vm.save()
            waitUntil { vm.state.value.feedback?.text?.contains("administrateur") == true && !vm.state.value.saving }
            assertNotNull(vm.state.value.draft)
            assertTrue("voletbureau" in vm.state.value.draft!!.layout.disabled)
            t.enqueue(400, """{"error":"invalid_layout"}""")
            vm.save()
            waitUntil { vm.state.value.feedback?.text?.contains("invalide") == true && !vm.state.value.saving }
            t.enqueue(503, """{"error":"names_unavailable"}""")
            vm.save()
            waitUntil { vm.state.value.feedback?.text?.contains("indisponible") == true && !vm.state.value.saving }
            assertNotNull(vm.state.value.draft)
            assertTrue(vm.state.value.feedback!!.kind == Feedback.Kind.ERROR)
            vm.cancelEdit()
            assertNull(vm.state.value.draft)
            assertTrue(vm.effective().disabled.isEmpty()) // rien d'appliqué tant que rien n'est enregistré
        }
    }

    @Test
    fun dry_run_reports_validated_without_applying() = runBlocking {
        TestServer(token = "jwt", testMode = true).use { t ->
            val vm = LightLayoutViewModel(t.container.comfort)
            vm.startEdit()
            vm.toggle("wc2")
            t.enqueue(200, doc("", "\"wc2\"", 1, "dry_run"))
            vm.save()
            waitUntil { vm.state.value.feedback?.kind == Feedback.Kind.INFO }
            assertTrue(vm.effective().disabled.isEmpty())
            assertNotNull(vm.state.value.draft)
        }
    }
}
