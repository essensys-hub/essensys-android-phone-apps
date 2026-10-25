package com.essensys.android.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.essensys.android.data.session.SessionState
import com.essensys.android.ui.screens.Destination
import com.essensys.android.ui.theme.ThemePreference
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Accueil, navigation et Réglages (spec home-dashboard). */
@RunWith(AndroidJUnit4::class)
class HomeFlowTest {

    @get:Rule val compose = createComposeRule()
    private val backend = FakeBackend()

    @After fun tearDown() = backend.close()

    private fun exists(tag: String) = runCatching { compose.onNodeWithTag(tag).assertExists() }.isSuccess

    private fun screenshot(name: String) {
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "screens").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun start(state: SessionState = SessionState(token = "jwt-test")) {
        compose.setContent { EssensysRoot(backend.container(state)) }
        compose.waitUntil(5_000) { exists("quick-2") }
    }

    @Test
    fun five_tabs_in_spec_order_and_no_coming_soon_card() {
        start()
        assertEquals(listOf("HOME", "LIGHTING", "SHUTTERS", "HEATING", "SETTINGS"), Destination.entries.map { it.name })
        Destination.entries.forEach { compose.onNodeWithTag("nav-${it.name}").assertExists() }
        assertTrue(compose.onAllNodesWithText("Bientôt disponible").fetchSemanticsNodes().isEmpty())
        screenshot("02-home-light")
        listOf("open-lighting", "open-shutters", "open-heating", "open-SCENARIOS", "open-WATER_HEATER", "open-SPRINKLER", "open-ALARM")
            .forEach { compose.onNodeWithTag(it).performScrollTo().assertExists() }
    }

    @Test
    fun tile_opens_pushed_screen_and_back_returns_home() {
        start()
        compose.onNodeWithTag("open-WATER_HEATER").performScrollTo().performClick()
        compose.waitUntil(5_000) { exists("heater-OFF") }
        compose.onNodeWithTag("back").performClick()
        compose.waitUntil(5_000) { exists("quick-2") }
        // Une tuile d'onglet bascule sur l'onglet.
        compose.onNodeWithTag("open-heating").performScrollTo().performClick()
        compose.waitUntil(5_000) { exists("zone-jour") }
    }

    @Test
    fun quick_scenario_launches_in_one_tap() {
        start()
        // Le bouton n'est actif qu'une fois l'état « en ligne » reçu : on l'attend avant d'appuyer.
        compose.waitUntil(5_000) { exists("status-online") }
        compose.onNodeWithTag("quick-2").performClick()
        compose.waitUntil(5_000) { backend.mutations.isNotEmpty() }
        assertEquals("POST /api/portal/scenarios/2/launch", backend.mutations.single())
    }

    @Test
    fun offline_disables_quick_scenarios_but_tiles_stay_open() {
        backend.gatewayOnline = false
        start()
        compose.waitUntil(5_000) { exists("status-offline") }
        compose.onNodeWithTag("quick-2").assertIsNotEnabled()
        compose.onNodeWithTag("open-SPRINKLER").performScrollTo().performClick()
        compose.waitUntil(5_000) { exists("sprinkler-AUTO") }
        compose.onNodeWithTag("sprinkler-AUTO").assertIsNotEnabled()
    }

    @Test
    fun settings_show_account_and_armoire_in_cloud() {
        start(SessionState(token = "jwt-test", theme = ThemePreference.DARK))
        compose.onNodeWithTag("nav-SETTINGS").performClick()
        compose.waitUntil(5_000) { exists("account-armoire") }
        compose.onNodeWithTag("account-user").assertExists()
        assertTrue(compose.onAllNodesWithText("Camille Démo — demo@essensys.fr").fetchSemanticsNodes().isNotEmpty())
        assertTrue(compose.onAllNodesWithText("SC944D-0001", substring = true).fetchSemanticsNodes().isNotEmpty())
        screenshot("10-settings-account-dark")
    }
}
