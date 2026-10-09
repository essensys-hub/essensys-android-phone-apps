package com.essensys.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Charte du portail Essensys (tokens de `essensys-user-portal-frontend/src/index.css`).
 * Spec : portal-theme. Aucune couleur ne doit être codée en dur hors de ce fichier.
 */
@Immutable
data class EssensysColors(
    val primary: Color,
    val primaryDark: Color,
    val secondary: Color,
    val danger: Color,
    val dangerDark: Color,
    val success: Color,
    val warning: Color,
    val background: Color,
    val card: Color,
    val cardHeader: Color,
    val border: Color,
    val text: Color,
    val textMuted: Color,
)

val PortalLight = EssensysColors(
    primary = Color(0xFF2563EB),
    primaryDark = Color(0xFF1D4ED8),
    secondary = Color(0xFF64748B),
    danger = Color(0xFFDC2626),
    dangerDark = Color(0xFFB91C1C),
    success = Color(0xFF16A34A),
    warning = Color(0xFFF59E0B),
    background = Color(0xFFF8F8F8),
    card = Color(0xFFFFFFFF),
    cardHeader = Color(0xFFF9FAFB),
    border = Color(0xFFE2E8F0),
    text = Color(0xFF111827),
    textMuted = Color(0xFF6B7280),
)

val PortalDark = EssensysColors(
    primary = Color(0xFF3B82F6),
    primaryDark = Color(0xFF60A5FA),
    secondary = Color(0xFF94A3B8),
    danger = Color(0xFFDC2626),
    dangerDark = Color(0xFFB91C1C),
    success = Color(0xFF16A34A),
    warning = Color(0xFFF59E0B),
    background = Color(0xFF0F172A),
    card = Color(0xFF1E293B),
    cardHeader = Color(0xFF334155),
    border = Color(0xFF475569),
    text = Color(0xFFF1F5F9),
    textMuted = Color(0xFF94A3B8),
)

enum class ThemePreference { SYSTEM, LIGHT, DARK }

val LocalEssensysColors = staticCompositionLocalOf { PortalLight }

fun EssensysColors.toColorScheme(dark: Boolean): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = primary,
        onPrimary = Color.White,
        primaryContainer = primaryDark,
        onPrimaryContainer = Color.White,
        secondary = secondary,
        onSecondary = Color.White,
        // Puces / éléments sélectionnés : teinte primaire du portail (pas le violet Material par défaut).
        secondaryContainer = primary.copy(alpha = 0.15f),
        onSecondaryContainer = primary,
        error = danger,
        onError = Color.White,
        background = background,
        onBackground = text,
        surface = card,
        onSurface = text,
        surfaceVariant = cardHeader,
        onSurfaceVariant = textMuted,
        surfaceContainer = card,
        surfaceContainerLow = card,
        surfaceContainerHigh = cardHeader,
        outline = border,
        outlineVariant = border,
    )
}

/** Formes du portail : boutons `rounded-lg` (8 dp), cartes `rounded-xl` (12 dp). */
val PortalShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
)

@Composable
fun EssensysTheme(
    preference: ThemePreference = ThemePreference.SYSTEM,
    content: @Composable () -> Unit,
) {
    val dark = when (preference) {
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
        ThemePreference.LIGHT -> false
        ThemePreference.DARK -> true
    }
    val colors = if (dark) PortalDark else PortalLight
    CompositionLocalProvider(LocalEssensysColors provides colors) {
        MaterialTheme(
            colorScheme = colors.toColorScheme(dark),
            shapes = PortalShapes,
            content = content,
        )
    }
}

/** Accès aux tokens hors Material (success, warning, en-tête de carte…). */
object Essensys {
    val colors: EssensysColors
        @Composable get() = LocalEssensysColors.current
}
