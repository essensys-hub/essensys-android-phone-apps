package com.essensys.android.data.control

import com.essensys.android.data.http.ApiError
import com.essensys.android.data.http.ApiResult
import com.essensys.android.data.session.ConnectionMode
import com.essensys.android.support.TestServer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Noms personnalisés des volets et stores (spec shutter-names-clients) contre un serveur simulé, sans armoire.
 * NR-android-26 : essensys-hub/essensys-feature-lifecycle#41 (les noms des lumières restent inchangés),
 * Feature #52, sub-issue essensys-hub/essensys-android-phone-apps#47.
 */
class ShutterNamesTest {

    private fun entry(id: String, default: String, name: String?, version: Int = 0) =
        """{"id":"$id","default":${jsonString(default)},"name":${name?.let(::jsonString) ?: "null"},"version":$version,"updated_at":${if (name == null) "null" else "\"2026-11-07T10:15:30.123Z\""}}"""

    private fun jsonString(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    private fun shutters(vararg entries: String) = """{"shutters":[${entries.joinToString(",")}]}"""

    // --- catalogue ---

    @Test
    fun catalogue_matches_the_16_known_shutters_in_order() {
        val expected = listOf(
            "volet1salon", "volet2salon", "volet3salon", "volet1salleamanger", "volet2salleamanger", "volet1cuisine",
            "volet2cuisine", "voletsdb", "volet1gdchamb", "volet2gdchamb", "volet1ptchamb", "volet2ptchamb",
            "volet3ptchamb", "voletbureau", "voletstore", "store",
        )
        assertEquals(expected, ShutterNames.fallback().keys.toList())
        assertEquals(16, ShutterNames.fallback().size)
        assertEquals("Volet 1 Salon", ShutterNames.fallback().getValue("volet1salon").display)
        assertEquals("Store (banne)", ShutterNames.fallback().getValue("store").display)
        // Le nom d'origine de voletstore contient des guillemets droits : affiché tel quel.
        assertEquals("Volet \"Store\"", ShutterNames.fallback().getValue("voletstore").display)
    }

    @Test
    fun a_light_id_is_not_a_shutter() {
        assertFalse("dressing" in ShutterNames.fallback())
        assertFalse("volet1salon" in LightNames.fallback())
    }

    // --- validation (même règle que les lumières) ---

    @Test
    fun name_is_trimmed_and_limited_to_40_characters() {
        assertEquals("Volet baie vitrée", ShutterNames.validate("  Volet baie vitrée ").getOrNull())
        assertEquals("a".repeat(40), ShutterNames.validate("a".repeat(40)).getOrNull())
        assertTrue(ShutterNames.validate("a".repeat(41)).isFailure)
        assertTrue(ShutterNames.validate("").isFailure)
        assertTrue(ShutterNames.validate("   ").isFailure)
        assertTrue(ShutterNames.validate("<b>x</b>").isFailure)
        assertTrue(ShutterNames.validate("a\u0001b").isFailure)
    }

    @Test
    fun quotes_and_special_names_are_kept_as_is() {
        for (name in listOf("Store \"nord\"", "a&b", "Volet d'angle", "&lt;b&gt;")) {
            assertEquals(name, ShutterNames.validate(name).getOrNull())
        }
    }

    // --- nom affiché et repli ---

    @Test
    fun display_is_the_custom_name_else_the_default() {
        assertEquals("Volet baie vitrée", ShutterName("volet1salon", "Volet 1 Salon", "Volet baie vitrée", 2, "2026-11-07T10:15:30.123Z").display)
        assertEquals("Volet 1 Salon", ShutterName("volet1salon", "Volet 1 Salon", null).display)
        assertEquals("Volet 1 Salon", ShutterName("volet1salon", "Volet 1 Salon", "  ", 3, null).display)
    }

    // --- lecture ---

    @Test
    fun read_in_cloud_uses_the_portal_path_and_applies_the_custom_name() = runBlocking {
        TestServer(token = "jwt").use { t ->
            t.enqueue(200, shutters(entry("volet1salon", "Volet 1 Salon", "Volet baie vitrée", 2), entry("store", "Store (banne)", null)))
            val names = (t.container.comfort.readShutterNames() as ApiResult.Ok).value
            val request = t.server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/api/portal/shutters/names", request.url.encodedPath)
            assertEquals("Bearer jwt", request.headers["Authorization"])
            assertEquals("Volet baie vitrée", names.getValue("volet1salon").display)
            assertEquals("Store (banne)", names.getValue("store").display)
            assertEquals("Volet Bureau", names.getValue("voletbureau").display) // absent de la réponse : nom d'origine
            assertEquals(16, names.size)
        }
    }

    @Test
    fun read_in_lan_uses_the_gateway_path() = runBlocking {
        TestServer(mode = ConnectionMode.LAN).use { t ->
            t.store.update { it.copy(lanCookie = "abc") }
            t.enqueue(200, shutters(entry("voletbureau", "Volet Bureau", "Bureau nord", 1)))
            val names = (t.container.comfort.readShutterNames() as ApiResult.Ok).value
            val request = t.server.takeRequest()
            assertEquals("/api/shutters/names", request.url.encodedPath)
            assertEquals("essensys_lan_session=abc", request.headers["Cookie"])
            assertEquals("Bureau nord", names.getValue("voletbureau").display)
        }
    }

    @Test
    fun special_name_from_the_server_is_kept_verbatim() = runBlocking {
        TestServer(token = "jwt").use { t ->
            t.enqueue(200, shutters(entry("store", "Store (banne)", "a&b <i>x</i> \"nord\"", 1)))
            val names = (t.container.comfort.readShutterNames() as ApiResult.Ok).value
            assertEquals("a&b <i>x</i> \"nord\"", names.getValue("store").display)
        }
    }

    @Test
    fun unknown_shutter_from_the_server_is_ignored() = runBlocking {
        TestServer(token = "jwt").use { t ->
            t.enqueue(200, shutters(entry("inconnu", "Inconnu", "X", 1)))
            val names = (t.container.comfort.readShutterNames() as ApiResult.Ok).value
            assertFalse("inconnu" in names)
            assertEquals(ShutterNames.fallback().keys.toList(), names.keys.toList())
        }
    }

    @Test
    fun failed_read_is_an_error_and_the_fallback_gives_the_original_names() = runBlocking {
        TestServer(token = "jwt").use { t ->
            t.enqueue(500, "boom")
            assertTrue(t.container.comfort.readShutterNames() is ApiResult.Err)
            t.enqueue(404, "")
            assertTrue(t.container.comfort.readShutterNames() is ApiResult.Err)
            t.enqueue(200, "pas du json")
            assertTrue(t.container.comfort.readShutterNames() is ApiResult.Err)
        }
        assertEquals("Volet 1 Salon", ShutterNames.fallback().getValue("volet1salon").display)
    }

    // --- écriture ---

    // NR: NR-android-26 essensys-hub/essensys-feature-lifecycle#41
    @Test
    fun write_in_cloud_sends_the_name_to_the_admin_path_NR_android_26() = runBlocking {
        TestServer(token = "jwt").use { t ->
            t.enqueue(200, """{"id":"volet1salon","default":"Volet 1 Salon","name":"Volet baie vitrée","version":2,"updated_at":"2026-11-07T10:15:30.123Z","status":"saved"}""")
            val result = (t.container.comfort.setShutterName("volet1salon", "  Volet baie vitrée ") as ApiResult.Ok).value
            val request = t.server.takeRequest()
            assertEquals("PUT", request.method)
            assertEquals("/api/portal/admin/shutters/names/volet1salon", request.url.encodedPath)
            assertEquals("""{"name":"Volet baie vitrée"}""", request.body!!.utf8())
            assertEquals("Bearer jwt", request.headers["Authorization"])
            assertEquals("Volet baie vitrée", result.shutter?.display)
            assertFalse(result.dryRun)
            assertEquals(1, t.server.requestCount) // un seul appel : aucune commande vers l'armoire
        }
    }

    // NR: NR-android-26 essensys-hub/essensys-feature-lifecycle#41 : les noms de lumières ne bougent pas
    @Test
    fun renaming_a_shutter_does_not_touch_light_names_NR_android_26() = runBlocking {
        TestServer(token = "jwt").use { t ->
            t.enqueue(200, """{"id":"store","default":"Store (banne)","name":"Store terrasse","version":1,"updated_at":"2026-11-07T10:15:30.123Z","status":"saved"}""")
            t.container.comfort.setShutterName("store", "Store terrasse")
            val request = t.server.takeRequest()
            assertFalse(request.url.encodedPath.contains("lights"))
            assertEquals("Dressing", LightNames.fallback().getValue("dressing").display)
            assertEquals(33, LightNames.fallback().size)
        }
    }

    @Test
    fun write_in_lan_uses_the_gateway_admin_path() = runBlocking {
        TestServer(mode = ConnectionMode.LAN).use { t ->
            t.store.update { it.copy(lanCookie = "abc") }
            t.enqueue(200, """{"id":"voletbureau","default":"Volet Bureau","name":"Bureau nord","version":1,"updated_at":"2026-11-07T10:15:30.123Z","status":"saved"}""")
            assertTrue(t.container.comfort.setShutterName("voletbureau", "Bureau nord") is ApiResult.Ok)
            assertEquals("/api/admin/shutters/names/voletbureau", t.server.takeRequest().url.encodedPath)
        }
    }

    @Test
    fun reset_sends_an_explicit_null_name() = runBlocking {
        TestServer(token = "jwt").use { t ->
            t.enqueue(200, """{"id":"volet1salon","default":"Volet 1 Salon","name":null,"version":3,"updated_at":"2026-11-07T10:20:00.000Z","status":"saved"}""")
            val result = (t.container.comfort.setShutterName("volet1salon", null) as ApiResult.Ok).value
            assertEquals("""{"name":null}""", t.server.takeRequest().body!!.utf8())
            assertNull(result.shutter?.name)
            assertEquals("Volet 1 Salon", result.shutter?.display)
        }
    }

    @Test
    fun write_in_test_mode_is_a_dry_run() = runBlocking {
        TestServer(token = "jwt", testMode = true).use { t ->
            t.enqueue(200, """{"id":"store","default":"Store (banne)","name":"X","version":1,"updated_at":"2026-11-07T10:15:30.123Z","status":"dry_run"}""")
            val result = (t.container.comfort.setShutterName("store", "X") as ApiResult.Ok).value
            assertTrue(result.dryRun)
            assertEquals("dry-run", t.server.takeRequest().headers["X-Essensys-Test-Mode"])
        }
    }

    @Test
    fun invalid_name_never_reaches_the_server() = runBlocking {
        TestServer(token = "jwt").use { t ->
            for (bad in listOf("", "   ", "a".repeat(41), "<b>", "a\u0001")) {
                val result = t.container.comfort.setShutterName("volet1salon", bad)
                assertTrue("$bad", (result as ApiResult.Err).error is ApiError.Http)
            }
            assertEquals(0, t.server.requestCount)
        }
    }

    @Test
    fun renaming_is_not_an_armoire_mutation() {
        val guard = com.essensys.android.data.http.NoArmoireInterceptor
        assertFalse(guard.isArmoireMutation("PUT", "/api/portal/admin/shutters/names/volet1salon"))
        assertFalse(guard.isArmoireMutation("PUT", "/api/admin/shutters/names/volet1salon"))
    }

    // --- messages d'erreur 400 / 403 / 503 ---

    @Test
    fun error_messages_are_clear_for_400_403_and_503() = runBlocking {
        TestServer(token = "jwt").use { t ->
            t.enqueue(400, """{"error":"invalid_name"}""")
            t.enqueue(400, """{"error":"unknown_shutter"}""")
            t.enqueue(403, """{"error":"forbidden"}""")
            t.enqueue(503, """{"error":"names_unavailable"}""")
            val messages = List(4) { ShutterNames.errorMessage((t.container.comfort.setShutterName("volet1salon", "Volet baie vitrée") as ApiResult.Err).error) }
            assertTrue(messages[0], messages[0].contains("1 à 40"))
            assertTrue(messages[1], messages[1].contains("inconnu"))
            assertTrue(messages[2], messages[2].contains("administrateur"))
            assertTrue(messages[3], messages[3].contains("indisponible"))
            assertEquals("jwt", t.store.state.value.token) // 403 ne ferme pas la session
        }
    }
}
