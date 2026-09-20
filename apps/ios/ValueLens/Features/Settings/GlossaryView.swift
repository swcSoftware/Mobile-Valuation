import SwiftUI

/// Plain-English definitions from the shared core (Explain.kt); expert paragraph when Expert Mode is on.
struct GlossaryView: View {
    @Environment(AppSettings.self) private var settings
    var body: some View {
        let entries = settings.repository.glossary()
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Text(settings.expertMode ? "Plain English first, expert detail below each term." : "Plain English first. Turn on Expert Mode in Settings for the full definitions.")
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
                }
            }
            .padding(16)
        }
        .background(Theme.background)
        .navigationTitle("Glossary")
        .navigationBarTitleDisplayMode(.inline)
    }
}
