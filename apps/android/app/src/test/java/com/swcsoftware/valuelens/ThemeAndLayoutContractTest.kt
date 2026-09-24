package com.swcsoftware.valuelens

import com.swcsoftware.valuelens.domain.DataCheck
import com.swcsoftware.valuelens.domain.ValuationReport
import com.swcsoftware.valuelens.domain.Verdict
import com.swcsoftware.valuelens.domain.verdictEnum
import kotlinx.serialization.json.Json
import java.io.File
import com.swcsoftware.valuelens.ui.LayoutStyle
import com.swcsoftware.valuelens.ui.RequiredComponent
import com.swcsoftware.valuelens.ui.theme.AccentChoice
import com.swcsoftware.valuelens.ui.theme.Rgb
import com.swcsoftware.valuelens.ui.theme.ThemePalette
import com.swcsoftware.valuelens.ui.theme.ThemePreference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Android half of Sprint 5 Tracks A and B. Mirrors apps/ios/ValueLensTests/ThemeTests.swift and the
 * pure-logic part of LayoutContractTests.swift.
 *
 * What is *not* here: the assertion that a rendered layout actually placed each required component.
 * That needs Compose UI testing, which this module has no dependency on yet — ISSUES #87.
 */
class ThemeAndLayoutContractTest {

    private fun rgb(argb: Long) = Rgb(argb)

    // ---- the refactor must not move the dark app ----

    @Test fun `dark palette still carries the shipped values`() {
        val dark = ThemePalette.Dark
        assertEquals(0xFF0B0D10.toInt(), argbOf(dark.background))
        assertEquals(0xFFF5A623.toInt(), argbOf(dark.price))
        assertEquals(0xFF2ED99E.toInt(), argbOf(dark.value))
        assertTrue(dark.isDark)
    }

    // ---- the semantic pair ----

    @Test fun `price and value are legible on their own ground`() {
        for (p in listOf(ThemePalette.Dark, ThemePalette.Light)) {
            val ground = rgb(argbOf(p.background).toLong() and 0xFFFFFFFFL)
            assertTrue(
                "price is unreadable on the ${if (p.isDark) "dark" else "light"} ground",
                rgb(argbOf(p.price).toLong() and 0xFFFFFFFFL).contrastAgainst(ground) >= 3.0,
            )
            assertTrue(
                "value is unreadable on the ${if (p.isDark) "dark" else "light"} ground",
                rgb(argbOf(p.value).toLong() and 0xFFFFFFFFL).contrastAgainst(ground) >= 3.0,
            )
        }
    }

    @Test fun `price and value stay distinguishable from each other`() {
        // Hue separation, not WCAG contrast: amber and mint are deliberately close in lightness so
        // neither dominates, so their contrast ratio is ~1.1 and always will be. Hue is what
        // actually separates them.
        for (p in listOf(ThemePalette.Dark, ThemePalette.Light)) {
            val separation = rgb(argbOf(p.price).toLong() and 0xFFFFFFFFL)
                .hueSeparationFrom(rgb(argbOf(p.value).toLong() and 0xFFFFFFFFL))
            assertTrue("price and value are only ${separation.toInt()}° apart in hue", separation > 60)
        }
    }

    @Test fun `an accent never replaces price or value`() {
        for ((_, argb) in AccentChoice.presets) {
            for (base in listOf(ThemePalette.Dark, ThemePalette.Light)) {
                val themed = base.withAccent(argb)
                assertEquals("an accent moved the price color", base.price, themed.price)
                assertEquals("an accent moved the fair-value color", base.value, themed.value)
            }
        }
    }

    // ---- the user's accent ----

    @Test fun `accent foreground is computed not assumed`() {
        assertEquals(0xFF0B0D10.toInt(), argbOf(rgb(0xFFF5E27A).readableForeground()))
        assertEquals(0xFFFFFFFF.toInt(), argbOf(rgb(0xFF1B3A6B).readableForeground()))
    }

    @Test fun `an illegible accent is refused rather than applied`() {
        // Near-white on the light ground: contrast ~1.05, nobody could see the button.
        val refused = ThemePalette.Light.withAccent(0xFFF4F7F8)
        assertEquals("an unreadable accent was applied instead of being refused",
            ThemePalette.Light.accent, refused.accent)

        // The same colour is fine on the dark ground, so it must be accepted there.
        val accepted = ThemePalette.Dark.withAccent(0xFFF4F7F8)
        assertNotEquals(ThemePalette.Dark.accent, accepted.accent)
    }

    @Test fun `every offered preset is legible on at least one ground`() {
        for ((name, argb) in AccentChoice.presets) {
            val c = rgb(argb)
            assertTrue(
                "$name is unusable on both grounds, so it should not be offered",
                c.isLegibleOn(rgb(0xFF0B0D10)) || c.isLegibleOn(rgb(0xFFF1F4F5)),
            )
        }
    }

    // ---- resolution ----

    @Test fun `preference maps to the right ground`() {
        assertEquals(ThemePalette.Light, ThemePalette.base(dark = false))
        assertEquals(ThemePalette.Dark, ThemePalette.base(dark = true))
        assertEquals(ThemePreference.DARK, ThemePreference.from("DARK"))
        assertEquals("an unknown stored value must not crash or flip the app to light",
            ThemePreference.DARK, ThemePreference.from("nonsense"))
    }

    @Test fun `layout style round-trips and falls back safely`() {
        assertEquals(LayoutStyle.REPORT_CARD, LayoutStyle.from("REPORT_CARD"))
        assertEquals(LayoutStyle.DEFAULT, LayoutStyle.from(null))
        assertEquals(LayoutStyle.DEFAULT, LayoutStyle.from("something we removed"))
        assertEquals("the report card is the default (owner decision, 2026-09-24)",
            LayoutStyle.REPORT_CARD, LayoutStyle.DEFAULT)
        assertEquals("an explicit choice of Classic survives the new default",
            LayoutStyle.CLASSIC, LayoutStyle.from("CLASSIC"))
    }

    // ---- the layout contract (pure logic; see the class comment) ----

    // The same fixture files the iOS contract test uses, so both platforms reason about identical
    // reports. Mutated with `copy` where a state needs forcing.
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private fun fixture(name: String): ValuationReport =
        json.decodeFromString(File("src/test/resources/fixtures/$name.json").readText())

    private fun ValuationReport.withChecks(vararg statuses: String) = copy(
        dataChecks = statuses.mapIndexed { i, st ->
            DataCheck(key = "k$i", label = "check $i", status = st, message = "m")
        },
    )

    @Test fun `provenance is always owed`() {
        val report = fixture("MCD").withChecks("pass").copy(warnings = emptyList())
        assertTrue(
            "every layout owes the reader provenance, on every report",
            RequiredComponent.demandedBy(report, report.modelA).contains(RequiredComponent.PROVENANCE),
        )
    }

    @Test fun `a failing check demands the withheld-value component`() {
        val report = fixture("MCD").withChecks("pass", "fail").copy(warnings = emptyList())
        val demanded = RequiredComponent.demandedBy(report, report.modelA)
        assertTrue(demanded.contains(RequiredComponent.WITHHELD_VALUE))
        assertTrue("a failing check is also a data note", demanded.contains(RequiredComponent.DATA_NOTES))
    }

    @Test fun `an insufficient-data verdict demands it too, with every check passing`() {
        // CRWV's gate passes; it is model B that produces nothing a share price can be read from.
        val report = fixture("CRWV").withChecks("pass").copy(warnings = emptyList())
        assertEquals(Verdict.INSUFFICIENT, report.modelB.marginOfSafety.verdictEnum)
        assertTrue(
            "the gate can pass while the model still produces nothing",
            RequiredComponent.demandedBy(report, report.modelB).contains(RequiredComponent.WITHHELD_VALUE),
        )
    }

    @Test fun `a clean general-mode report owes nothing beyond provenance`() {
        val report = fixture("MCD").withChecks("pass").copy(warnings = emptyList())
        assertEquals("general", report.sector?.mode)
        assertEquals(setOf(RequiredComponent.PROVENANCE), RequiredComponent.demandedBy(report, report.modelA))
    }

    @Test fun `a non-general sector demands the mode badge`() {
        for (name in listOf("JPM", "O")) {
            val report = fixture(name).withChecks("pass").copy(warnings = emptyList())
            assertNotEquals("general", report.sector?.mode)
            assertTrue(
                "${report.sector?.mode} must announce itself",
                RequiredComponent.demandedBy(report, report.modelA).contains(RequiredComponent.SECTOR_MODE),
            )
        }
    }

    @Test fun `warnings alone demand data notes`() {
        val report = fixture("MCD").withChecks("pass")
        assertTrue("MCD carries the share-scale correction warning", report.warnings.isNotEmpty())
        assertTrue(RequiredComponent.demandedBy(report, report.modelA).contains(RequiredComponent.DATA_NOTES))
    }

    private fun argbOf(c: androidx.compose.ui.graphics.Color): Int =
        (((c.alpha * 255).toInt() and 0xFF) shl 24) or
        (((c.red * 255).toInt() and 0xFF) shl 16) or
        (((c.green * 255).toInt() and 0xFF) shl 8) or
        ((c.blue * 255).toInt() and 0xFF)
}
