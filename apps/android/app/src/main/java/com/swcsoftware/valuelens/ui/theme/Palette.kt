package com.swcsoftware.valuelens.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Every colour the app draws with, as data — the Android half of Sprint 5 Track B.
 * Mirrors apps/ios/ValueLens/DesignSystem/ThemePalette.swift.
 *
 * Three independent axes (docs/DESIGN.md):
 *  - **Theme** (Light / Dark / System): ground, text, and the semantic colours per ground.
 *  - **Accent**: chrome only — buttons, tabs, selection, focus. The user's choice.
 *  - **Layout** (Classic / Report card): typography and components; never a colour token.
 */
data class ThemePalette(
    val background: Color,
    val surface: Color,
    val raised: Color,
    val border: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    /** Market price. **Always amber, never the user's accent.** */
    val price: Color,
    /** Intrinsic / fair value. **Always mint, never the user's accent.** */
    val value: Color,
    val danger: Color,
    val warning: Color,
    val info: Color,
    /** Chrome only. The one colour the user may choose. */
    val accent: Color,
    /** Text drawn on `accent`, computed from its luminance rather than assumed. */
    val accentForeground: Color,
    val isDark: Boolean,
) {
    companion object {
        /** The values that shipped in Sprints 0–4, unchanged. Track B must not move the dark app. */
        val Dark = ThemePalette(
            background = Color(0xFF0B0D10),
            surface = Color(0xFF15181D),
            raised = Color(0xFF1E2229),
            border = Color(0x14FFFFFF),
            textPrimary = Color(0xFFF2F2F2),
            textSecondary = Color(0xFF9EA3AB),
            textTertiary = Color(0xFF6B7079),
            price = Color(0xFFF5A623),
            value = Color(0xFF2ED99E),
            danger = Color(0xFFEF5450),
            warning = Color(0xFFFAC73F),
            info = Color(0xFF5C9EFA),
            accent = Color(0xFF2ED99E),
            accentForeground = Color(0xFF0B0D10),
            isDark = true,
        )

        /**
         * Mint and amber both fail contrast as text on paper at their dark values, so the light
         * ground gets darker versions of the same two meanings. They stay amber and mint.
         */
        val Light = ThemePalette(
            background = Color(0xFFF1F4F5),
            surface = Color(0xFFFFFFFF),
            raised = Color(0xFFE7EDEF),
            border = Color(0x2110181C),
            textPrimary = Color(0xFF14181B),
            textSecondary = Color(0xFF56646A),
            textTertiary = Color(0xFF7B888D),
            price = Color(0xFF9A6006),
            value = Color(0xFF0B7A57),
            danger = Color(0xFFB4291F),
            warning = Color(0xFF8A5D00),
            info = Color(0xFF1B5FA8),
            accent = Color(0xFF0B7A57),
            accentForeground = Color(0xFFFFFFFF),
            isDark = false,
        )

        fun base(dark: Boolean): ThemePalette = if (dark) Dark else Light
    }

    /** Applies the user's accent, if they chose one and it is legible on this ground. */
    fun withAccent(argb: Long?): ThemePalette {
        if (argb == null) return this
        val chosen = Rgb(argb)
        val ground = Rgb(if (isDark) 0xFF0B0D10 else 0xFFF1F4F5)
        if (!chosen.isLegibleOn(ground)) return this
        return copy(accent = Color(argb), accentForeground = chosen.readableForeground())
    }
}

/**
 * Just enough colour maths to keep a user-chosen accent legible. A pale accent on white would
 * otherwise give white-on-white buttons, and "it looked fine when I picked it" is not a check.
 */
@JvmInline
value class Rgb(private val argb: Long) {
    private val r: Double get() = ((argb shr 16) and 0xFF) / 255.0
    private val g: Double get() = ((argb shr 8) and 0xFF) / 255.0
    private val b: Double get() = (argb and 0xFF) / 255.0

    /** WCAG relative luminance. */
    fun luminance(): Double {
        fun channel(c: Double) = if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b)
    }

    fun contrastAgainst(other: Rgb): Double {
        val a = luminance()
        val o = other.luminance()
        return (max(a, o) + 0.05) / (min(a, o) + 0.05)
    }

    /** Black or white, whichever the eye can actually read on this colour. */
    fun readableForeground(): Color =
        if (contrastAgainst(Rgb(0xFF0B0D10)) >= contrastAgainst(Rgb(0xFFFFFFFF))) Color(0xFF0B0D10)
        else Color(0xFFFFFFFF)

    /**
     * 3:1 is the WCAG minimum for a UI component boundary against its background. An accent that
     * cannot clear it is refused rather than drawn, and the picker says why.
     */
    fun isLegibleOn(ground: Rgb): Boolean = contrastAgainst(ground) >= 3.0

    /** Degrees of hue between two colours, the short way round the wheel. */
    fun hueSeparationFrom(other: Rgb): Double {
        val delta = abs(hueDegrees() - other.hueDegrees())
        return min(delta, 360 - delta)
    }

    fun hueDegrees(): Double {
        val maxC = maxOf(r, g, b)
        val minC = minOf(r, g, b)
        val chroma = maxC - minC
        if (chroma == 0.0) return 0.0
        val hue = when (maxC) {
            r -> 60 * (((g - b) / chroma) % 6)
            g -> 60 * (((b - r) / chroma) + 2)
            else -> 60 * (((r - g) / chroma) + 4)
        }
        return if (hue < 0) hue + 360 else hue
    }
}

/** Light / Dark / System. Independent of layout — either layout works on either ground. */
enum class ThemePreference(val displayName: String) {
    SYSTEM("System"), LIGHT("Light"), DARK("Dark");

    companion object {
        fun from(raw: String?): ThemePreference =
            entries.firstOrNull { it.name == raw } ?: DARK
    }
}

/** The accents offered in Settings; a user may also pick any colour, contrast-checked first. */
object AccentChoice {
    val presets: List<Pair<String, Long>> = listOf(
        "Mint" to 0xFF2ED99E,
        "Forest" to 0xFF0B7A57,
        "Azure" to 0xFF5C9EFA,
        "Iris" to 0xFF7C6BF5,
        "Coral" to 0xFFE0655F,
        "Ochre" to 0xFFD98A27,
        "Fuchsia" to 0xFFC2569C,
        "Slate" to 0xFF4A5560,
    )
}
