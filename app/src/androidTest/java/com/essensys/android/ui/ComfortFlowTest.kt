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
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.essensys.android.data.session.ConnectionMode
import com.essensys.android.data.session.SessionState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Chauffe-eau, arrosage et alarme contre le backend simulé (specs comfort-controls, alarm-control). */
@RunWith(AndroidJUnit4::class)
class ComfortFlowTest {

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

    private fun open(feature: String, state: SessionState = SessionState(token = "jwt-test")) {
        compose.setContent { EssensysRoot(backend.container(state)) }
        compose.waitUntil(5_000) { exists("open-$feature") }
        compose.onNodeWithTag("open-$feature").performScrollTo().performClick()
    }

    @Test
    fun water_heater_off_peak_sends_firmware_index() {
        backend.exchange[353] = "2"
        open("WATER_HEATER")
        compose.waitUntil(5_000) { exists("heater-OFF_PEAK") }
        screenshot("12-water-heater-light")
        compose.onNodeWithTag("heater-OFF_PEAK").performClick()
        compose.waitUntil(5_000) { backend.injections.isNotEmpty() }
        assertEquals("""{"k":353,"v":"1"}""", backend.injections.single())
    }

    @Test
    fun sprinkler_30_minutes_sends_firmware_index() {
        open("SPRINKLER")
        compose.waitUntil(5_000) { exists("sprinkler-MINUTES_30") }
        compose.onNodeWithTag("sprinkler-MINUTES_30").performClick()
        compose.waitUntil(5_000) { backend.injections.isNotEmpty() }
        assertEquals("""{"k":363,"v":"30"}""", backend.injections.single())
    }

    @Test
    fun alarm_on_sends_code_once_then_clears_field() {
        open("ALARM")
        compose.waitUntil(5_000) { exists("alarm-code") }
        compose.onNodeWithTag("alarm-on").assertIsNotEnabled()
        compose.onNodeWithTag("alarm-code").performTextInput("12ab34")
        compose.onNodeWithTag("alarm-on").performClick()
        compose.waitUntil(5_000) { backend.mutations.isNotEmpty() }
        assertEquals("POST /api/portal/web/actions", backend.mutations.single())
        assertEquals("""{"alarme":"on","codealarme":"1234"}""", backend.mutationBodies.single())
        compose.onNodeWithTag("alarm-on").assertIsNotEnabled() // champ vidé après envoi
        compose.onNodeWithTag("alarm-reminder").assertExists()
    }

    @Test
    fun alarm_off_requires_confirmation() {
        open("ALARM")
        compose.waitUntil(5_000) { exists("alarm-code") }
        compose.onNodeWithTag("alarm-code").performTextInput("4321")
        compose.onNodeWithTag("alarm-off").performClick()
        compose.onNodeWithTag("alarm-off-cancel").performClick()
        assertTrue(backend.mutations.isEmpty())
        compose.onNodeWithTag("alarm-off").performClick()
        compose.onNodeWithTag("alarm-off-confirm").performClick()
        compose.waitUntil(5_000) { backend.mutations.isNotEmpty() }
        assertEquals("""{"alarme":"off","codealarme":"4321"}""", backend.mutationBodies.single())
    }

    @Test
    fun alarm_is_cloud_only_in_lan() {
        open("ALARM", SessionState(mode = ConnectionMode.LAN, lanCookie = "abc"))
        compose.waitUntil(5_000) { exists("alarm-cloud-only") }
        assertFalse(exists("alarm-code"))
    }
}
