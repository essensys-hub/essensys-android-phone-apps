package com.essensys.android.ui

import com.essensys.android.ui.screens.formatRememberUntil
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class FormatRememberUntilTest {

    @Test
    fun end_date_is_shown_in_local_time() {
        val end = Instant.parse("2026-12-10T08:00:00Z").toEpochMilli()
        assertEquals("10/12/2026", formatRememberUntil(end, ZoneId.of("Europe/Paris")))
    }

    @Test
    fun the_local_day_can_differ_from_the_utc_day() {
        val end = Instant.parse("2026-12-10T23:30:00Z").toEpochMilli()
        assertEquals("11/12/2026", formatRememberUntil(end, ZoneId.of("Europe/Paris")))
    }
}
