import SwiftUI

/// Plain-English definitions from the shared core (labels/glossary.json); expert paragraph when Expert
/// Mode is on. `focus` scrolls to and highlights one entry (opened from a term's definition sheet).
struct GlossaryView: View {
    var focus: String? = nil
    @Environment(AppSettings.self) private var settings

    var body: some View {
        let entries = CoreValuationRepository.glossaryEntries
        ScrollViewReader { proxy in
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    Text(settings.expertMode ? "Plain English first, expert detail below each term. In Expert Mode, tap any dotted-underlined term to see its definition in place." : "Plain English first. Turn on Expert Mode in Settings for the full definitions.")
                        .font(.caption).foregroundStyle(Theme.textSecondary)
                    ForEach(entries) { g in
                        Card {
                            VStack(alignment: .leading, spacing: 6) {
                                Text(g.term).font(.vlHeadline).foregroundStyle(Theme.textPrimary)
                                Text(g.plain).font(.vlBody).foregroundStyle(Theme.textSecondary).fixedSize(horizontal: false, vertical: true)
                                if settings.expertMode {
                                    Text(g.expert).font(.caption).foregroundStyle(Theme.textTertiary).fixedSize(horizontal: false, vertical: true)
                                }
                            }
                        }
                        .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).stroke(Theme.accent, lineWidth: g.key == focus ? 2 : 0))
                        .id(g.key)
                    }
                }
                .padding(16)
            }
            .onAppear { if let focus { proxy.scrollTo(focus, anchor: .top) } }
        }
        .background(Theme.background)
        .navigationTitle("Glossary")
        .navigationBarTitleDisplayMode(.inline)
    }
}
