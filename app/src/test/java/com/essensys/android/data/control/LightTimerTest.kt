package com.essensys.android.data.control

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LightTimerTest {

    // NR: NR-android-24 essensys-hub/essensys-android-phone-apps#43
    @Test
    fun light_timer_keys_and_range_NR_android_24() {
        // Mapping du change lumieres-temporisees-2026-11-003 : ne change qu'avec le firmware et les autres surfaces.
        assertEquals(
            listOf("dressing" to 521, "wc1" to 555, "wc2" to 556, "pieceserv" to 557, "ann1" to 561, "ann2" to 562),
            LightTimer.entries.map { it.id to it.key },
        )
        assertEquals(listOf("Dressing", "WC 1", "WC 2", "Service", "Annexe 1", "Annexe 2"), LightTimer.entries.map { it.label })
        // Les identifiants sont ceux des lumières déjà pilotées par l'app.
        assertTrue(LightTimer.entries.all { t -> IndexTable.lights.any { it.id == t.id } })
        // Un octet : 0 (jamais) à 255.
        assertTrue(LightTimer.isValid(0))
        assertTrue(LightTimer.isValid(255))
        assertEquals(false, LightTimer.isValid(256))
        assertEquals(false, LightTimer.isValid(-1))
        assertEquals(listOf(15, 30, 60, 0), LightTimer.SHORTCUTS.map { it.first })
    }

    @Test
    fun free_value_accepts_1_to_255_only() {
        assertEquals(7, LightTimer.parseFree("7"))
        assertEquals(255, LightTimer.parseFree(" 255 "))
        assertEquals(1, LightTimer.parseFree("1"))
        assertNull(LightTimer.parseFree("0"))
        assertNull(LightTimer.parseFree("256"))
        assertNull(LightTimer.parseFree("300"))
        assertNull(LightTimer.parseFree("-5"))
        assertNull(LightTimer.parseFree(""))
        assertNull(LightTimer.parseFree("abc"))
        assertNull(LightTimer.parseFree("7.5"))
    }

    @Test
    fun describe_shows_shortcuts_and_unusual_values_as_is() {
        assertEquals("15 min", LightTimer.describe(15))
        assertEquals("1 h", LightTimer.describe(60))
        assertEquals("Jamais", LightTimer.describe(0))
        assertEquals("7 min", LightTimer.describe(7))
        assertEquals("inconnue", LightTimer.describe(null))
    }
}
