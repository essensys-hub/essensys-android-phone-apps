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
 * Noms personnalisés des lumières (spec light-names-clients) contre un serveur simulé, sans armoire.
 * NR-android-25 : essensys-hub/essensys-feature-lifecycle#41, sub-issue essensys-hub/essensys-android-phone-apps#45.
 */
class LightNamesTest {

    private fun entry(id: String, default: String, name: String?, version: Int = 0) =
        """{"id":"$id","default":"$default","name":${name?.let { "\"$it\"" } ?: "null"},"version":$version,"updated_at":${if (name == null) "null" else "\"2026-11-07T10:15:30.123Z\""}}"""

    private fun lights(vararg entries: String) = """{"lights":[${entries.joinToString(",")}]}"""

    // --- validation du nom (1 à 40 caractères, ni contrôle ni < >) ---

    @Test
    fun name_is_trimmed_and_limited_to_40_characters() {
        assertEquals("WC 3", LightNames.validate("  WC 3 ").getOrNull())
        assertEquals("a".repeat(40), LightNames.validate("a".repeat(40)).getOrNull())
        assertTrue(LightNames.validate("a".repeat(41)).isFailure)
        assertTrue(LightNames.validate("").isFailure)
        assertTrue(LightNames.validate("    ").isFailure)
    }

    @Test
    fun length_counts_unicode_characters_not_utf16_units() {
        assertEquals("😀".repeat(40), LightNames.validate("😀".repeat(40)).getOrNull())
        assertTrue(LightNames.validate("😀".repeat(41)).isFailure)
    }

    @Test
    fun control_characters_and_angle_brackets_are_refused() {
        for (bad in listOf("a\tb", "a\nb", "a\u0000b", "a\u007Fb", "<b>x</b>", "a>b", "a<b")) {
            assertTrue("$bad aurait dû être refusé", LightNames.validate(bad).isFailure)
        }
    }

    @Test
    fun special_names_are_kept_as_is() {
        for (name in listOf("a&b", "Chambre d'amis", "\"Bureau\"", "Café é à", "WC #3 (haut)", "&lt;b&gt;")) {
            assertEquals(name, LightNames.validate(name).getOrNull())
        }
    }

    // --- nom affiché et repli ---

    @Test
    fun catalogue_matches_the_33_known_lights_in_order() {
        assertEquals(IndexTable.lights.map { it.id }, LightNames.fallback().keys.toList())
        assertEquals(33, LightNames.fallback().size)
        assertEquals("Dressing", LightNames.fallback().getValue("dressing").display)
        assertEquals("Salon (indirect 1)", LightNames.fallback().getValue("isalonind").display)
    }

    @Test
    fun display_is_the_custom_name_else_the_default() {
        assertEquals("WC 3", LightName("dressing", "Dressing", "WC 3", 2, "2026-11-07T10:15:30.123Z").display)
        assertEquals("Dressing", LightName("dressing", "Dressing", null, 0, null).display)
        assertEquals("Dressing", LightName("dressing", "Dressing", "  ", 3, null).display)
    }

    // --- lecture ---

    @Test
    fun read_in_cloud_uses_the_portal_path_and_applies_the_custom_name() = runBlocking {
        TestServer(token = "jwt").use { t ->
            t.enqueue(200, lights(entry("dressing", "Dressing", "WC 3", 2), entry("wc1", "WC 1", null)))
            val names = (t.container.comfort.readLightNames() as ApiResult.Ok).value
            val request = t.server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/api/portal/lights/names", request.url.encodedPath)
            assertEquals("Bearer jwt", request.headers["Authorization"])
            assertEquals("WC 3", names.getValue("dressing").display)
            assertEquals("WC 1", names.getValue("wc1").display)
            assertEquals("Salon", names.getValue("salon").display) // absente de la réponse : nom d'origine
            assertEquals(33, names.size)
        }
    }

    @Test
    fun read_in_lan_uses_the_gateway_path() = runBlocking {
        TestServer(mode = ConnectionMode.LAN).use { t ->
            t.store.update { it.copy(lanCookie = "abc") }
            t.enqueue(200, lights(entry("wc1", "WC 1", "WC Haut", 1)))
            val names = (t.container.comfort.readLightNames() as ApiResult.Ok).value
            val request = t.server.takeRequest()
            assertEquals("/api/lights/names", request.url.encodedPath)
            assertEquals("essensys_lan_session=abc", request.headers["Cookie"])
            assertEquals("WC Haut", names.getValue("wc1").display)
        }
    }

    @Test
    fun special_name_from_the_server_is_kept_verbatim() = runBlocking {
        TestServer(token = "jwt").use { t ->
            t.enqueue(200, """{"lights":[{"id":"cuisine","default":"Cuisine","name":"a&b <i>x</i>","version":1,"updated_at":"2026-11-07T10:15:30.123Z"}]}""")
            val names = (t.container.comfort.readLightNames() as ApiResult.Ok).value
            assertEquals("a&b <i>x</i>", names.getValue("cuisine").display)
        }
    }

    @Test
    fun unknown_light_from_the_server_is_ignored() = runBlocking {
        TestServer(token = "jwt").use { t ->
            t.enqueue(200, lights(entry("inconnue", "Inconnue", "X", 1)))
            val names = (t.container.comfort.readLightNames() as ApiResult.Ok).value
            assertFalse("inconnue" in names)
            assertEquals(LightNames.fallback().keys.toList(), names.keys.toList())
        }
    }

    @Test
    fun failed_read_is_an_error_and_the_fallback_gives_the_original_names() = runBlocking {
        TestServer(token = "jwt").use { t ->
            t.enqueue(500, "boom")
            assertTrue(t.container.comfort.readLightNames() is ApiResult.Err)
            t.enqueue(200, "pas du json")
            assertTrue(t.container.comfort.readLightNames() is ApiResult.Err)
        }
        assertEquals("Dressing", LightNames.fallback().getValue("dressing").display)
    }

    // --- écriture ---

    // NR: NR-android-25 essensys-hub/essensys-feature-lifecycle#41
    @Test
    fun write_in_cloud_sends_the_name_to_the_admin_path_NR_android_25() = runBlocking {
        TestServer(token = "jwt").use { t ->
            t.enqueue(200, """{"id":"dressing","default":"Dressing","name":"WC 3","version":2,"updated_at":"2026-11-07T10:15:30.123Z","status":"saved"}""")
            val result = (t.container.comfort.setLightName("dressing", "  WC 3 ") as ApiResult.Ok).value
            val request = t.server.takeRequest()
            assertEquals("PUT", request.method)
            assertEquals("/api/portal/admin/lights/names/dressing", request.url.encodedPath)
            assertEquals("""{"name":"WC 3"}""", request.body!!.utf8())
            assertEquals("Bearer jwt", request.headers["Authorization"])
            assertEquals("WC 3", result.light?.display)
            assertFalse(result.dryRun)
        }
    }

    @Test
    fun write_in_lan_uses_the_gateway_admin_path() = runBlocking {
        TestServer(mode = ConnectionMode.LAN).use { t ->
            t.store.update { it.copy(lanCookie = "abc") }
            t.enqueue(200, """{"id":"wc1","default":"WC 1","name":"WC Haut","version":1,"updated_at":"2026-11-07T10:15:30.123Z","status":"saved"}""")
            assertTrue(t.container.comfort.setLightName("wc1", "WC Haut") is ApiResult.Ok)
            assertEquals("/api/admin/lights/names/wc1", t.server.takeRequest().url.encodedPath)
        }
    }

    @Test
    fun reset_sends_an_explicit_null_name() = runBlocking {
        TestServer(token = "jwt").use { t ->
            t.enqueue(200, """{"id":"dressing","default":"Dressing","name":null,"version":3,"updated_at":"2026-11-07T10:20:00.000Z","status":"saved"}""")
            val result = (t.container.comfort.setLightName("dressing", null) as ApiResult.Ok).value
            assertEquals("""{"name":null}""", t.server.takeRequest().body!!.utf8())
            assertNull(result.light?.name)
            assertEquals("Dressing", result.light?.display)
        }
    }

    @Test
    fun write_in_test_mode_is_a_dry_run() = runBlocking {
        TestServer(token = "jwt", testMode = true).use { t ->
            t.enqueue(200, """{"id":"wc2","default":"WC 2","name":"X","version":1,"updated_at":"2026-11-07T10:15:30.123Z","status":"dry_run"}""")
            val result = (t.container.comfort.setLightName("wc2", "X") as ApiResult.Ok).value
            assertTrue(result.dryRun)
            assertEquals("dry-run", t.server.takeRequest().headers["X-Essensys-Test-Mode"])
        }
    }

    @Test
    fun invalid_name_never_reaches_the_server() = runBlocking {
        TestServer(token = "jwt").use { t ->
            for (bad in listOf("", "   ", "a".repeat(41), "<b>", "a\u0001")) {
                val result = t.container.comfort.setLightName("dressing", bad)
                assertTrue("$bad", (result as ApiResult.Err).error is ApiError.Http)
            }
            assertEquals(0, t.server.requestCount)
        }
    }

    @Test
    fun renaming_is_not_an_armoire_mutation() {
        val guard = com.essensys.android.data.http.NoArmoireInterceptor
        assertFalse(guard.isArmoireMutation("PUT", "/api/portal/admin/lights/names/dressing"))
        assertFalse(guard.isArmoireMutation("PUT", "/api/admin/lights/names/dressing"))
    }

    // --- messages d'erreur 400 / 403 / 503 ---

    @Test
    fun error_messages_are_clear_for_400_403_and_503() = runBlocking {
        TestServer(token = "jwt").use { t ->
            t.enqueue(400, """{"error":"invalid_name"}""")
            t.enqueue(400, """{"error":"unknown_light"}""")
            t.enqueue(403, """{"error":"forbidden"}""")
            t.enqueue(503, """{"error":"names_unavailable"}""")
            val messages = List(4) { LightNames.errorMessage((t.container.comfort.setLightName("dressing", "WC 3") as ApiResult.Err).error) }
            assertTrue(messages[0], messages[0].contains("1 à 40"))
            assertTrue(messages[1], messages[1].contains("inconnue"))
            assertTrue(messages[2], messages[2].contains("administrateur"))
            assertTrue(messages[3], messages[3].contains("indisponible"))
            assertEquals("jwt", t.store.state.value.token) // 403 ne ferme pas la session
        }
    }
}
