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
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
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

/** Temporisations des lumières contre le backend simulé (spec light-timers-clients). */
@RunWith(AndroidJUnit4::class)
class LightTimersFlowTest {

    @get:Rule val compose = createComposeRule()
    private val backend = FakeBackend()

    @After fun tearDown() = backend.close()

    private fun exists(tag: String) = runCatching { compose.onNodeWithTag(tag).assertExists() }.isSuccess

    private fun hasText(tag: String, text: String) = runCatching { compose.onNodeWithTag(tag).assertTextContains(text, substring = true) }.isSuccess

    private fun screenshot(name: String) {
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "screens").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun start(admin: Boolean = true) {
        backend.lightTimers["dressing"] = 15
        backend.lightTimers["wc2"] = 0
        backend.lightTimers["ann2"] = 7
        compose.setContent { EssensysRoot(backend.container(SessionState(token = "jwt-test", isAdmin = admin))) }
        compose.waitUntil(5_000) { exists("status-online") || exists("status-offline") }
    }

    private fun openTimers() {
        compose.onNodeWithTag("open-LIGHT_TIMERS").performScrollTo().performClick()
        compose.waitUntil(5_000) { exists("timer-dressing-value") }
    }

    private fun openDressing() {
        compose.onNodeWithTag("nav-LIGHTING").performClick()
        compose.waitUntil(5_000) { exists("light-dressing-timer-toggle") }
        compose.onNodeWithTag("light-dressing-timer-toggle").performScrollTo().performClick()
        compose.waitUntil(5_000) { exists("timer-dressing-free") }
    }

    @Test
    fun liste_les_six_lumieres_avec_leur_delai() {
        start()
        openTimers()
        compose.waitUntil(5_000) { hasText("timer-dressing-value", "15 min") }
        compose.onNodeWithTag("timer-wc1-value").performScrollTo().assertTextContains("inconnue")
        compose.onNodeWithTag("timer-wc2-value").assertTextContains("Jamais")
        compose.onNodeWithTag("timer-pieceserv-value").assertTextContains("inconnue")
        compose.onNodeWithTag("timer-ann1-value").assertTextContains("inconnue")
        compose.onNodeWithTag("timer-ann2-value").assertTextContains("7 min") // non raccourci : tel quel
        screenshot("21-light-timers-light")
    }

    @Test
    fun raccourci_envoie_la_valeur_au_chemin_cloud_admin() {
        start()
        openTimers()
        compose.onNodeWithTag("timer-wc1-60").performScrollTo().performClick()
        compose.waitUntil(5_000) { backend.timerPuts.isNotEmpty() }
        assertEquals("""/api/portal/admin/lights/timers/wc1 {"minutes":60}""", backend.timerPuts.single())
        assertTrue(backend.injections.isEmpty()) // pas d'injection générique
    }

    @Test
    fun dressing_se_regle_dans_la_fiche_de_la_lumiere_avec_valeur_libre_7() {
        start()
        openDressing()
        compose.waitUntil(5_000) { hasText("light-dressing-timer-toggle", "15 min") }
        compose.onNodeWithTag("timer-dressing-free").performScrollTo().performTextInput("7")
        compose.onNodeWithTag("timer-dressing-send").performScrollTo().performClick()
        compose.waitUntil(5_000) { backend.timerPuts.isNotEmpty() }
        assertEquals("""/api/portal/admin/lights/timers/dressing {"minutes":7}""", backend.timerPuts.single())
        // Relecture à 5 s : la valeur de l'armoire s'affiche telle quelle.
        compose.waitUntil(12_000) { hasText("timer-dressing-value", "7 min") }
        compose.waitUntil(5_000) { hasText("light-dressing-timer-toggle", "7 min") }
        screenshot("22-light-timer-dressing-light")
    }

    @Test
    fun valeurs_refusees_300_et_0_sans_requete() {
        start()
        openDressing()
        for (bad in listOf("300", "0")) {
            compose.onNodeWithTag("timer-dressing-free").performScrollTo().performTextInput(bad)
            compose.onNodeWithTag("timer-dressing-send").performScrollTo().performClick()
            compose.waitUntil(5_000) { exists("timer-dressing-error") }
            compose.onNodeWithTag("timer-dressing-error").assertTextContains("entre 1 et 255", substring = true)
            clearField()
        }
        compose.waitForIdle()
        assertTrue(backend.timerPuts.isEmpty())
    }

    private fun clearField() {
        compose.onNodeWithTag("timer-dressing-free").performTextClearance()
    }

    @Test
    fun masque_pour_un_utilisateur_simple() {
        start(admin = false)
        compose.waitForIdle()
        assertTrue(!exists("open-LIGHT_TIMERS"))
        compose.onNodeWithTag("nav-LIGHTING").performClick()
        compose.waitUntil(5_000) { exists("light-dressing-primary") }
        assertTrue(!exists("light-dressing-timer-toggle"))
        assertTrue(!exists("light-wc1-timer-toggle"))
        assertTrue(backend.requests.none { it.url.encodedPath.contains("lights/timers") })
    }

    @Test
    fun reglage_absent_des_lumieres_sans_temporisation() {
        start()
        compose.onNodeWithTag("nav-LIGHTING").performClick()
        compose.waitUntil(5_000) { exists("light-dressing-timer-toggle") }
        for (id in listOf("wc1", "wc2", "pieceserv", "ann1", "ann2")) compose.onNodeWithTag("light-$id-timer-toggle").performScrollTo().assertExists()
        assertTrue(!exists("light-salon-timer-toggle"))
        assertTrue(!exists("light-terrasse-timer-toggle"))
    }

    @Test
    fun commandes_desactivees_hors_ligne() {
        backend.gatewayOnline = false
        start()
        compose.waitUntil(5_000) { exists("status-offline") }
        openTimers()
        compose.onNodeWithTag("timers-offline").assertExists()
        compose.onNodeWithTag("timer-dressing-30").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("timer-dressing-send").assertIsNotEnabled()
    }

    @Test
    fun commandes_actives_en_ligne() {
        start()
        openTimers()
        compose.onNodeWithTag("timer-dressing-30").performScrollTo().assertIsEnabled()
    }
}
