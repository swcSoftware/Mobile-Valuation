import SwiftUI

/// The app's colors, by name.
///
/// **Sprint 5 Track B.** These were `static let` constants, referenced 193 times across 15 files,
/// which is why a light ground was impossible. They are now computed from `current`, a palette the
/// root resolves from the user's theme preference, the device's appearance and their chosen accent.
///
/// The call sites did not change, on purpose: a 193-site rewrite to thread an `@Environment` value
/// through every view is exactly the kind of global churn CLAUDE.md non-negotiable 4 warns about,
/// and it would have made "did the dark app change?" impossible to answer by reading the diff.
///
/// **The invariant that makes a mutable static safe here**: `current` is written in exactly one
/// place, `ThemeController.resolve`, which also bumps a generation the root view uses as its `.id`.
/// A palette change therefore rebuilds the whole tree, so no view can be left holding colors from
/// the previous palette. Nothing else may write to it.
@MainActor
enum Theme {
    /// Written only by `ThemeController.resolve`. See the invariant above.
    static var current: ThemePalette = .dark

    static var background: Color { current.background }
    static var surface: Color { current.surface }
    static var surfaceRaised: Color { current.surfaceRaised }
    static var border: Color { current.border }
    static var textPrimary: Color { current.textPrimary }
    static var textSecondary: Color { current.textSecondary }
    static var textTertiary: Color { current.textTertiary }

    /// Market price is always amber and fair value is always mint, on any ground and whatever accent
    /// the user picks. That pair is how the app is read; if either could be restyled the reading
    /// breaks (docs/DESIGN.md, "Semantic colors").
    static var price: Color { current.price }
    static var value: Color { current.value }
    static var danger: Color { current.danger }
    static var warning: Color { current.warning }
    static var info: Color { current.info }

    /// Chrome only.
    static var accent: Color { current.accent }
    static var accentForeground: Color { current.accentForeground }

    static func verdictColor(_ v: Verdict) -> Color {
        switch v {
        case .deepValue, .withinMargin: value
        case .thinMargin: warning
        case .aboveIntrinsic: danger
        case .insufficientData: textTertiary
        }
    }
}

/// Owns the one write to `Theme.current`, and the generation that forces the tree to rebuild with it.
@Observable
@MainActor
final class ThemeController {
    private(set) var palette: ThemePalette = .dark
    /// Changes whenever the palette does; the root view uses it as `.id` so nothing keeps stale colors.
    private(set) var generation: Int = 0

    func resolve(preference: ThemePreference, system: ColorScheme, accentHex: UInt32?) {
        let next = ThemePalette
            .base(for: preference.forcedScheme ?? system)
            .withAccent(accentHex)
        guard next != palette else { return }
        palette = next
        Theme.current = next
        generation += 1
    }
}

extension Font {
    static let vlDisplay = Font.system(size: 40, weight: .bold, design: .rounded)
    static let vlTitle = Font.system(.title2, design: .rounded).weight(.semibold)
    static let vlHeadline = Font.system(.headline, design: .rounded)
    static let vlBody = Font.system(.body)
    static let vlMono = Font.system(.footnote, design: .monospaced)
    static let vlNumber = Font.system(.title3, design: .rounded).weight(.semibold).monospacedDigit()
    static let vlCaption = Font.system(.caption)

    /// The app's one display face at any size: SF Pro Rounded, the face the classic layout has used
    /// since Sprint 0. Every layout uses it (owner decision, 2026-09-24) — a layout changes
    /// arrangement and emphasis, never the typeface.
    static func vlRounded(_ size: CGFloat, _ weight: Font.Weight = .bold) -> Font {
        .system(size: size, weight: weight, design: .rounded)
    }
}

struct Card<Content: View>: View {
    var padding: CGFloat = 16
    @ViewBuilder var content: Content
    var body: some View {
        content
            .padding(padding)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Theme.surface, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).stroke(Theme.border))
    }
}

struct SectionHeader: View {
    let title: String
    var subtitle: String? = nil
    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(title.uppercased()).font(.caption.weight(.semibold)).tracking(1.2).foregroundStyle(Theme.textSecondary)
            if let subtitle { Text(subtitle).font(.caption).foregroundStyle(Theme.textTertiary) }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.top, 8)
    }
}

struct Pill: View {
    let text: String
    let color: Color
    var body: some View {
        Text(text)
            .font(.caption.weight(.semibold))
            .padding(.horizontal, 10).padding(.vertical, 5)
            .background(color.opacity(0.16), in: Capsule())
            .foregroundStyle(color)
    }
}

struct PrimaryButton: View {
    let title: String
    var enabled: Bool = true
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            Text(title).font(.vlHeadline).frame(maxWidth: .infinity).padding(.vertical, 14)
        }
        .buttonStyle(.borderedProminent)
        // Chrome takes the accent, never the fair-value colour: a button is not a valuation.
        .tint(Theme.accent)
        .foregroundStyle(Theme.accentForeground)
        .disabled(!enabled)
    }
}
