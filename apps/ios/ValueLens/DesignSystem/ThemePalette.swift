import SwiftUI

/// Every color the app draws with, as data.
///
/// Sprint 5 Track B: these used to be `static let` constants on `Theme`, so a light ground was
/// impossible. They are now resolved at runtime from three **independent** axes (docs/DESIGN.md):
///
/// | Axis   | Chosen by                  | Controls                                        |
/// |--------|----------------------------|-------------------------------------------------|
/// | Theme  | Light / Dark / System      | ground, text, and the semantic colors per ground |
/// | Accent | the user                   | chrome only: buttons, tabs, selection, focus     |
/// | Layout | Classic / Report card      | typography and components — never a color token  |
struct ThemePalette: Equatable {
    var background: Color
    var surface: Color
    var surfaceRaised: Color
    var border: Color
    var textPrimary: Color
    var textSecondary: Color
    var textTertiary: Color

    /// Market price. **Always amber, never the user's accent.**
    var price: Color
    /// Intrinsic / fair value. **Always mint, never the user's accent.**
    var value: Color
    var danger: Color
    var warning: Color
    var info: Color

    /// Chrome: buttons, tabs, selection, focus. The one color the user may choose.
    var accent: Color
    /// Text drawn on top of `accent`, computed from its luminance rather than assumed.
    var accentForeground: Color

    var colorScheme: ColorScheme
}

extension ThemePalette {
    /// The values that shipped in Sprints 0–4, unchanged to the component. Track B must not alter
    /// how the dark app looks.
    static let dark = ThemePalette(
        background: Color(red: 0.043, green: 0.051, blue: 0.063),   // #0B0D10
        surface: Color(red: 0.082, green: 0.094, blue: 0.114),      // #15181D
        surfaceRaised: Color(red: 0.118, green: 0.133, blue: 0.157),
        border: Color.white.opacity(0.08),
        textPrimary: Color(white: 0.95),
        textSecondary: Color(white: 0.62),
        textTertiary: Color(white: 0.42),
        price: Color(red: 0.96, green: 0.65, blue: 0.14),
        value: Color(red: 0.18, green: 0.85, blue: 0.62),
        danger: Color(red: 0.94, green: 0.33, blue: 0.31),
        warning: Color(red: 0.98, green: 0.78, blue: 0.25),
        info: Color(red: 0.36, green: 0.62, blue: 0.98),
        accent: Color(red: 0.18, green: 0.85, blue: 0.62),          // mint, matching the old tint
        accentForeground: Color(red: 0.043, green: 0.051, blue: 0.063),
        colorScheme: .dark
    )

    /// Mint and amber both fail contrast as text on paper at their dark-theme values, so the light
    /// ground gets darker versions of the same two meanings. They stay amber and mint; they never
    /// become something else, because that pair is how the app is read.
    static let light = ThemePalette(
        background: Color(hex: 0xF1F4F5),
        surface: .white,
        surfaceRaised: Color(hex: 0xE7EDEF),
        border: Color(hex: 0x10181C).opacity(0.13),
        textPrimary: Color(hex: 0x14181B),
        textSecondary: Color(hex: 0x56646A),
        textTertiary: Color(hex: 0x7B888D),
        price: Color(hex: 0x9A6006),
        value: Color(hex: 0x0B7A57),
        danger: Color(hex: 0xB4291F),
        warning: Color(hex: 0x8A5D00),
        info: Color(hex: 0x1B5FA8),
        accent: Color(hex: 0x0B7A57),
        accentForeground: .white,
        colorScheme: .light
    )

    static func base(for scheme: ColorScheme) -> ThemePalette {
        scheme == .dark ? .dark : .light
    }

    /// Applies the user's accent, if they chose one and it is legible on this ground.
    func withAccent(_ hex: UInt32?) -> ThemePalette {
        guard let hex else { return self }
        let chosen = RGB(hex: hex)
        guard chosen.isLegible(on: RGB(hex: colorScheme == .dark ? 0x0B0D10 : 0xF1F4F5)) else { return self }
        var out = self
        out.accent = Color(hex: hex)
        out.accentForeground = chosen.readableForeground
        return out
    }
}

// MARK: - contrast

/// Just enough color math to keep a user-chosen accent legible. A pale accent on white would
/// otherwise give white-on-white buttons, and "it looked fine when I picked it" is not a check.
struct RGB {
    let r: Double, g: Double, b: Double

    init(hex: UInt32) {
        r = Double((hex >> 16) & 0xFF) / 255
        g = Double((hex >> 8) & 0xFF) / 255
        b = Double(hex & 0xFF) / 255
    }

    /// WCAG relative luminance.
    var luminance: Double {
        func channel(_ c: Double) -> Double {
            c <= 0.03928 ? c / 12.92 : pow((c + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b)
    }

    func contrast(against other: RGB) -> Double {
        let a = luminance, b = other.luminance
        return (max(a, b) + 0.05) / (min(a, b) + 0.05)
    }

    /// Black or white, whichever the eye can actually read on this color.
    var readableForeground: Color {
        contrast(against: RGB(hex: 0x0B0D10)) >= contrast(against: RGB(hex: 0xFFFFFF))
            ? Color(hex: 0x0B0D10) : .white
    }

    /// 3:1 is the WCAG minimum for a UI component boundary against its background. An accent that
    /// cannot clear it is refused rather than drawn, and the picker says why.
    func isLegible(on ground: RGB) -> Bool { contrast(against: ground) >= 3.0 }
}

extension Color {
    init(hex: UInt32) {
        self.init(
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255
        )
    }
}

// MARK: - the user's choice

enum ThemePreference: String, CaseIterable, Codable, Sendable {
    case system, light, dark

    var displayName: String {
        switch self {
        case .system: "System"
        case .light: "Light"
        case .dark: "Dark"
        }
    }

    /// `nil` means "follow the device", which is what `preferredColorScheme` wants.
    var forcedScheme: ColorScheme? {
        switch self {
        case .system: nil
        case .light: .light
        case .dark: .dark
        }
    }
}

/// The accents offered in Settings. A user may also pick any color; it is contrast-checked first.
enum AccentChoice {
    static let presets: [(name: String, hex: UInt32)] = [
        ("Mint", 0x2ED99E),
        ("Forest", 0x0B7A57),
        ("Azure", 0x5C9EFA),
        ("Iris", 0x7C6BF5),
        ("Coral", 0xE0655F),
        ("Ochre", 0xD98A27),
        ("Fuchsia", 0xC2569C),
        ("Slate", 0x4A5560),
    ]
}
