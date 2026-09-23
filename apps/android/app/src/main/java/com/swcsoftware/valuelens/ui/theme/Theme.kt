package com.swcsoftware.valuelens.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import com.swcsoftware.valuelens.domain.Verdict

/** The palette in force. Provided once by [ValueLensTheme]; read through [VL]. */
val LocalPalette = staticCompositionLocalOf { ThemePalette.Dark }

/**
 * The app's colours, by name. Mirrors apps/ios/ValueLens/DesignSystem/Theme.swift.
 *
 * **Sprint 5 Track B.** These were constants on an object, so a light ground was impossible. They
 * now read the palette from a CompositionLocal, which means every one of the 230 call sites stayed
 * exactly as it was *and* recomposes correctly when the palette changes — Compose gives us the
 * reactivity that the iOS side has to get from rebuilding the tree.
 */
object VL {
    val background: Color @Composable get() = LocalPalette.current.background
    val surface: Color @Composable get() = LocalPalette.current.surface
    val raised: Color @Composable get() = LocalPalette.current.raised
    val border: Color @Composable get() = LocalPalette.current.border
    val textPrimary: Color @Composable get() = LocalPalette.current.textPrimary
    val textSecondary: Color @Composable get() = LocalPalette.current.textSecondary
    val textTertiary: Color @Composable get() = LocalPalette.current.textTertiary

    /**
     * Market price is always amber and fair value is always mint, on any ground and under any
     * accent. That pair is how the app is read (docs/DESIGN.md, "Semantic colors").
     */
    val price: Color @Composable get() = LocalPalette.current.price
    val value: Color @Composable get() = LocalPalette.current.value
    val danger: Color @Composable get() = LocalPalette.current.danger
    val warning: Color @Composable get() = LocalPalette.current.warning
    val info: Color @Composable get() = LocalPalette.current.info

    /** Chrome only. */
    val accent: Color @Composable get() = LocalPalette.current.accent
    val accentForeground: Color @Composable get() = LocalPalette.current.accentForeground

    @Composable
    fun verdictColor(v: Verdict): Color = when (v) {
        Verdict.DEEP_VALUE, Verdict.WITHIN_MARGIN -> value
        Verdict.THIN_MARGIN -> warning
        Verdict.ABOVE_INTRINSIC -> danger
        Verdict.INSUFFICIENT -> textTertiary
    }
}

private fun colorSchemeFor(p: ThemePalette) = if (p.isDark) {
    darkColorScheme(
        background = p.background, surface = p.surface, surfaceVariant = p.raised,
        primary = p.accent, onPrimary = p.accentForeground, secondary = p.info, tertiary = p.price,
        onBackground = p.textPrimary, onSurface = p.textPrimary, onSurfaceVariant = p.textSecondary,
        outline = p.border, error = p.danger,
    )
} else {
    lightColorScheme(
        background = p.background, surface = p.surface, surfaceVariant = p.raised,
        primary = p.accent, onPrimary = p.accentForeground, secondary = p.info, tertiary = p.price,
        onBackground = p.textPrimary, onSurface = p.textPrimary, onSurfaceVariant = p.textSecondary,
        outline = p.border, error = p.danger,
    )
}

private val Type = Typography(
    displaySmall = TextStyle(fontSize = 38.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
    headlineSmall = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 15.sp),
    bodySmall = TextStyle(fontSize = 12.sp),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp),
)

val Mono = FontFamily.Monospace

/**
 * Resolves the three axes into one palette and provides it.
 *
 * @param preference the user's Light / Dark / System choice
 * @param accentArgb the user's chrome colour, or null for the theme's own
 */
@Composable
fun ValueLensTheme(
    preference: ThemePreference = ThemePreference.DARK,
    accentArgb: Long? = null,
    content: @Composable () -> Unit,
) {
    val dark = when (preference) {
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
        ThemePreference.LIGHT -> false
        ThemePreference.DARK -> true
    }
    val palette = ThemePalette.base(dark).withAccent(accentArgb)
    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(colorScheme = colorSchemeFor(palette), typography = Type, content = content)
    }
}
