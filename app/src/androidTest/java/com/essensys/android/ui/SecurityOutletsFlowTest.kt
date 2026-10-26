package com.essensys.android.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.essensys.android.data.session.SessionState
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Prises de sécurité contre le backend simulé (spec security-outlets). */
@RunWith(AndroidJUnit4::class)
class SecurityOutletsFlowTest {

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

    private fun openOutlets(waitOnline: Boolean = true) {
        compose.setContent { EssensysRoot(backend.container(SessionState(token = "jwt-test"))) }
        compose.waitUntil(5_000) { exists(if (waitOnline) "status-online" else "status-offline") }
        compose.onNodeWithTag("open-SECURITY_OUTLETS").performScrollTo().performClick()
        compose.waitUntil(5_000) { exists("outlets-cut") }
    }

    @Test
    fun couper_envoie_590_et_623_a_1_apres_confirmation() {
        backend.exchange[440] = "0"
        openOutlets()
        compose.waitUntil(5_000) { runCatching { compose.onNodeWithTag("outlets-state").assertTextContains("Prises pilotées") }.isSuccess }
        screenshot("20-security-outlets-light")
        compose.onNodeWithTag("outlets-cut").performClick()
        compose.onNodeWithTag("outlets-cut-confirm").performClick()
        compose.waitUntil(5_000) { backend.injections.isNotEmpty() }
        assertEquals("""{"params":[{"k":590,"v":"1"},{"k":623,"v":"1"}]}""", backend.injections.single())
        compose.waitUntil(5_000) { runCatching { compose.onNodeWithTag("outlets-state").assertTextContains("En attente de l'armoire") }.isSuccess }
    }

    @Test
    fun annuler_la_confirmation_n_envoie_rien() {
        openOutlets()
        compose.onNodeWithTag("outlets-cut").performClick()
        compose.onNodeWithTag("outlets-cut-cancel").performClick()
        compose.waitForIdle()
        assertTrue(backend.injections.isEmpty())
        compose.onNodeWithTag("outlets-cut").assertIsEnabled()
    }

    @Test
    fun retablir_envoie_590_et_623_a_2() {
        openOutlets()
        compose.onNodeWithTag("outlets-restore").performClick()
        compose.waitUntil(5_000) { backend.injections.isNotEmpty() }
        assertEquals("""{"params":[{"k":590,"v":"1"},{"k":623,"v":"2"}]}""", backend.injections.single())
    }

    @Test
    fun commandes_desactivees_hors_ligne() {
        backend.gatewayOnline = false
        openOutlets(waitOnline = false)
        compose.onNodeWithTag("outlets-offline").assertExists()
        compose.onNodeWithTag("outlets-cut").assertIsNotEnabled()
        compose.onNodeWithTag("outlets-restore").assertIsNotEnabled()
    }

    @Test
    fun etat_inconnu_sans_indice_440() {
        openOutlets()
        compose.waitForIdle()
        compose.onNodeWithTag("outlets-state").assertTextContains("État inconnu")
        compose.onNodeWithTag("outlets-restore").assertIsEnabled()
    }
}
