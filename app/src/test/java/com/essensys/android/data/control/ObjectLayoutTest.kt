package com.essensys.android.data.control

import com.essensys.android.data.http.ApiError
import com.essensys.android.data.http.ApiResult
import com.essensys.android.data.session.ConnectionMode
import com.essensys.android.support.TestServer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Disposition des lumières et des volets (spec object-layout-clients) : règle commune `effectiveLayout`, filtre par
 * groupe, déplacement, validation et appels cloud / réseau local contre un serveur simulé, sans armoire.
 * NR-android-27 : essensys-hub/essensys-feature-lifecycle#41 (les noms restent inchangés), Feature
 * affichage-objets-2026-11-006 (ticket #55), sub-issue essensys-hub/essensys-android-phone-apps#49.
 */
class ObjectLayoutTest {

    private val lightIds = IndexTable.lights.map { it.id }
    private val shutterIds = IndexTable.shutters.map { it.id }

    private fun layout(order: List<String> = emptyList(), disabled: List<String> = emptyList()) = ObjectLayout(order, disabled, 1, "2026-11-07T10:15:30.123Z")

    private fun mainLights(l: EffectiveLayout) = l.visible(IndexTable.lights.filter { it.group == IndexTable.Group.MAIN }) { it.id }.map { it.id }

    // --- règle commune ---

    @Test
    fun default_layout_is_the_full_catalogue_in_the_original_order() {
        val e = ObjectLayouts.effective(LayoutKind.LIGHTS, ObjectLayout.DEFAULT)
        assertEquals(lightIds, e.ordered)
        assertTrue(e.disabled.isEmpty())
        assertEquals(33, e.ordered.size)
        assertEquals(16, ObjectLayouts.effective(LayoutKind.SHUTTERS, ObjectLayout.DEFAULT).ordered.size)
    }

    @Test
    fun incomplete_order_with_an_old_id_puts_salon_first_and_ignores_the_old_id() {
        val e = ObjectLayouts.effective(LayoutKind.LIGHTS, layout(order = listOf("salon", "ancien-identifiant")))
        assertEquals("salon", mainLights(e).first())
        assertEquals(lightIds.filter { it != "salon" }, e.ordered.drop(1))
        assertFalse("ancien-identifiant" in e.ordered)
    }

    @Test
    fun unknown_ids_in_disabled_are_ignored_and_duplicates_in_order_count_once() {
        val e = ObjectLayouts.effective(LayoutKind.LIGHTS, layout(order = listOf("wc2", "wc2", "salon"), disabled = listOf("inconnu", "wc2")))
        assertEquals(setOf("wc2"), e.disabled)
        assertEquals(listOf("wc2", "salon"), e.ordered.take(2))
        assertEquals(33, e.ordered.size)
    }

    @Test
    fun a_shutter_id_in_a_light_layout_is_ignored() {
        val e = ObjectLayouts.effective(LayoutKind.LIGHTS, layout(order = listOf("volet1salon", "salon"), disabled = listOf("store")))
        assertFalse("volet1salon" in e.ordered)
        assertTrue(e.disabled.isEmpty())
    }

    // --- masquage ---

    @Test
    fun a_disabled_light_is_absent_from_its_group_list_so_from_group_commands() {
        val e = ObjectLayouts.effective(LayoutKind.LIGHTS, layout(disabled = listOf("wc2")))
        val main = e.visible(IndexTable.lights.filter { it.group == IndexTable.Group.MAIN }) { it.id }
        assertEquals(20, main.size)
        assertFalse(main.any { it.id == "wc2" })
        // Les commandes de groupe sont construites sur cette même liste : 20 commandes, aucune pour wc2.
        val commands = main.map { it.command(true) }
        assertEquals(20, commands.size)
        assertEquals(12, e.visible(IndexTable.lights.filter { it.group == IndexTable.Group.INDIRECT }) { it.id }.size)
    }

    @Test
    fun a_disabled_shutter_disappears_and_the_rest_keeps_the_global_order() {
        val e = ObjectLayouts.effective(LayoutKind.SHUTTERS, layout(disabled = listOf("voletbureau")))
        val active = e.visible(IndexTable.shutters) { it.id }.map { it.id }
        assertEquals(shutterIds.filter { it != "voletbureau" }, active)
    }

    @Test
    fun all_disabled_leaves_every_group_empty() {
        val e = ObjectLayouts.effective(LayoutKind.LIGHTS, layout(disabled = lightIds))
        assertTrue(mainLights(e).isEmpty())
        assertTrue(e.visible(LightTimer.entries) { it.id }.isEmpty())
        assertEquals("Aucune lumière activée", ObjectLayouts.emptyText(LayoutKind.LIGHTS))
        assertEquals("Aucun volet activé", ObjectLayouts.emptyText(LayoutKind.SHUTTERS))
    }

    @Test
    fun timers_follow_the_global_order_and_skip_disabled_lights() {
        val e = ObjectLayouts.effective(LayoutKind.LIGHTS, layout(order = listOf("ann2", "dressing"), disabled = listOf("wc2")))
        assertEquals(listOf("ann2", "dressing", "pieceserv", "ann1", "wc1"), e.visible(LightTimer.entries) { it.id }.map { it.id })
    }

    // --- ordre ---

    @Test
    fun order_applies_inside_each_group_and_never_moves_an_item_across_groups() {
        // L'ordre global mêle les deux groupes : chaque groupe garde ses propres membres.
        val e = ObjectLayouts.effective(LayoutKind.LIGHTS, layout(order = listOf("isalonind", "sam", "salon")))
        val main = mainLights(e)
        assertEquals(listOf("sam", "salon"), main.take(2))
        assertEquals(21, main.size)
        val indirect = e.visible(IndexTable.lights.filter { it.group == IndexTable.Group.INDIRECT }) { it.id }.map { it.id }
        assertEquals("isalonind", indirect.first())
        assertEquals(12, indirect.size)
    }

    @Test
    fun shutter_order_is_followed_by_the_travel_time_list() {
        val e = ObjectLayouts.effective(LayoutKind.SHUTTERS, layout(order = listOf("volet2salon", "volet1salon")))
        assertEquals(listOf("volet2salon", "volet1salon", "volet3salon"), e.visible(IndexTable.shutters) { it.id }.map { it.id }.take(3))
    }

    @Test
    fun move_swaps_with_the_neighbour_of_the_same_group() {
        val base = ObjectLayouts.effective(LayoutKind.LIGHTS, ObjectLayout.DEFAULT)
        val up = base.move("sam", -1, LayoutKind.LIGHTS::groupOf)
        assertEquals(listOf("sam", "salon"), up.ordered.filter { it == "salon" || it == "sam" })
        // « isalonind » est en tête de son groupe : monter ne change rien, même s'il suit « dressing » dans le catalogue.
        assertFalse(base.canMove("isalonind", -1, LayoutKind.LIGHTS::groupOf))
        assertTrue(base.move("isalonind", -1, LayoutKind.LIGHTS::groupOf) === base)
        assertFalse(base.canMove("dressing", 1, LayoutKind.LIGHTS::groupOf))
        assertTrue(base.canMove("dressing", -1, LayoutKind.LIGHTS::groupOf))
        assertFalse(base.canMove("store", 1, LayoutKind.SHUTTERS::groupOf))
        assertFalse(base.canMove("voletstore", -1, LayoutKind.SHUTTERS::groupOf))
    }

    @Test
    fun move_skips_over_members_of_another_group_that_sit_between() {
        // « isalonind » (indirect) placé entre « entree » et « escalier » : « escalier » monte au-dessus de « entree ».
        val e = EffectiveLayout(listOf("terrasse", "entree", "isalonind", "escalier"), emptySet())
        val moved = e.move("escalier", -1, LayoutKind.LIGHTS::groupOf)
        assertEquals(listOf("terrasse", "escalier", "isalonind", "entree"), moved.ordered)
    }

    // --- validation avant l'envoi ---

    @Test
    fun validation_refuses_unknown_ids_cross_genre_ids_and_duplicates() {
        assertTrue(ObjectLayouts.validate(LayoutKind.LIGHTS, lightIds, listOf("wc2")).isSuccess)
        assertEquals("unknown_light", (ObjectLayouts.validate(LayoutKind.LIGHTS, listOf("x"), emptyList()).exceptionOrNull() as ObjectLayouts.LayoutException).code)
        assertEquals("unknown_shutter", (ObjectLayouts.validate(LayoutKind.SHUTTERS, emptyList(), listOf("wc2")).exceptionOrNull() as ObjectLayouts.LayoutException).code)
        assertEquals("invalid_layout", (ObjectLayouts.validate(LayoutKind.LIGHTS, listOf("salon", "salon"), emptyList()).exceptionOrNull() as ObjectLayouts.LayoutException).code)
        assertEquals("invalid_layout", (ObjectLayouts.validate(LayoutKind.LIGHTS, emptyList(), listOf("wc2", "wc2")).exceptionOrNull() as ObjectLayouts.LayoutException).code)
    }

    @Test
    fun error_messages_cover_400_403_and_503() {
        assertTrue(ObjectLayouts.errorMessage(ApiError.Forbidden("forbidden", null)).contains("administrateur"))
        assertTrue(ObjectLayouts.errorMessage(ApiError.Http(400, "unknown_light", null)).contains("Lumière inconnue"))
        assertTrue(ObjectLayouts.errorMessage(ApiError.Http(400, "unknown_shutter", null)).contains("Volet inconnu"))
        assertTrue(ObjectLayouts.errorMessage(ApiError.Http(400, "invalid_layout", null)).contains("invalide"))
        assertTrue(ObjectLayouts.errorMessage(ApiError.Http(503, "names_unavailable", null)).contains("indisponible"))
    }

    // --- lecture ---

    @Test
    fun cloud_read_uses_the_portal_path_and_decodes_the_document() = runBlocking {
        TestServer(token = "jwt").use { t ->
            t.enqueue(200, """{"order":["salon","entree"],"disabled":["wc2"],"version":3,"updated_at":"2026-11-07T10:15:30.123Z"}""")
            val result = (t.container.comfort.readLightLayout() as ApiResult.Ok).value
            val request = t.server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/api/portal/lights/layout", request.url.encodedPath)
            assertEquals(listOf("salon", "entree"), result.order)
            assertEquals(listOf("wc2"), result.disabled)
            assertEquals(3, result.version)
        }
    }

    @Test
    fun lan_read_uses_the_gateway_path_for_shutters() = runBlocking {
        TestServer(mode = ConnectionMode.LAN).use { t ->
            t.store.update { it.copy(lanCookie = "abc") }
            t.enqueue(200, """{"order":[],"disabled":[],"version":0,"updated_at":null}""")
            assertEquals(ObjectLayout.DEFAULT, (t.container.comfort.readShutterLayout() as ApiResult.Ok).value)
            assertEquals("/api/shutters/layout", t.server.takeRequest().url.encodedPath)
        }
    }

    @Test
    fun a_404_or_a_broken_answer_is_an_error_so_the_caller_shows_the_full_catalogue() = runBlocking {
        TestServer(token = "jwt").use { t ->
            t.enqueue(404, "")
            assertTrue(t.container.comfort.readLightLayout() is ApiResult.Err)
            t.enqueue(500, "boom")
            assertTrue(t.container.comfort.readShutterLayout() is ApiResult.Err)
            t.enqueue(200, "pas du json")
            assertTrue(t.container.comfort.readLightLayout() is ApiResult.Err)
        }
        assertEquals(lightIds, ObjectLayouts.effective(LayoutKind.LIGHTS, ObjectLayout.DEFAULT).ordered)
    }

    // --- écriture ---

    @Test
    fun write_in_cloud_sends_one_put_with_complete_order_and_disabled() = runBlocking {
        TestServer(token = "jwt").use { t ->
            t.enqueue(200, """{"order":["volet2salon","volet1salon"],"disabled":["voletbureau","store"],"version":1,"updated_at":"2026-11-07T10:15:30.123Z","status":"saved"}""")
            val order = listOf("volet2salon", "volet1salon") + shutterIds.filter { it != "volet2salon" && it != "volet1salon" }
            val result = (t.container.comfort.writeObjectLayout(LayoutKind.SHUTTERS, order, listOf("voletbureau", "store")) as ApiResult.Ok).value
            val request = t.server.takeRequest()
            assertEquals("PUT", request.method)
            assertEquals("/api/portal/admin/shutters/layout", request.url.encodedPath)
            val body = request.body!!.utf8()
            assertTrue(body, body.startsWith("""{"order":["volet2salon","volet1salon","volet3salon""""))
            assertTrue(body, body.endsWith(""""disabled":["voletbureau","store"]}"""))
            assertFalse(result.dryRun)
            assertEquals(1, result.layout?.version)
            assertEquals(1, t.server.requestCount) // aucune commande vers l'armoire
        }
    }

    @Test
    fun reset_sends_only_reset_true_and_lan_uses_the_admin_path() = runBlocking {
        TestServer(mode = ConnectionMode.LAN).use { t ->
            t.store.update { it.copy(lanCookie = "abc") }
            t.enqueue(200, """{"order":[],"disabled":[],"version":4,"updated_at":"2026-11-07T10:20:00.000Z","status":"saved"}""")
            val result = (t.container.comfort.writeObjectLayout(LayoutKind.LIGHTS, emptyList(), emptyList(), reset = true) as ApiResult.Ok).value
            val request = t.server.takeRequest()
            assertEquals("/api/admin/lights/layout", request.url.encodedPath)
            assertEquals("""{"reset":true}""", request.body!!.utf8())
            assertEquals(4, result.layout?.version)
        }
    }

    @Test
    fun invalid_layout_is_refused_before_any_request() = runBlocking {
        TestServer(token = "jwt").use { t ->
            val result = t.container.comfort.writeObjectLayout(LayoutKind.LIGHTS, listOf("inconnue"), emptyList())
            assertEquals("unknown_light", ((result as ApiResult.Err).error as ApiError.Http).code)
            assertEquals(0, t.server.requestCount)
        }
    }

    @Test
    fun write_errors_403_and_503_come_back_as_errors_and_dry_run_is_reported() = runBlocking {
        TestServer(token = "jwt").use { t ->
            t.enqueue(403, """{"error":"forbidden"}""")
            assertTrue((t.container.comfort.writeObjectLayout(LayoutKind.LIGHTS, lightIds, emptyList()) as ApiResult.Err).error is ApiError.Forbidden)
            t.enqueue(503, """{"error":"names_unavailable"}""")
            val err = (t.container.comfort.writeObjectLayout(LayoutKind.LIGHTS, lightIds, emptyList()) as ApiResult.Err).error
            assertTrue(ObjectLayouts.errorMessage(err).contains("indisponible"))
        }
        TestServer(token = "jwt", testMode = true).use { t ->
            t.enqueue(200, """{"order":[],"disabled":["wc2"],"version":2,"updated_at":"2026-11-07T10:15:30.123Z","status":"dry_run"}""")
            val saved = (t.container.comfort.writeObjectLayout(LayoutKind.LIGHTS, lightIds, listOf("wc2")) as ApiResult.Ok).value
            assertTrue(saved.dryRun)
            assertEquals("dry-run", t.server.takeRequest().headers["X-Essensys-Test-Mode"])
        }
    }

    // --- non-régression ---

    // NR: NR-android-27 essensys-hub/essensys-feature-lifecycle#41 : les noms (lumières et volets) et les clés ne bougent pas
    @Test
    fun layout_does_not_touch_names_catalogues_or_exchange_indexes_NR_android_27() = runBlocking {
        TestServer(token = "jwt").use { t ->
            t.enqueue(200, """{"order":["dressing"],"disabled":["wc2"],"version":1,"updated_at":"2026-11-07T10:15:30.123Z","status":"saved"}""")
            t.container.comfort.writeObjectLayout(LayoutKind.LIGHTS, lightIds, listOf("wc2"))
            assertFalse(t.server.takeRequest().url.encodedPath.contains("names"))
        }
        // L'ordre d'origine des tableaux (clé de repli) et les noms d'origine ne changent pas.
        assertEquals("terrasse", IndexTable.lights.first().id)
        assertEquals("idressing", IndexTable.lights.last().id)
        assertEquals(33, LightNames.fallback().size)
        assertEquals(16, ShutterNames.fallback().size)
        assertEquals("WC 2", LightNames.fallback().getValue("wc2").display)
        assertEquals("Volet Bureau", ShutterNames.fallback().getValue("voletbureau").display)
        assertEquals(571, IndexTable.shutters.first { it.id == "voletbureau" }.travelTimeIndex)
        // Un nom personnalisé et une position : le nom suit l'objet (la disposition ne porte que des identifiants).
        val renamed = LightName("dressing", "Dressing", "WC 3")
        assertEquals("WC 3", renamed.display)
        assertEquals("dressing", ObjectLayouts.effective(LayoutKind.LIGHTS, layout(order = listOf("dressing"))).ordered.first())
    }
}
