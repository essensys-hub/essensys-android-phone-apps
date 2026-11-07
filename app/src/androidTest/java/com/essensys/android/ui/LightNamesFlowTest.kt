package com.essensys.android.ui

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.essensys.android.data.session.SessionState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Noms personnalisés des lumières contre le backend simulé, sans armoire (spec light-names-clients).
 * NR-android-25 : essensys-hub/essensys-feature-lifecycle#41, sub-issue essensys-hub/essensys-android-phone-apps#45.
 */
@RunWith(AndroidJUnit4::class)
class LightNamesFlowTest {

    @get:Rule val compose = createComposeRule()
    private val backend = FakeBackend()

    @After fun tearDown() = backend.close()

    private fun exists(tag: String) = runCatching { compose.onNodeWithTag(tag).assertExists() }.isSuccess

    private fun hasText(tag: String, text: String) = runCatching { compose.onNodeWithTag(tag).assertTextContains(text, substring = true) }.isSuccess

    private fun start(admin: Boolean = true) {
        backend.lightTimers["dressing"] = 15
        compose.setContent { EssensysRoot(backend.container(SessionState(token = "jwt-test", isAdmin = admin))) }
        compose.waitUntil(5_000) { exists("status-online") || exists("status-offline") }
    }

    private fun openLighting() {
        compose.onNodeWithTag("nav-LIGHTING").performClick()
        compose.waitUntil(5_000) { exists("light-dressing-primary") }
    }

    private fun openRename(id: String = "dressing") {
        compose.onNodeWithTag("light-$id-name-toggle").performScrollTo().performClick()
        compose.waitUntil(5_000) { exists("light-$id-name-field") }
    }

    private fun typeName(text: String, id: String = "dressing") {
        compose.onNodeWithTag("light-$id-name-field").performScrollTo().performTextClearance()
        compose.onNodeWithTag("light-$id-name-field").performTextInput(text)
    }

    @Test
    fun le_nom_personnalise_apparait_dans_la_liste_et_dans_temporisations() {
        backend.lightNames["dressing"] = "WC 3"
        start()
        openLighting()
        compose.waitUntil(5_000) { hasText("light-dressing-name", "WC 3") }
        compose.onNodeWithTag("light-wc1-name").performScrollTo().assertTextContains("WC 1")
        compose.onNodeWithTag("nav-HOME").performClick()
        compose.onNodeWithTag("open-LIGHT_TIMERS").performScrollTo().performClick()
        compose.waitUntil(5_000) { exists("timer-dressing-value") }
        // L'écran Temporisations utilise le nom affiché, pas le libellé « Dressing » de /lights/timers.
        compose.onNodeWithTag("timer-dressing-name").assertTextContains("WC 3")
        assertTrue(backend.requests.any { it.url.encodedPath == "/api/portal/lights/names" })
    }

    @Test
    fun lecture_impossible_garde_les_noms_d_origine_sans_blocage() {
        backend.namesUnavailable = true
        start()
        openLighting()
        compose.onNodeWithTag("light-dressing-name").assertTextContains("Dressing")
        assertTrue(!exists("feedback"))
    }

    @Test
    fun administrateur_renomme_puis_remet_le_nom_d_origine() {
        start()
        openLighting()
        openRename()
        typeName("WC 3")
        compose.onNodeWithTag("light-dressing-name-save").performScrollTo().performClick()
        compose.waitUntil(5_000) { backend.namePuts.isNotEmpty() }
        assertEquals("""/api/portal/admin/lights/names/dressing {"name":"WC 3"}""", backend.namePuts.single())
        compose.waitUntil(5_000) { hasText("light-dressing-name", "WC 3") }
        if (!exists("light-dressing-name-reset")) openRename() // le panneau reste ouvert après l'enregistrement
        compose.waitUntil(5_000) { runCatching { compose.onNodeWithTag("light-dressing-name-reset").assertIsEnabled() }.isSuccess }
        compose.onNodeWithTag("light-dressing-name-reset").performScrollTo().performClick()
        compose.waitUntil(5_000) { backend.namePuts.size == 2 }
        compose.waitUntil(5_000) { hasText("light-dressing-name", "Dressing") }
        assertEquals("""/api/portal/admin/lights/names/dressing {"name":null}""", backend.namePuts.last())
        // Aucun ordre vers l'armoire (spec « Aucun effet sur l'armoire »).
        assertTrue(backend.injections.isEmpty())
        assertTrue(backend.mutations.isEmpty())
        assertTrue(backend.requests.none { it.url.encodedPath.contains("inject") || it.url.encodedPath.contains("web/actions") })
    }

    @Test
    fun nom_de_41_caracteres_refuse_sans_requete() {
        start()
        openLighting()
        openRename()
        typeName("a".repeat(41))
        compose.onNodeWithTag("light-dressing-name-save").performScrollTo().performClick()
        compose.waitUntil(5_000) { exists("light-dressing-name-error") }
        compose.onNodeWithTag("light-dressing-name-error").assertTextContains("1 à 40", substring = true)
        compose.waitForIdle()
        assertTrue(backend.namePuts.isEmpty())
    }

    @Test
    fun erreur_403_affiche_un_message_clair() {
        backend.nameForbidden = true
        start()
        openLighting()
        openRename()
        typeName("WC 3")
        compose.onNodeWithTag("light-dressing-name-save").performScrollTo().performClick()
        compose.waitUntil(5_000) { runCatching { compose.onNode(hasText("administrateur", substring = true)).assertExists() }.isSuccess }
    }

    @Test
    fun pas_de_champ_pour_un_utilisateur_simple() {
        backend.lightNames["dressing"] = "WC 3"
        start(admin = false)
        openLighting()
        assertTrue(!exists("light-dressing-name-toggle"))
        assertTrue(!exists("light-dressing-name-field"))
        compose.waitUntil(5_000) { hasText("light-dressing-name", "WC 3") }
        assertTrue(backend.namePuts.isEmpty())
    }
}
