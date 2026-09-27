import SwiftUI

/// Sprint 8: Expert Mode text with its glossary terms dotted-underlined. Tapping one opens a half-height
/// sheet with the definition (`.glossarySheet()` at the root), so the reader never leaves the screen.
///
/// Which words link is decided by the core (`Glossary.link`), so iOS and Android underline the same ones.
/// Outside Expert Mode this is plain `Text`.
struct GlossaryText: View {
    let text: String
    let color: Color
    @Environment(AppSettings.self) private var settings
    @Environment(\.glossaryLinksEnabled) private var screenShowsMath

    init(_ text: String, color: Color) { self.text = text; self.color = color }

    var body: some View {
        Group {
            if settings.expertMode || screenShowsMath { Text(Self.attributed(text)) } else { Text(text) }
        }
        .foregroundStyle(color)
        .tint(color)   // a linked term keeps the text's color; the dotted underline marks it
    }

    static func attributed(_ text: String) -> AttributedString {
        var out = AttributedString()
        for span in CoreValuationRepository.glossaryLinks(text) {
            var run = AttributedString(span.text)
            if let key = span.key, let url = GlossaryLink.url(key) {
                run.link = url
                run.swiftUI.underlineStyle = Text.LineStyle(pattern: .dot)
            }
            out += run
        }
        return out
    }
}

extension EnvironmentValues {
    /// Set by a company screen while it shows expert detail without global Expert Mode ("Show me the math").
    @Entry var glossaryLinksEnabled: Bool = false
}

enum GlossaryLink {
    static let scheme = "alpha-glossary"
    static func url(_ key: String) -> URL? { URL(string: "\(scheme):\(key)") }
    static func key(_ url: URL) -> String? {
        guard url.scheme == scheme else { return nil }
        return String(url.absoluteString.dropFirst(scheme.count + 1))
    }
}

private struct GlossarySelection: Identifiable { let key: String; var id: String { key } }

/// Catches taps on glossary terms anywhere below it; every other link keeps its normal behavior.
private struct GlossarySheetModifier: ViewModifier {
    @State private var selected: GlossarySelection?

    func body(content: Content) -> some View {
        content
            .environment(\.openURL, OpenURLAction { url in
                guard let key = GlossaryLink.key(url) else { return .systemAction }
                selected = GlossarySelection(key: key)
                return .handled
            })
            .sheet(item: $selected) { sel in
                GlossaryDefinitionSheet(key: sel.key)
                    .presentationDetents([.medium, .large])
                    .presentationDragIndicator(.visible)
            }
    }
}

extension View {
    func glossarySheet() -> some View { modifier(GlossarySheetModifier()) }
}

/// The definition, over the current screen. "See it in the Glossary" opens the full list at this entry.
struct GlossaryDefinitionSheet: View {
    let key: String
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ScrollView {
                if let e = CoreValuationRepository.glossaryEntry(key) {
                    VStack(alignment: .leading, spacing: 12) {
                        Text(e.term).font(.vlHeadline).foregroundStyle(Theme.textPrimary)
                        Text(e.plain).font(.vlBody).foregroundStyle(Theme.textSecondary).fixedSize(horizontal: false, vertical: true)
                        Text(e.expert).font(.callout).foregroundStyle(Theme.textTertiary).fixedSize(horizontal: false, vertical: true)
                        NavigationLink {
                            GlossaryView(focus: key)
                        } label: {
                            Label("See it in the Glossary", systemImage: "book").font(.callout.weight(.medium))
                        }
                        .padding(.top, 4)
                    }
                    .padding(20)
                    .frame(maxWidth: .infinity, alignment: .leading)
                } else {
                    Text("No definition for this term yet.").foregroundStyle(Theme.textSecondary).padding(20)
                }
            }
            .background(Theme.background)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } } }
            .navigationBarTitleDisplayMode(.inline)
        }
        .tint(Theme.accent)   // a sheet doesn't inherit the root's tint; without this its buttons use the asset color
    }
}
