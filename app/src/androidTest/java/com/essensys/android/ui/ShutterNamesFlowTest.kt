package com.essensys.android.ui

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
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
 * Noms personnalisés des volets et stores contre le backend simulé, sans armoire (spec shutter-names-clients).
 * NR-android-26 : essensys-hub/essensys-feature-lifecycle#41, Feature #52, sub-issue essensys-hub/essensys-android-phone-apps#47.
 */
@RunWith(AndroidJUnit4::class)
class ShutterNamesFlowTest {

    @get:Rule val compose = createComposeRule()
    private val backend = FakeBackend()

    @After fun tearDown() = backend.close()

    private fun exists(tag: String) = runCatching { compose.onNodeWithTag(tag).assertExists() }.isSuccess

    private fun hasText(tag: String, text: String) = runCatching { compose.onNodeWithTag(tag).assertTextContains(text, substring = true) }.isSuccess

    private fun start(admin: Boolean = true) {
        compose.setContent { EssensysRoot(backend.container(SessionState(token = "jwt-test", isAdmin = admin))) }
        compose.waitUntil(5_000) { exists("status-online") || exists("status-offline") }
    }

    private fun openShutters() {
        compose.onNodeWithTag("nav-SHUTTERS").performClick()
        compose.waitUntil(5_000) { exists("shutter-volet1salon-primary") }
    }

    private fun openRename(id: String = "volet1salon") {
        compose.onNodeWithTag("shutter-$id-name-toggle").performScrollTo().performClick()
        compose.waitUntil(5_000) { exists("shutter-$id-name-field") }
    }

    private fun typeName(text: String, id: String = "volet1salon") {
        compose.onNodeWithTag("shutter-$id-name-field").performScrollTo().performTextClearance()
        compose.onNodeWithTag("shutter-$id-name-field").performTextInput(text)
    }

    @Test
    fun le_nom_personnalise_apparait_dans_la_liste_et_dans_temps_de_course() {
        backend.shutterNames["volet1salon"] = "Volet baie vitrée"
        start()
        openShutters()
        compose.waitUntil(5_000) { hasText("shutter-volet1salon-name", "Volet baie vitrée") }
        compose.onNodeWithTag("shutter-volet2salon-name").performScrollTo().assertTextContains("Volet 2 Salon")
        compose.onNodeWithTag("time-name-volet1salon").performScrollTo().assertTextEquals("Volet baie vitrée")
        // Le temps de course reste réglé sur le même indice (566) et affiché pour le même volet.
        compose.onNodeWithTag("time-current-volet1salon").assertTextEquals("Actuel : 25 s")
        assertTrue(backend.requests.any { it.url.encodedPath == "/api/portal/shutters/names" })
    }

    @Test
    fun lecture_impossible_garde_les_noms_d_origine_sans_blocage() {
        backend.namesUnavailable = true
        start()
        openShutters()
        compose.onNodeWithTag("shutter-volet1salon-name").assertTextContains("Volet 1 Salon")
        assertTrue(!exists("feedback"))
    }

    @Test
    fun guillemets_du_nom_d_origine_et_nom_special_affiches_tels_quels() {
        backend.shutterNames["store"] = "Store terrasse"
        start()
        openShutters()
        compose.onNodeWithTag("shutter-voletstore-name").performScrollTo().assertTextEquals("Volet \"Store\"")
        compose.waitUntil(5_000) { hasText("shutter-store-name", "Store terrasse") }
        compose.onNodeWithTag("time-name-store").performScrollTo().assertTextEquals("Store terrasse")
    }

    @Test
    fun administrateur_renomme_puis_remet_le_nom_d_origine() {
        start()
        openShutters()
        openRename()
        typeName("Volet baie vitrée")
        compose.onNodeWithTag("shutter-volet1salon-name-save").performScrollTo().performClick()
        compose.waitUntil(5_000) { backend.shutterNamePuts.isNotEmpty() }
        assertEquals("""/api/portal/admin/shutters/names/volet1salon {"name":"Volet baie vitrée"}""", backend.shutterNamePuts.single())
        compose.waitUntil(5_000) { hasText("shutter-volet1salon-name", "Volet baie vitrée") }
        compose.onNodeWithTag("time-name-volet1salon").performScrollTo().assertTextEquals("Volet baie vitrée")
        if (!exists("shutter-volet1salon-name-reset")) openRename() // le panneau reste ouvert après l'enregistrement
        compose.waitUntil(5_000) { runCatching { compose.onNodeWithTag("shutter-volet1salon-name-reset").assertIsEnabled() }.isSuccess }
        compose.onNodeWithTag("shutter-volet1salon-name-reset").performScrollTo().performClick()
        compose.waitUntil(5_000) { backend.shutterNamePuts.size == 2 }
        compose.waitUntil(5_000) { hasText("shutter-volet1salon-name", "Volet 1 Salon") }
        assertEquals("""/api/portal/admin/shutters/names/volet1salon {"name":null}""", backend.shutterNamePuts.last())
        // Aucun ordre vers l'armoire (spec « Aucun effet sur l'armoire »).
        assertTrue(backend.injections.isEmpty())
        assertTrue(backend.mutations.isEmpty())
        assertTrue(backend.requests.none { it.url.encodedPath.contains("inject") || it.url.encodedPath.contains("web/actions") })
    }

    @Test
    fun nom_de_41_caracteres_refuse_sans_requete() {
        start()
        openShutters()
        openRename()
        typeName("a".repeat(41))
        compose.onNodeWithTag("shutter-volet1salon-name-save").performScrollTo().performClick()
        compose.waitUntil(5_000) { exists("shutter-volet1salon-name-error") }
        compose.onNodeWithTag("shutter-volet1salon-name-error").assertTextContains("1 à 40", substring = true)
        compose.waitForIdle()
        assertTrue(backend.shutterNamePuts.isEmpty())
    }

    @Test
    fun erreur_403_affiche_un_message_clair() {
        backend.nameForbidden = true
        start()
        openShutters()
        openRename()
        typeName("Volet baie vitrée")
        compose.onNodeWithTag("shutter-volet1salon-name-save").performScrollTo().performClick()
        compose.waitUntil(5_000) { runCatching { compose.onNode(hasText("administrateur", substring = true)).assertExists() }.isSuccess }
    }

    @Test
    fun pas_de_champ_pour_un_utilisateur_simple() {
        backend.shutterNames["volet1salon"] = "Volet baie vitrée"
        start(admin = false)
        openShutters()
        assertTrue(!exists("shutter-volet1salon-name-toggle"))
        assertTrue(!exists("shutter-volet1salon-name-field"))
        compose.waitUntil(5_000) { hasText("shutter-volet1salon-name", "Volet baie vitrée") }
        assertTrue(backend.shutterNamePuts.isEmpty())
    }
}
