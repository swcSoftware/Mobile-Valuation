import XCTest
import SwiftUI
@testable import ValueLens

/// Sprint 5 Track B. The rules here are the ones that would fail silently: a palette that looks
/// fine in the simulator can still be unreadable for someone else's accent, and "the dark app is
/// unchanged" is a claim worth pinning down.
@MainActor
final class ThemeTests: XCTestCase {

    // MARK: - the refactor must not move the dark app

    func testDarkPaletteStillCarriesTheShippedValues() {
        // Spot-checks against the literal values from the pre-Sprint-5 `Theme` enum. If a refactor
        // shifts these, the dark app changed and the commit claiming otherwise is wrong.
        let dark = ThemePalette.dark
        XCTAssertEqual(dark.background.rgb, RGB(hex: 0x0B0D10), accuracy: 0.004)
        XCTAssertEqual(dark.price.rgb, RGB(hex: 0xF5A623), accuracy: 0.006)
        XCTAssertEqual(dark.value.rgb, RGB(hex: 0x2ED99E), accuracy: 0.006)
        XCTAssertEqual(dark.colorScheme, .dark)
    }

    // MARK: - the semantic pair

    func testPriceAndValueAreLegibleOnTheirOwnGround() {
        for palette in [ThemePalette.dark, ThemePalette.light] {
            let ground = palette.background.rgb
            XCTAssertGreaterThanOrEqual(palette.price.rgb.contrast(against: ground), 3.0,
                                        "price is unreadable on the \(palette.colorScheme) ground")
            XCTAssertGreaterThanOrEqual(palette.value.rgb.contrast(against: ground), 3.0,
                                        "value is unreadable on the \(palette.colorScheme) ground")
        }
    }

    func testPriceAndValueStayDistinguishableFromEachOther() {
        // They are the whole reading of the screen: what you pay against what it is worth.
        //
        // Measured by hue separation, not by WCAG contrast. Contrast ratio answers "can you read
        // text on this background", and amber and mint are *deliberately* close in lightness so
        // neither dominates — their ratio against each other is ~1.1 and always will be. Hue is
        // what actually separates them.
        for palette in [ThemePalette.dark, ThemePalette.light] {
            let separation = palette.price.rgb.hueSeparation(from: palette.value.rgb)
            XCTAssertGreaterThan(separation, 60,
                                 "price and value are only \(Int(separation))° apart in hue on the \(palette.colorScheme) ground")
        }
    }

    func testAnAccentNeverReplacesPriceOrValue() {
        for hex in AccentChoice.presets.map(\.hex) {
            for base in [ThemePalette.dark, ThemePalette.light] {
                let themed = base.withAccent(hex)
                XCTAssertEqual(themed.price, base.price, "an accent moved the price color")
                XCTAssertEqual(themed.value, base.value, "an accent moved the fair-value color")
            }
        }
    }

    // MARK: - the user's accent

    func testAccentForegroundIsComputedNotAssumed() {
        // A pale accent needs dark text; a deep one needs white. Assuming either gives an
        // unreadable button for half the palette.
        XCTAssertEqual(RGB(hex: 0xF5E27A).readableForeground, Color(hex: 0x0B0D10))
        XCTAssertEqual(RGB(hex: 0x1B3A6B).readableForeground, .white)
    }

    func testAnIllegibleAccentIsRefusedRatherThanApplied() {
        // Near-white on the light ground: contrast ~1.05, nobody could see the button.
        let refused = ThemePalette.light.withAccent(0xF4F7F8)
        XCTAssertEqual(refused.accent, ThemePalette.light.accent,
                       "an unreadable accent was applied instead of being refused")

        // The same color is fine on the dark ground, so it must be accepted there.
        let accepted = ThemePalette.dark.withAccent(0xF4F7F8)
        XCTAssertNotEqual(accepted.accent, ThemePalette.dark.accent)
    }

    func testEveryOfferedPresetIsLegibleOnAtLeastOneGround() {
        for preset in AccentChoice.presets {
            let rgb = RGB(hex: preset.hex)
            XCTAssertTrue(rgb.isLegible(on: RGB(hex: 0x0B0D10)) || rgb.isLegible(on: RGB(hex: 0xF1F4F5)),
                          "\(preset.name) is unusable on both grounds, so it should not be offered")
        }
    }

    // MARK: - resolution

    func testPreferenceBeatsTheDeviceAndSystemFollowsIt() {
        let controller = ThemeController()
        controller.resolve(preference: .light, system: .dark, accentHex: nil)
        XCTAssertEqual(controller.palette.colorScheme, .light, "an explicit choice must beat the device")

        controller.resolve(preference: .system, system: .dark, accentHex: nil)
        XCTAssertEqual(controller.palette.colorScheme, .dark, "system must follow the device")
    }

    func testGenerationChangesOnlyWhenThePaletteDoes() {
        // The generation is what forces the tree to rebuild; if it moved on every resolve the whole
        // UI would be thrown away on any settings touch, and if it never moved the app would keep
        // drawing with stale colors.
        let controller = ThemeController()
        controller.resolve(preference: .dark, system: .dark, accentHex: nil)
        let settled = controller.generation

        controller.resolve(preference: .dark, system: .dark, accentHex: nil)
        XCTAssertEqual(controller.generation, settled, "an unchanged palette rebuilt the tree")

        controller.resolve(preference: .light, system: .dark, accentHex: nil)
        XCTAssertEqual(controller.generation, settled + 1, "a changed palette did not rebuild the tree")
    }

    func testResolvingWritesTheStaticTheEntireAppReads() {
        // `Theme.current` is a mutable static, which is only safe because this is its single writer.
        let controller = ThemeController()
        controller.resolve(preference: .light, system: .dark, accentHex: nil)
        XCTAssertEqual(Theme.current.colorScheme, .light)
        controller.resolve(preference: .dark, system: .light, accentHex: nil)
        XCTAssertEqual(Theme.current.colorScheme, .dark)
    }
}

// MARK: - helpers

private extension Color {
    /// Resolves a SwiftUI colour back to components so palettes can be asserted against hex values.
    var rgb: RGB {
        var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
        UIColor(self).getRed(&r, green: &g, blue: &b, alpha: &a)
        return RGB(r: Double(r), g: Double(g), b: Double(b))
    }
}

private extension RGB {
    /// Degrees of hue between two colors, the short way round the wheel.
    func hueSeparation(from other: RGB) -> Double {
        let delta = abs(hueDegrees - other.hueDegrees)
        return min(delta, 360 - delta)
    }

    var hueDegrees: Double {
        let maxC = max(r, g, b), minC = min(r, g, b), chroma = maxC - minC
        guard chroma > 0 else { return 0 }
        let hue: Double
        switch maxC {
        case r: hue = 60 * ((g - b) / chroma).truncatingRemainder(dividingBy: 6)
        case g: hue = 60 * (((b - r) / chroma) + 2)
        default: hue = 60 * (((r - g) / chroma) + 4)
        }
        return hue < 0 ? hue + 360 : hue
    }

    init(r: Double, g: Double, b: Double) {
        self.init(hex: (UInt32(max(0, min(255, r * 255)).rounded()) << 16)
                     | (UInt32(max(0, min(255, g * 255)).rounded()) << 8)
                     |  UInt32(max(0, min(255, b * 255)).rounded()))
    }
}

private func XCTAssertEqual(_ lhs: RGB, _ rhs: RGB, accuracy: Double,
                            _ message: @autoclosure () -> String = "",
                            file: StaticString = #filePath, line: UInt = #line) {
    XCTAssertEqual(lhs.r, rhs.r, accuracy: accuracy, message(), file: file, line: line)
    XCTAssertEqual(lhs.g, rhs.g, accuracy: accuracy, message(), file: file, line: line)
    XCTAssertEqual(lhs.b, rhs.b, accuracy: accuracy, message(), file: file, line: line)
}
