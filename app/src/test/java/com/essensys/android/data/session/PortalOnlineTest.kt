package com.essensys.android.data.session

import com.essensys.android.data.http.ApiResult
import com.essensys.android.data.http.EssensysJson
import com.essensys.android.data.http.PortalSession
import com.essensys.android.support.TestServer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Armoire « en ligne » avec ou sans gateway (armoire-online-sans-gateway-2026-10-012).
 * Les sessions sont décodées depuis le JSON du cloud, comme sur le réseau. Cas A à F de la matrice du ticket.
 */
class PortalOnlineTest {

    private fun session(json: String) = EssensysJson.decodeFromString(PortalSession.serializer(), json)

    private fun json(gateway: String?, armoire: String?) =
        """{"user":{"id":1,"email":"demo@essensys.fr"},"portal_access":true""" +
            (gateway?.let { ""","gateway":$it""" } ?: "") + (armoire?.let { ""","armoire":$it""" } ?: "") + "}"

    private val gatewayOn = """{"id":"gw","online":true}"""
    private val gatewayOff = """{"id":"gw","online":false}"""
    private val armoireSeen = """{"no_serie":"UNKNOWN-734254c2","last_seen":"2026-10-26T11:48:00Z","online":true}"""
    private val armoireOld = """{"no_serie":"UNKNOWN-734254c2","last_seen":"2026-10-26T09:00:00Z","online":false}"""

    @Test
    fun avec_gateway_en_ligne() {
        assertTrue(session(json(gatewayOn, armoireSeen)).armoireOnline())
    }

    @Test
    fun gateway_hors_ligne_armoire_vue() {
        assertTrue(session(json(gatewayOff, armoireSeen)).armoireOnline())
    }

    @Test
    fun gateway_et_armoire_hors_ligne() {
        assertFalse(session(json(gatewayOff, armoireOld)).armoireOnline())
    }

    // NR: NR-android-19 essensys-hub/essensys-android-phone-apps#24
    // Signalement du 2026-10-26 (essensys-hub/essensys-feature-lifecycle#37) : sans gateway, armoire vue.
    @Test
    fun sans_gateway_armoire_vue_en_ligne_NR_android_19() {
        val s = session(json(null, armoireSeen))
        assertEquals(null, s.gateway)
        assertTrue(s.armoireOnline())
    }

    @Test
    fun sans_gateway_armoire_ancienne_hors_ligne() {
        assertFalse(session(json(null, armoireOld)).armoireOnline())
    }

    @Test
    fun sans_gateway_ni_armoire_hors_ligne() {
        assertFalse(session(json(null, null)).armoireOnline())
    }

    @Test
    fun ancien_backend_sans_champ_online_se_reduit_a_la_gateway() {
        val oldArmoire = """{"no_serie":"SC944D-0001","last_seen":"2026-10-26T11:48:00Z"}"""
        assertTrue(session(json(gatewayOn, oldArmoire)).armoireOnline())
        assertFalse(session(json(null, oldArmoire)).armoireOnline())
    }

    @Test
    fun le_depot_lit_armoire_en_ligne_sans_gateway() = runBlocking {
        TestServer(token = "jwt").use { t ->
            t.enqueue(200, json(null, armoireSeen))
            t.enqueue(200, json(null, armoireOld))
            assertEquals(ApiResult.Ok(true), t.container.portal.gatewayOnline())
            assertEquals(ApiResult.Ok(false), t.container.portal.gatewayOnline())
        }
    }
}
