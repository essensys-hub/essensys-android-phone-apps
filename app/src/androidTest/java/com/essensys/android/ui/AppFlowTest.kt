package com.essensys.android.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.essensys.android.data.session.ConnectionMode
import com.essensys.android.data.session.SessionState
import com.essensys.android.ui.theme.ThemePreference
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Parcours V1 sur émulateur, contre un backend simulé (spec android-quality « Tests unitaires et instrumentés »). */
@RunWith(AndroidJUnit4::class)
class AppFlowTest {

    @get:Rule val compose = createComposeRule()
    private val backend = FakeBackend()

    @After fun tearDown() = backend.close()

    private fun screenshot(name: String) {
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "screens").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun login() {
        compose.onNodeWithTag("email").performTextInput("demo@essensys.fr")
        compose.onNodeWithTag("password").performTextInput("secret123")
        compose.onNodeWithTag("login").performClick()
    }

    @Test
    fun cloud_login_opens_home_with_gateway_status() {
        compose.setContent { EssensysRoot(backend.container()) }
        screenshot("01-login-light")
        login()
        compose.waitUntil(5_000) { runCatching { compose.onNodeWithTag("status-online").assertIsDisplayed() }.isSuccess }
        compose.onNodeWithTag("open-lighting").assertIsDisplayed()
        screenshot("02-home-light")
    }

    // NR: NR-android-8 essensys-hub/essensys-android-phone-apps#4
    @Test
    fun lighting_command_is_sent_once_on_double_tap_NR_android_8() {
        backend.injectDelayMs = 800
        compose.setContent { EssensysRoot(backend.container(SessionState(token = "jwt-test"))) }
        compose.waitUntil(5_000) { runCatching { compose.onNodeWithTag("nav-LIGHTING").assertIsDisplayed() }.isSuccess }
        compose.onNodeWithTag("nav-LIGHTING").performClick()
        compose.onNodeWithTag("light-salon-primary").performScrollTo()
        compose.onNodeWithTag("light-salon-primary").performClick()
        compose.onNodeWithTag("light-salon-primary").performClick()
        compose.waitUntil(5_000) { runCatching { compose.onNodeWithTag("feedback").assertIsDisplayed() }.isSuccess }
        assertEquals(listOf("""{"k":612,"v":"128"}"""), backend.injections.toList())
        compose.onNodeWithText("Salon : commande envoyée — l'armoire exécute sous ~5 s").assertIsDisplayed()
        screenshot("03-lighting-light")
    }

    @Test
    fun shutters_screen_sends_kitchen_close_and_shows_travel_time() {
        compose.setContent { EssensysRoot(backend.container(SessionState(token = "jwt-test"))) }
        compose.waitUntil(5_000) { runCatching { compose.onNodeWithTag("nav-SHUTTERS").assertIsDisplayed() }.isSuccess }
        compose.onNodeWithTag("nav-SHUTTERS").performClick()
        compose.onNodeWithText("Temps de course : 25 s").assertIsDisplayed()
        compose.onNodeWithTag("shutter-volet1cuisine-secondary").performScrollTo().performClick()
        compose.waitUntil(5_000) { backend.injections.isNotEmpty() }
        assertEquals("""{"k":622,"v":"1"}""", backend.injections.single())
        screenshot("04-shutters-light")
    }

    @Test
    fun offline_gateway_disables_commands() {
        backend.gatewayOnline = false
        compose.setContent { EssensysRoot(backend.container(SessionState(token = "jwt-test"))) }
        compose.waitUntil(5_000) { runCatching { compose.onNodeWithTag("status-offline").assertIsDisplayed() }.isSuccess }
        compose.onNodeWithTag("nav-LIGHTING").performClick()
        compose.onNodeWithTag("light-salon-primary").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun temporary_password_forces_change_before_home() {
        backend.temporaryPassword = true
        compose.setContent { EssensysRoot(backend.container()) }
        login()
        compose.waitUntil(5_000) { runCatching { compose.onNodeWithTag("change-password").assertIsDisplayed() }.isSuccess }
        screenshot("05-password-change")
        compose.onNodeWithTag("current-password").performTextInput("secret123")
        compose.onNodeWithTag("new-password").performTextInput("Nouveau-pass-456")
        compose.onNodeWithTag("change-password").performClick()
        compose.waitUntil(5_000) { runCatching { compose.onNodeWithTag("open-lighting").assertIsDisplayed() }.isSuccess }
    }

    @Test
    fun account_without_armoire_sees_link_screen() {
        backend.linked = false
        compose.setContent { EssensysRoot(backend.container(SessionState(token = "jwt-test"))) }
        compose.waitUntil(5_000) { runCatching { compose.onNodeWithTag("request-link").assertIsDisplayed() }.isSuccess }
        screenshot("06-link")
        assertTrue(backend.requests.none { it.url.encodedPath == "/api/portal/inject" })
    }

    @Test
    fun lan_host_in_cleartext_is_refused() {
        compose.setContent { EssensysRoot(backend.container(SessionState(mode = ConnectionMode.LAN))) }
        compose.onNodeWithTag("lan-host").performTextClearance()
        compose.onNodeWithTag("lan-host").performTextInput("http://mon.essensys.local")
        login()
        compose.onNodeWithText("Seule une connexion sécurisée (https://) est possible.").assertIsDisplayed()
        assertTrue(backend.requests.isEmpty())
    }

    @Test
    fun test_mode_shows_banner_and_dry_run_message() {
        compose.setContent { EssensysRoot(backend.container(SessionState(token = "jwt-test", testMode = true))) }
        compose.waitUntil(5_000) { runCatching { compose.onNodeWithTag("test-banner").assertIsDisplayed() }.isSuccess }
        compose.onNodeWithTag("nav-LIGHTING").performClick()
        compose.onNodeWithTag("light-salon-primary").performScrollTo().performClick()
        compose.waitUntil(5_000) { runCatching { compose.onNodeWithText("Salon : commande validée (test, non exécutée)").assertIsDisplayed() }.isSuccess }
        assertEquals("dry-run", backend.requests.last { it.url.encodedPath == "/api/portal/inject" }.headers["X-Essensys-Test-Mode"])
    }

    @Test
    fun dark_theme_renders_lighting_and_settings() {
        compose.setContent { EssensysRoot(backend.container(SessionState(token = "jwt-test", theme = ThemePreference.DARK))) }
        compose.waitUntil(5_000) { runCatching { compose.onNodeWithTag("nav-LIGHTING").assertIsDisplayed() }.isSuccess }
        compose.onNodeWithTag("nav-LIGHTING").performClick()
        screenshot("07-lighting-dark")
        compose.onNodeWithTag("nav-SETTINGS").performClick()
        compose.onNodeWithTag("logout").assertIsDisplayed()
        screenshot("08-settings-dark")
    }
}
