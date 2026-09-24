import SwiftUI

/// The Index: every term the app shows, with the SEC tag(s) behind it (Sprint 6, owner decision).
///
/// Value screens show plain language only; this page is the path back to the filing for anyone who
/// wants to check a number themselves. Data comes from the core (`DisplayLabels.index()`), so it
/// always matches the concept map the app actually reads.
struct TagIndexView: View {
    @State private var query = ""
    private let entries = CoreValuationRepository.tagIndex()

    private var filtered: [TagIndexEntry] {
        let q = query.trimmingCharacters(in: .whitespaces).lowercased()
        guard !q.isEmpty else { return entries }
        return entries.filter { e in
            e.term.lowercased().contains(q) || e.tags.contains { $0.tag.lowercased().contains(q) || $0.label.lowercased().contains(q) }
        }
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                VStack(alignment: .leading, spacing: 8) {
                    Text("Every figure Alpha shows is read from a company's SEC filing, where it is recorded under a standard tag. These are the tags behind the terms you see, if you want to find a number in the filing yourself.")
                    Text("On sec.gov, open the company's 10-K or 10-Q in the Inline XBRL viewer (the iXBRL link beside the document) and search for the tag. Where a term lists several tags, Alpha uses the first one the company actually filed.")
                }
                .font(.caption)
                .foregroundStyle(Theme.textSecondary)
                .fixedSize(horizontal: false, vertical: true)

                ForEach(filtered) { entry in
                    Card {
                        VStack(alignment: .leading, spacing: 8) {
                            Text(entry.term).font(.vlHeadline).foregroundStyle(Theme.textPrimary)
                            ForEach(Array(entry.tags.enumerated()), id: \.element) { i, t in
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(t.tag)
                                        .font(.vlMono)
                                        .foregroundStyle(i == 0 ? Theme.textPrimary : Theme.textSecondary)
                                        .textSelection(.enabled)   // so it can be copied into a search
                                    Text(t.label).font(.caption2).foregroundStyle(Theme.textTertiary)
                                        .fixedSize(horizontal: false, vertical: true)
                                }
                                .accessibilityElement(children: .combine)
                            }
                        }
                    }
                }

                if filtered.isEmpty {
                    Text("Nothing matches \u{201C}\(query)\u{201D}.").font(.caption).foregroundStyle(Theme.textTertiary)
                }
            }
            .padding(16)
        }
        .background(Theme.background)
        .searchable(text: $query, prompt: "Search terms or tags")
        .navigationTitle("Index")
        .navigationBarTitleDisplayMode(.inline)
    }
}
