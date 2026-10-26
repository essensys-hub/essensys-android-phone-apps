package com.essensys.android.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.essensys.android.data.session.SessionState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Écran Scénarios contre le backend simulé (spec scenarios, parity-quality « Tests applicatifs »). */
@RunWith(AndroidJUnit4::class)
class ScenariosFlowTest {

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

    private fun openScenarios() {
        compose.setContent { EssensysRoot(backend.container(SessionState(token = "jwt-test"))) }
        compose.waitUntil(5_000) { exists("open-SCENARIOS") }
        compose.onNodeWithTag("open-SCENARIOS").performScrollTo().performClick()
        compose.waitUntil(5_000) { exists("launch-2") }
    }

    @Test
    fun launch_je_sors_and_back_to_home() {
        openScenarios()
        compose.onNodeWithTag("last-2").assertExists()
        screenshot("11-scenarios-light")
        compose.onNodeWithTag("launch-2").performScrollTo().performClick()
        compose.waitUntil(5_000) { backend.mutations.isNotEmpty() }
        assertEquals(listOf("POST /api/portal/scenarios/2/launch"), backend.mutations.toList())
        compose.onNodeWithTag("back").performClick()
        compose.onNodeWithTag("open-SCENARIOS").assertExists()
    }

    @Test
    fun restore_requires_confirmation() {
        openScenarios()
        compose.onNodeWithTag("restore-4").performScrollTo().performClick()
        compose.onNodeWithTag("restore-cancel").performClick()
        assertTrue(backend.mutations.isEmpty())
        compose.onNodeWithTag("restore-4").performScrollTo().performClick()
        compose.onNodeWithTag("restore-confirm").performClick()
        compose.waitUntil(5_000) { backend.mutations.isNotEmpty() }
        assertEquals("POST /api/portal/scenarios/4/restore", backend.mutations.single())
        assertFalse(exists("restore-7")) // pas de réglages d'usine pour les personnalisés
    }

    @Test
    fun edit_scenario_saves_full_block_with_shifted_index() {
        openScenarios()
        compose.onNodeWithTag("edit-4").performScrollTo().performClick()
        compose.waitUntil(5_000) { exists("bit-605-2") }
        compose.onNodeWithTag("bit-605-2").performScrollTo().performClick()
        compose.onNodeWithTag("editor-save").performScrollTo().performClick()
        compose.waitUntil(5_000) { backend.mutations.isNotEmpty() }
        assertEquals("PUT /api/portal/scenarios/4", backend.mutations.single())
        val body = backend.mutationBodies.single()
        assertTrue(body, body.contains("\"728\":\"2\""))
        assertFalse(body, body.contains("\"605\""))
    }

    @Test
    fun edit_scenario_security_outlets_choice_saves_offset_31() {
        openScenarios()
        compose.onNodeWithTag("edit-4").performScrollTo().performClick()
        compose.waitUntil(5_000) { exists("scenario-security-CUT") }
        compose.onNodeWithTag("scenario-security-CUT").performScrollTo().performClick()
        compose.onNodeWithTag("editor-save").performScrollTo().performClick()
        compose.waitUntil(5_000) { backend.mutations.isNotEmpty() }
        val body = backend.mutationBodies.single()
        assertTrue(body, body.contains("\"746\":\"1\"")) // scénario 4 : 715 + 31
    }
}
