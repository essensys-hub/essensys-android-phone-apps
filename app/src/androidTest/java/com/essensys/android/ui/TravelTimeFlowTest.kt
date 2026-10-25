package com.essensys.android.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.essensys.android.data.session.SessionState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Réglage du temps de course des volets (spec shutter-travel-time). */
@RunWith(AndroidJUnit4::class)
class TravelTimeFlowTest {

    @get:Rule val compose = createComposeRule()
    private val backend = FakeBackend()

    @After fun tearDown() = backend.close()

    private fun screenshot(name: String) {
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "screens").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun exists(tag: String) = runCatching { compose.onNodeWithTag(tag).assertExists() }.isSuccess

    private fun openShutters() {
        compose.setContent { EssensysRoot(backend.container(SessionState(token = "jwt-test"))) }
        compose.waitUntil(5_000) { exists("nav-SHUTTERS") }
        compose.onNodeWithTag("nav-SHUTTERS").performClick()
        compose.waitUntil(5_000) { exists("time-input-volet1salon") }
    }

    @Test
    fun travel_time_is_sent_then_displayed() {
        openShutters()
        compose.onNodeWithTag("time-current-volet1salon").performScrollTo().assertTextEquals("Actuel : 25 s")
        compose.onNodeWithTag("time-current-volet2salon").assertTextEquals("Non transmis (défaut usine : 120 s, store : 255 s)")
        compose.onNodeWithTag("time-input-volet1salon").performScrollTo().performTextInput("30")
        compose.onNodeWithTag("time-save-volet1salon").performClick()
        compose.waitUntil(5_000) { backend.injections.isNotEmpty() }
        assertEquals("""{"k":566,"v":"30"}""", backend.injections.single())
        compose.waitUntil(5_000) {
            runCatching { compose.onNodeWithTag("time-current-volet1salon").assertTextEquals("Actuel : 30 s") }.isSuccess
        }
        compose.onNodeWithTag("time-save-volet2salleamanger").performScrollTo()
        screenshot("14-shutters-travel-time-light")
    }

    @Test
    fun out_of_range_value_is_refused_locally() {
        openShutters()
        compose.onNodeWithTag("time-input-volet1salon").performScrollTo().performTextInput("300")
        compose.onNodeWithTag("time-save-volet1salon").performClick()
        compose.waitUntil(5_000) { exists("feedback") }
        assertTrue(compose.onNodeWithText("Volet 1 Salon : entre 1 et 255 secondes").let { runCatching { it.assertExists() }.isSuccess })
        assertTrue(backend.injections.isEmpty())
    }

    @Test
    fun offline_disables_travel_time_save() {
        backend.gatewayOnline = false
        openShutters()
        compose.waitUntil(5_000) { exists("status-offline") }
        compose.onNodeWithTag("time-input-volet1salon").performScrollTo().performTextInput("30")
        compose.onNodeWithTag("time-save-volet1salon").assertIsNotEnabled()
    }
}
