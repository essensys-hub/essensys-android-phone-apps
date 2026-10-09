package com.essensys.android.data.control

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Non-régression des couples (k, v) : copie figée des tables du portail au 2026-10-09.
 * Toute divergence avec le portail doit être une décision explicite (ticket + mise à jour des deux côtés).
 */
class IndexTableTest {

    private val goldenLights = mapOf(
        "terrasse" to ((616 to 610) to "4"),
        "entree" to ((611 to 605) to "1"),
        "escalier" to ((613 to 607) to "1"),
        "deg1" to ((616 to 610) to "1"),
        "deg2" to ((616 to 610) to "2"),
        "pieceserv" to ((615 to 609) to "128"),
        "ann1" to ((616 to 610) to "8"),
        "ann2" to ((616 to 610) to "16"),
        "salon" to ((612 to 606) to "128"),
        "sam" to ((612 to 606) to "64"),
        "cuisine" to ((615 to 609) to "1"),
        "sdb1" to ((616 to 610) to "128"),
        "sdb2" to ((615 to 609) to "8"),
        "wc1" to ((615 to 609) to "32"),
        "wc2" to ((615 to 609) to "64"),
        "bureau" to ((612 to 606) to "32"),
        "gdchamb" to ((614 to 608) to "128"),
        "ptchamb1" to ((614 to 608) to "64"),
        "ptchamb2" to ((614 to 608) to "32"),
        "ptchamb3" to ((614 to 608) to "16"),
        "dressing" to ((611 to 605) to "8"),
        "isalonind" to ((611 to 605) to "2"),
        "isalonind2" to ((611 to 605) to "4"),
        "icuisine" to ((615 to 609) to "2"),
        "isdb1" to ((615 to 609) to "4"),
        "isdb2" to ((615 to 609) to "16"),
        "igdchamb1" to ((613 to 607) to "2"),
        "igdchamb2" to ((613 to 607) to "4"),
        "iptchamb1" to ((613 to 607) to "8"),
        "iptchamb2" to ((613 to 607) to "16"),
        "iptchamb22" to ((613 to 607) to "32"),
        "iptchamb3" to ((613 to 607) to "64"),
        "idressing" to ((611 to 605) to "16"),
    )

    private val goldenShutters = mapOf(
        "volet1salon" to ((617 to 620) to "1"),
        "volet2salon" to ((617 to 620) to "2"),
        "volet3salon" to ((617 to 620) to "4"),
        "volet1salleamanger" to ((617 to 620) to "8"),
        "volet2salleamanger" to ((617 to 620) to "16"),
        "volet1cuisine" to ((619 to 622) to "1"),
        "volet2cuisine" to ((619 to 622) to "2"),
        "voletsdb" to ((619 to 622) to "4"),
        "volet1gdchamb" to ((618 to 621) to "1"),
        "volet2gdchamb" to ((618 to 621) to "2"),
        "volet1ptchamb" to ((618 to 621) to "4"),
        "volet2ptchamb" to ((618 to 621) to "8"),
        "volet3ptchamb" to ((618 to 621) to "16"),
        "voletbureau" to ((617 to 620) to "32"),
        "voletstore" to ((617 to 620) to "64"),
        "store" to ((619 to 622) to "8"),
    )

    // NR: NR-android-5 essensys-hub/essensys-android-phone-apps#4
    @Test
    fun lighting_sends_portal_index_and_mask_NR_android_5() {
        assertEquals(goldenLights.keys, IndexTable.lights.map { it.id }.toSet())
        IndexTable.lights.forEach { light ->
            val (indices, mask) = goldenLights.getValue(light.id)
            assertEquals(light.id, Injection(indices.first, mask), light.command(on = true))
            assertEquals(light.id, Injection(indices.second, mask), light.command(on = false))
        }
        // Exemple de la spec : allumer le salon = k=612, v="128".
        assertEquals(Injection(612, "128"), IndexTable.lights.first { it.id == "salon" }.command(on = true))
    }

    // NR: NR-android-6 essensys-hub/essensys-android-phone-apps#4
    @Test
    fun shutters_send_portal_index_and_mask_NR_android_6() {
        assertEquals(goldenShutters.keys, IndexTable.shutters.map { it.id }.toSet())
        IndexTable.shutters.forEach { shutter ->
            val (indices, mask) = goldenShutters.getValue(shutter.id)
            assertEquals(shutter.id, Injection(indices.first, mask), shutter.command(open = true))
            assertEquals(shutter.id, Injection(indices.second, mask), shutter.command(open = false))
        }
        // Cuisine : 619 ouvrir / 622 fermer (vérifié par la console cuisine 2026-06-031).
        assertEquals(Injection(622, "1"), IndexTable.shutters.first { it.id == "volet1cuisine" }.command(open = false))
    }

    @Test
    fun travel_time_indices_are_in_legacy_range() {
        IndexTable.shutters.forEach { assert(it.travelTimeIndex in 566..589) { it.id } }
    }
}
