package com.essensys.android.data.control

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleCodecTest {

    private val jour = IndexTable.heatingZones.first { it.id == "jour" }

    // NR: NR-android-13 essensys-hub/essensys-android-phone-apps#12
    @Test
    fun schedule_byte_encodes_even_and_odd_hour_NR_android_13() {
        // Exemple de la spec : lundi 6 h Confort + 7 h Éco en Zone Jour → k=16, v="33" (0x21).
        val grid = ScheduleGrid.EMPTY
            .with(day = 0, hour = 6, value = IndexTable.HeatingSlot.CONFORT.value)
            .with(day = 0, hour = 7, value = IndexTable.HeatingSlot.ECO.value)
        assertEquals(listOf(Injection(16, "33")), ScheduleCodec.diff(grid, ScheduleGrid.EMPTY, jour.scheduleStart))
        assertEquals(16, ScheduleCodec.indexOf(jour.scheduleStart, day = 0, hour = 7))
        // Dimanche 23 h = dernier octet de la zone (96).
        assertEquals(96, ScheduleCodec.indexOf(jour.scheduleStart, day = 6, hour = 23))
        assertEquals(1 to 2, ScheduleCodec.decodeByte(0x21))
        assertEquals(0x43, ScheduleCodec.encodeByte(3, 4))
    }

    @Test
    fun decode_reads_each_byte_and_defaults_missing_to_off() {
        val grid = ScheduleCodec.decode(mapOf(13 to "33", 96 to "65", 50 to "abc"), jour.scheduleStart)
        assertEquals(1, grid[0, 0])
        assertEquals(2, grid[0, 1])
        assertEquals(1, grid[6, 22]) // 65 = 0x41
        assertEquals(4, grid[6, 23])
        assertEquals(0, grid[3, 2]) // octet illisible → OFF
        assertEquals(0, grid[1, 5]) // octet absent → OFF
    }

    @Test
    fun diff_only_emits_changed_bytes_and_keeps_untouched_frost_slot() {
        // Octet 20 lu = hors gel (5) en heure paire, Confort en impaire : modifier l'impaire garde le 5.
        val baseline = ScheduleCodec.decode(mapOf(20 to "21"), jour.scheduleStart) // 0x15
        val grid = baseline.with(day = 0, hour = 15, value = IndexTable.HeatingSlot.ECO.value)
        assertEquals(listOf(Injection(20, "37")), ScheduleCodec.diff(grid, baseline, jour.scheduleStart)) // 0x25
        assertTrue(ScheduleCodec.diff(baseline, baseline, jour.scheduleStart).isEmpty())
    }

    @Test
    fun copy_day_to_all_repeats_source_day() {
        val monday = ScheduleGrid.EMPTY.with(0, 8, 1).with(0, 9, 1)
        val week = monday.copyDayToAll(0)
        (0 until 7).forEach { day -> assertEquals(1, week[day, 8]); assertEquals(1, week[day, 9]) }
        // 8 h–9 h = 5e octet de chaque jour : un octet modifié par jour.
        assertEquals((0 until 7).map { 13 + it * 12 + 4 }, ScheduleCodec.diff(week, ScheduleGrid.EMPTY, jour.scheduleStart).map { it.k })
    }
}
