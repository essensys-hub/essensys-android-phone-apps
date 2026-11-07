package com.essensys.android.ui

import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.essensys.android.data.control.IndexTable
import com.essensys.android.data.session.SessionState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Disposition des lumières et des volets contre le backend simulé, sans armoire (spec object-layout-clients).
 * NR-android-27 : essensys-hub/essensys-feature-lifecycle#41 (les noms restent inchangés), Feature
 * affichage-objets-2026-11-006 (ticket #55), sub-issue essensys-hub/essensys-android-phone-apps#49.
 */
@RunWith(AndroidJUnit4::class)
class ObjectLayoutFlowTest {

    @get:Rule val compose = createComposeRule()
    private val backend = FakeBackend()

    @After fun tearDown() = backend.close()

    private fun exists(tag: String) = runCatching { compose.onNodeWithTag(tag).assertExists() }.isSuccess

    private fun hasText(tag: String, text: String) = runCatching { compose.onNodeWithTag(tag).assertTextContains(text, substring = true) }.isSuccess

    private fun start(admin: Boolean = true) {
        backend.lightTimers["dressing"] = 15
        backend.lightTimers["wc2"] = 10
        compose.setContent { EssensysRoot(backend.container(SessionState(token = "jwt-test", isAdmin = admin))) }
        compose.waitUntil(5_000) { exists("status-online") || exists("status-offline") }
    }

    private fun openLighting() {
        compose.onNodeWithTag("nav-LIGHTING").performClick()
        compose.waitUntil(5_000) { exists("light-salon-primary") }
    }

    private fun openShutters() {
        compose.onNodeWithTag("nav-SHUTTERS").performClick()
        compose.waitUntil(5_000) { exists("time-name-volet1salon") || exists("shutter-volet1salon-primary") }
    }

    private fun openTimers() {
        compose.onNodeWithTag("nav-HOME").performClick()
        compose.onNodeWithTag("open-LIGHT_TIMERS").performScrollTo().performClick()
        compose.waitUntil(5_000) { exists("timer-dressing-value") || exists("timers-empty") }
    }

    private fun click(tag: String) = compose.onNodeWithTag(tag).performScrollTo().performClick()

    @Test
    fun ancien_serveur_404_affiche_tout_le_catalogue_sans_blocage() {
        backend.layoutAbsent = true
        start()
        openLighting()
        IndexTable.lights.forEach { assertTrue(it.id, exists("light-${it.id}-name")) }
        assertTrue(!exists("feedback"))
        openShutters()
        compose.waitUntil(5_000) { exists("shutter-voletbureau-name") }
        IndexTable.shutters.forEach { assertTrue(it.id, exists("shutter-${it.id}-name")) }
    }

    @Test
    fun lumiere_desactivee_absente_de_la_liste_de_tout_allumer_et_des_temporisations() {
        backend.lightLayout.disabled = listOf("wc2")
        backend.lightLayout.version = 1
        start(admin = false)
        openLighting()
        compose.waitUntil(5_000) { !exists("light-wc2-name") }
        assertTrue(exists("light-wc1-name"))
        click("group-MAIN-all-on")
        compose.waitUntil(5_000) { backend.injections.size >= 20 }
        compose.waitForIdle()
        assertEquals(20, backend.injections.size)
        // wc2 : indice 615, masque 64 (IndexTable) — aucune commande pour elle.
        assertTrue(backend.injections.none { it == """{"k":615,"v":"64"}""" })
        // Les Temporisations (réservées à l'administrateur) sont vérifiées dans le test suivant.
    }

    @Test
    fun temporisations_sans_la_lumiere_desactivee_et_suivant_l_ordre() {
        backend.lightLayout.disabled = listOf("wc2")
        backend.lightLayout.order = listOf("ann2", "dressing")
        backend.lightLayout.version = 1
        start()
        // Laisser la disposition se charger avant d'ouvrir l'écran.
        openLighting()
        compose.waitUntil(5_000) { !exists("light-wc2-name") }
        openTimers()
        assertTrue(exists("timer-wc1-value"))
        assertTrue(!exists("timer-wc2-value"))
        assertTrue(!exists("timer-wc2-name"))
        compose.onNodeWithTag("timer-ann2-name").assertExists()
        assertTrue(backend.timerPuts.isEmpty())
    }

    @Test
    fun groupe_sans_objet_actif_affiche_le_texte_et_aucun_bouton() {
        backend.lightLayout.disabled = IndexTable.lights.filter { it.group == IndexTable.Group.INDIRECT }.map { it.id }
        backend.lightLayout.version = 1
        start(admin = false)
        openLighting()
        compose.waitUntil(5_000) { exists("group-INDIRECT-empty") }
        compose.onNodeWithTag("group-INDIRECT-empty").performScrollTo().assertTextEquals("Aucune lumière activée")
        assertTrue(!exists("group-INDIRECT-all-on"))
        assertTrue(exists("group-MAIN-all-on"))
    }

    @Test
    fun volet_desactive_absent_de_la_liste_de_tout_ouvrir_et_de_temps_de_course() {
        backend.shutterLayout.disabled = listOf("voletbureau")
        backend.shutterLayout.version = 1
        backend.exchange[571] = "40"
        start(admin = false)
        openShutters()
        compose.waitUntil(5_000) { !exists("shutter-voletbureau-name") }
        assertTrue(!exists("time-name-voletbureau"))
        // `testTag` de TravelTimeFlowTest inchangés pour les volets actifs.
        compose.onNodeWithTag("time-current-volet1salon").performScrollTo().assertTextEquals("Actuel : 25 s")
        click("group-MAIN-all-on")
        compose.waitUntil(5_000) { backend.injections.size >= 13 }
        compose.waitForIdle()
        assertEquals(13, backend.injections.size)
        assertTrue(backend.injections.none { it == """{"k":617,"v":"32"}""" }) // voletbureau : 617 / masque 32
        // La lecture des temps ne demande pas le temps du volet désactivé (indice 571).
        val keys = backend.requests.filter { it.url.encodedPath.endsWith("/exchange") }.map { it.url.queryParameter("keys").orEmpty() }
        assertTrue(keys.toString(), keys.isNotEmpty() && keys.none { it.split(",").contains("571") })
    }

    @Test
    fun ordre_choisi_suivi_par_la_liste_et_la_carte_temps_de_course() {
        backend.shutterLayout.order = listOf("volet2salon", "volet1salon")
        backend.shutterLayout.version = 1
        start(admin = false)
        openShutters()
        compose.waitUntil(5_000) {
            val first = runCatching { compose.onNodeWithTag("time-name-volet2salon").fetchSemanticsNode().positionInRoot.y }.getOrNull()
            val second = runCatching { compose.onNodeWithTag("time-name-volet1salon").fetchSemanticsNode().positionInRoot.y }.getOrNull()
            first != null && second != null && first < second
        }
    }

    @Test
    fun administrateur_desactive_monte_et_enregistre_en_une_seule_ecriture() {
        start()
        openLighting()
        click("layout-lights-manage")
        compose.waitUntil(5_000) { exists("layout-lights-card") }
        // « sam » monte au-dessus de « salon » ; « wc2 » est désactivée ; tout reste local jusqu'à « Enregistrer ».
        click("layout-lights-up-sam")
        click("layout-lights-switch-wc2")
        compose.onNodeWithTag("layout-lights-state-wc2").performScrollTo().assertTextEquals("Désactivé")
        compose.waitForIdle()
        assertTrue(backend.layoutPuts.isEmpty())
        // Tête et queue de groupe : Monter désactivé pour « terrasse », Descendre pour « dressing ».
        compose.onNodeWithTag("layout-lights-up-terrasse").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("layout-lights-down-dressing").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("layout-lights-up-sam").performScrollTo().assertIsEnabled()
        click("layout-lights-save")
        compose.waitUntil(5_000) { backend.layoutPuts.isNotEmpty() }
        compose.waitUntil(20_000) { !exists("layout-lights-card") }
        assertEquals(1, backend.layoutPuts.size)
        val put = backend.layoutPuts.single()
        assertTrue(put, put.startsWith("/api/portal/admin/lights/layout "))
        assertTrue(put, put.contains(""""order":["terrasse","entree","escalier","deg1","deg2","pieceserv","ann1","ann2","sam","salon""""))
        assertTrue(put, put.endsWith(""""disabled":["wc2"]}"""))
        // wc2 disparaît de la liste, sam précède salon.
        compose.waitUntil(5_000) { !exists("light-wc2-name") }
        val sam = compose.onNodeWithTag("light-sam-name").fetchSemanticsNode().positionInRoot.y
        val salon = compose.onNodeWithTag("light-salon-name").fetchSemanticsNode().positionInRoot.y
        assertTrue(sam < salon)
        // Aucun ordre vers l'armoire, noms inchangés.
        assertTrue(backend.injections.isEmpty())
        assertTrue(backend.mutations.isEmpty())
        assertTrue(backend.requests.none { it.url.encodedPath.contains("inject") || it.url.encodedPath.contains("web/actions") })
        assertTrue(backend.namePuts.isEmpty())
        compose.onNodeWithTag("light-salon-name").assertTextContains("Salon")
    }

    @Test
    fun reactivation_remet_la_lumiere_a_sa_position() {
        backend.lightLayout.disabled = listOf("wc2")
        backend.lightLayout.version = 1
        start()
        openLighting()
        compose.waitUntil(5_000) { !exists("light-wc2-name") }
        click("layout-lights-manage")
        compose.waitUntil(5_000) { exists("layout-lights-row-wc2") }
        // La lumière désactivée est listée dans l'écran de gestion.
        compose.onNodeWithTag("layout-lights-state-wc2").performScrollTo().assertTextEquals("Désactivé")
        click("layout-lights-switch-wc2")
        click("layout-lights-save")
        compose.waitUntil(5_000) { backend.layoutPuts.isNotEmpty() }
        compose.waitUntil(5_000) { exists("light-wc2-name") }
        assertTrue(backend.layoutPuts.single().endsWith(""""disabled":[]}"""))
    }

    @Test
    fun ordre_d_origine_envoie_reset_true_et_conserve_les_noms() {
        backend.lightNames["dressing"] = "WC 3"
        backend.lightLayout.order = listOf("dressing")
        backend.lightLayout.disabled = listOf("wc2")
        backend.lightLayout.version = 2
        start()
        openLighting()
        compose.waitUntil(5_000) { !exists("light-wc2-name") }
        click("layout-lights-manage")
        compose.waitUntil(5_000) { exists("layout-lights-reset") }
        click("layout-lights-reset")
        click("layout-lights-save")
        compose.waitUntil(5_000) { backend.layoutPuts.isNotEmpty() }
        assertEquals("""/api/portal/admin/lights/layout {"reset":true}""", backend.layoutPuts.single())
        compose.waitUntil(5_000) { exists("light-wc2-name") }
        compose.onNodeWithTag("light-dressing-name").performScrollTo().assertTextContains("WC 3")
        val terrasse = compose.onNodeWithTag("light-terrasse-name").fetchSemanticsNode().positionInRoot.y
        val entree = compose.onNodeWithTag("light-entree-name").fetchSemanticsNode().positionInRoot.y
        assertTrue(terrasse < entree)
    }

    @Test
    fun volets_gestion_ecriture_et_erreur_403_garde_l_ecran_ouvert() {
        backend.layoutForbidden = true
        start()
        openShutters()
        click("layout-shutters-manage")
        compose.waitUntil(5_000) { exists("layout-shutters-card") }
        click("layout-shutters-switch-voletbureau")
        click("layout-shutters-save")
        compose.waitUntil(5_000) { runCatching { compose.onNode(androidx.compose.ui.test.hasText("administrateur", substring = true)).assertExists() }.isSuccess }
        assertTrue(exists("layout-shutters-card")) // l'écran reste ouvert avec les modifications
        compose.onNodeWithTag("layout-shutters-state-voletbureau").performScrollTo().assertTextEquals("Désactivé")
        backend.layoutForbidden = false
        click("layout-shutters-save")
        compose.waitUntil(5_000) { backend.layoutPuts.size == 2 }
        compose.waitUntil(5_000) { !exists("shutter-voletbureau-name") }
        assertTrue(backend.layoutPuts.last().startsWith("/api/portal/admin/shutters/layout "))
    }

    @Test
    fun utilisateur_simple_sans_acces_a_l_ecran_de_gestion_ni_aux_objets_desactives() {
        backend.lightLayout.disabled = listOf("wc2")
        backend.lightLayout.version = 1
        start(admin = false)
        openLighting()
        compose.waitUntil(5_000) { !exists("light-wc2-name") }
        assertTrue(!exists("layout-lights-manage"))
        assertTrue(!exists("layout-lights-card"))
        openShutters()
        assertTrue(!exists("layout-shutters-manage"))
        assertTrue(backend.layoutPuts.isEmpty())
    }
}
