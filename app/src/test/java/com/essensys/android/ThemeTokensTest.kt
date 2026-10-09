package com.essensys.android

import androidx.compose.ui.graphics.toArgb
import com.essensys.android.ui.theme.PortalDark
import com.essensys.android.ui.theme.PortalLight
import com.essensys.android.ui.theme.toColorScheme
import org.junit.Assert.assertEquals
import org.junit.Test

/** Les tokens doivent rester identiques à ceux du portail (src/index.css). */
class ThemeTokensTest {
    private fun hex(argb: Int) = String.format("#%06X", argb and 0xFFFFFF)

    @Test
    fun light_tokens_match_portal() {
        assertEquals("#2563EB", hex(PortalLight.primary.toArgb()))
        assertEquals("#F8F8F8", hex(PortalLight.background.toArgb()))
        assertEquals("#E2E8F0", hex(PortalLight.border.toArgb()))
        assertEquals("#111827", hex(PortalLight.text.toArgb()))
    }

    @Test
    fun dark_tokens_match_portal_and_feed_material_scheme() {
        val scheme = PortalDark.toColorScheme(dark = true)
        assertEquals("#0F172A", hex(scheme.background.toArgb()))
        assertEquals("#1E293B", hex(scheme.surface.toArgb()))
        assertEquals("#3B82F6", hex(scheme.primary.toArgb()))
    }
}
