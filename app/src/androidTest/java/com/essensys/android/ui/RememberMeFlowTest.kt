package com.essensys.android.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.essensys.android.data.session.ConnectionMode
import com.essensys.android.data.session.SessionState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** « Se souvenir de moi » sur émulateur (tâches 1.3, 2.1 et 2.3 de session-30-jours-2026-11-001), backend simulé. */
@RunWith(AndroidJUnit4::class)
class RememberMeFlowTest {

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

    private fun login() {
        compose.onNodeWithTag("email").performTextInput("demo@essensys.fr")
        compose.onNodeWithTag("password").performTextInput("secret123")
        compose.onNodeWithTag("login").performClick()
        compose.waitUntil(5_000) { exists("open-lighting") }
    }

    private fun openSettings() {
        compose.onNodeWithTag("nav-SETTINGS").performClick()
        compose.waitUntil(5_000) { exists("logout") }
    }

    private fun loginBody() = backend.requests.last { it.url.encodedPath == "/api/auth/login" }.body!!.utf8()

    @Test
    fun the_box_is_unchecked_by_default_in_both_modes() {
        compose.setContent { EssensysRoot(backend.container()) }
        compose.onNodeWithTag("remember-me").assertIsOff()
        compose.onNodeWithTag("mode-lan").performClick()
        compose.onNodeWithTag("remember-me").assertIsDisplayed().assertIsOff()
        compose.onNodeWithTag("mode-cloud").performClick()
        compose.onNodeWithTag("remember-me").assertIsOff()
    }

    @Test
    fun a_checked_box_is_sent_and_settings_show_the_end_date() {
        compose.setContent { EssensysRoot(backend.container()) }
        compose.onNodeWithTag("remember-me").performClick()
        compose.onNodeWithTag("remember-me").assertIsOn()
        screenshot("14-login-remember-light")
        login()
        assertTrue(loginBody().contains("\"remember_me\":true"))
        openSettings()
        val expected = backend.rememberExpiresAt.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
        compose.onNodeWithTag("remember-until").performScrollTo().assertTextEquals("Connecté jusqu'au $expected")
        screenshot("15-settings-remember-light")
    }

    @Test
    fun an_unchecked_box_sends_no_option_and_settings_show_no_date() {
        compose.setContent { EssensysRoot(backend.container()) }
        login()
        assertFalse(loginBody().contains("remember_me"))
        openSettings()
        compose.onNodeWithTag("remember-until").assertDoesNotExist()
    }

    @Test
    fun a_server_without_remember_support_logs_in_normally_without_a_date() {
        backend.rememberSupported = false
        compose.setContent { EssensysRoot(backend.container()) }
        compose.onNodeWithTag("remember-me").performClick()
        login()
        openSettings()
        compose.onNodeWithTag("remember-until").assertDoesNotExist()
    }

    @Test
    fun after_logout_the_box_is_unchecked_again_and_the_remembered_session_is_revoked() {
        compose.setContent { EssensysRoot(backend.container()) }
        compose.onNodeWithTag("remember-me").performClick()
        login()
        openSettings()
        compose.onNodeWithTag("logout").performScrollTo().performClick()
        compose.waitUntil(5_000) { exists("login") }
        compose.onNodeWithTag("remember-me").assertIsOff()
        compose.waitUntil(5_000) { backend.requests.any { it.url.encodedPath == "/api/auth/logout" } }
        val logout = backend.requests.last { it.url.encodedPath == "/api/auth/logout" }
        assertTrue(logout.body!!.utf8().contains("\"remember_token\":\"remember-fake-0123456789\""))
    }

    @Test
    fun an_expired_access_is_renewed_without_showing_the_login_screen() {
        backend.unauthorizedTokens = setOf("jwt-old")
        val state = SessionState(token = "jwt-old", rememberToken = "remember-fake-0123456789")
        compose.setContent { EssensysRoot(backend.container(state)) }
        compose.waitUntil(10_000) { exists("open-lighting") }
        assertEquals(1, backend.refreshCount.get())
        compose.onNodeWithTag("login").assertDoesNotExist()
    }

    @Test
    fun a_session_that_has_ended_sends_back_to_login_with_a_message() {
        backend.unauthorizedTokens = setOf("jwt-old")
        // Date de fin passée : l'app renonce sans même interroger le serveur.
        val state = SessionState(token = "jwt-old", rememberToken = "remember-fake-0123456789", rememberExpiresAtMs = 1L)
        compose.setContent { EssensysRoot(backend.container(state)) }
        compose.waitUntil(10_000) { exists("session-ended") }
        compose.onNodeWithTag("session-ended").assertIsDisplayed()
        compose.onNodeWithTag("remember-me").assertIsOff()
        assertEquals(0, backend.refreshCount.get())
    }

    @Test
    fun the_lan_login_screen_offers_the_box_too() {
        compose.setContent { EssensysRoot(backend.container(SessionState(mode = ConnectionMode.LAN))) }
        compose.onNodeWithTag("remember-me").assertIsDisplayed().assertIsOff()
    }
}
