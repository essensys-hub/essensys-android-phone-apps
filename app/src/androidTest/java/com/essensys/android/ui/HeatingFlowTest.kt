package com.essensys.android.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.essensys.android.data.session.ConnectionMode
import com.essensys.android.data.session.SessionState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Écran Chauffage contre le backend simulé (spec parity-quality « Tests applicatifs »). */
@RunWith(AndroidJUnit4::class)
class HeatingFlowTest {

    @get:Rule val compose = createComposeRule()
    private val backend = FakeBackend()

    @After fun tearDown() = backend.close()

    private fun screenshot(name: String) {
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "screens").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun shown(tag: String) = runCatching { compose.onNodeWithTag(tag).assertIsDisplayed() }.isSuccess

    /** Le bandeau de retour est en haut de la colonne : présent même s'il est hors de la zone visible. */
    private fun exists(tag: String) = runCatching { compose.onNodeWithTag(tag).assertExists() }.isSuccess

    private fun openHeating(state: SessionState = SessionState(token = "jwt-test")) {
        compose.setContent { EssensysRoot(backend.container(state)) }
        compose.waitUntil(5_000) { shown("nav-HEATING") }
        compose.onNodeWithTag("nav-HEATING").performClick()
        compose.waitUntil(5_000) { backend.requests.any { it.url.encodedPath.endsWith("/exchange") } }
    }

    @Test
    fun edit_slot_then_save_sends_expected_batch() {
        openHeating()
        compose.onNodeWithTag("day-0").performScrollTo().performClick()
        compose.onNodeWithTag("brush-CONFORT").performScrollTo().performClick()
        compose.onNodeWithTag("hour-6").performScrollTo().performClick()
        compose.onNodeWithTag("brush-ECO").performScrollTo().performClick()
        compose.onNodeWithTag("hour-7").performScrollTo().performClick()
        compose.onNodeWithTag("heating-pending").performScrollTo().assertIsDisplayed()
        screenshot("09-heating-light")
        compose.onNodeWithTag("heating-save").performScrollTo().performClick()
        compose.waitUntil(5_000) { backend.injections.isNotEmpty() && exists("feedback") }
        assertEquals("""{"params":[{"k":16,"v":"33"}]}""", backend.injections.single())
        assertTrue(backend.requests.any { it.url.encodedPath == "/api/portal/inject/batch" })
    }

    @Test
    fun failed_save_keeps_changes_for_retry() {
        backend.failNextInject = true
        openHeating()
        compose.onNodeWithTag("hour-10").performScrollTo().performClick()
        compose.onNodeWithTag("heating-save").performScrollTo().performClick()
        compose.waitUntil(5_000) { exists("feedback") }
        // Le reste à envoyer est toujours en attente : Enregistrer reste disponible.
        compose.onNodeWithTag("heating-pending").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("heating-save").performScrollTo().performClick()
        compose.waitUntil(5_000) { backend.injections.size == 2 }
        assertEquals(backend.injections[0], backend.injections[1])
    }

    // Spec : forçage confort en salle de bain 1 = k=351, v="17" ; Anticipé absent des salles de bain.
    @Test
    fun immediate_mode_for_bathroom_sends_mode_index() {
        openHeating()
        compose.onNodeWithTag("zone-sdb1").performClick()
        compose.waitUntil(5_000) { backend.requests.count { it.url.encodedPath.endsWith("/exchange") } >= 2 }
        assertTrue(runCatching { compose.onNodeWithTag("mode-ANTICIPATED").assertExists() }.isFailure)
        compose.onNodeWithTag("mode-FORCE_CONFORT").performScrollTo().performClick()
        compose.waitUntil(5_000) { backend.injections.isNotEmpty() }
        assertEquals("""{"k":351,"v":"17"}""", backend.injections.single())
    }

    @Test
    fun offline_gateway_keeps_schedule_readable_but_disables_save() {
        backend.gatewayOnline = false
        openHeating()
        compose.waitUntil(5_000) { shown("status-offline") }
        compose.onNodeWithTag("hour-3").performScrollTo().performClick()
        compose.onNodeWithTag("heating-save").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("mode-AUTO").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun lan_sync_rereads_schedule_from_armoire() {
        backend.exchange[13] = "33"
        openHeating(SessionState(mode = ConnectionMode.LAN, lanCookie = "abc"))
        compose.onNodeWithTag("heating-sync").performScrollTo().performClick()
        compose.waitUntil(10_000) { backend.requests.any { it.url.encodedPath == "/api/admin/heating/sync/status" } && exists("feedback") }
        assertTrue(backend.requests.any { it.url.encodedPath == "/api/admin/heating/sync" })
    }
}
