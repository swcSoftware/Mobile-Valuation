import SwiftUI

/// Typographic-first, dark-by-default palette. Market price and fair value are always
/// distinguishable by color: price = amber, intrinsic/fair value = mint.
enum Theme {
    static let background = Color(red: 0.043, green: 0.051, blue: 0.063)   // #0B0D10
    static let surface = Color(red: 0.082, green: 0.094, blue: 0.114)      // #15181D
    static let surfaceRaised = Color(red: 0.118, green: 0.133, blue: 0.157)
    static let border = Color.white.opacity(0.08)
    static let textPrimary = Color(white: 0.95)
    static let textSecondary = Color(white: 0.62)
    static let textTertiary = Color(white: 0.42)

    static let price = Color(red: 0.96, green: 0.65, blue: 0.14)           // amber
    static let value = Color(red: 0.18, green: 0.85, blue: 0.62)           // mint
    static let danger = Color(red: 0.94, green: 0.33, blue: 0.31)
    static let warning = Color(red: 0.98, green: 0.78, blue: 0.25)
    static let info = Color(red: 0.36, green: 0.62, blue: 0.98)

    static func verdictColor(_ v: Verdict) -> Color {
        switch v {
        case .deepValue, .withinMargin: value
        case .thinMargin: warning
        case .aboveIntrinsic: danger
        case .insufficientData: textTertiary
        }
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
        .tint(Theme.value)
        .foregroundStyle(Theme.background)
        .disabled(!enabled)
    }
}
