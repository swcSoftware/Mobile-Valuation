import SwiftUI

/// Concept-map gaps for this filer: a line item the app couldn't resolve from SEC's tags.
/// Detection only — the map is never updated from the device (docs/TASKS.md Sprint 4 Track A).
struct CoverageGapCard: View {
    let coverage: CoverageReport
    let expert: Bool
    let onReport: () -> Void
    @State private var open = false

    private var summary: String {
        let n = coverage.gaps.count
        let critical = coverage.gaps.filter(\.critical).count
        if critical > 0 { return "\(critical) line item\(critical > 1 ? "s" : "") this app needs isn't in \(coverage.company)'s filings in a form we recognize." }
        return "\(n) line item\(n > 1 ? "s" : "") couldn't be matched to SEC's tags — some figures below may be missing."
    }

    var body: some View {
        Card {
            VStack(alignment: .leading, spacing: 8) {
                Button { withAnimation(.snappy) { open.toggle() } } label: {
                    HStack(alignment: .top) {
                        Image(systemName: "puzzlepiece.extension").foregroundStyle(Theme.warning)
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Unmatched line items").font(.vlHeadline).foregroundStyle(Theme.textPrimary)
                            Text(summary).font(.caption).foregroundStyle(Theme.textSecondary).fixedSize(horizontal: false, vertical: true)
                        }
                        Spacer()
                        Image(systemName: "chevron.down").font(.caption2.weight(.bold)).foregroundStyle(Theme.textTertiary)
                            .rotationEffect(.degrees(open ? 180 : 0))
                    }
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)

                if open {
                    VStack(alignment: .leading, spacing: 10) {
                        ForEach(coverage.gaps) { g in
                            VStack(alignment: .leading, spacing: 2) {
                                HStack {
                                    Text(Labels.concept(g.concept)).font(.subheadline).foregroundStyle(Theme.textPrimary)
                                    if g.critical { Pill(text: "needed", color: Theme.danger) }
                                }
                                Text(g.kind == "unusable" ? "Reported, but not in an annual or trailing-twelve-month form we can use."
                                     : g.kind == "stale" ? "In the annual report but missing from the latest quarter."
                                     : "This filer doesn't use any tag we recognize for it.")
                                    .font(.caption).foregroundStyle(Theme.textSecondary).fixedSize(horizontal: false, vertical: true)
                                if expert, !g.candidates.isEmpty {
                                    ForEach(g.candidates, id: \.self) { c in
                                        Text(Labels.sentence(c)).font(.caption).foregroundStyle(Theme.info).lineLimit(2)
                                    }
                                }
                            }
                        }
                        Button(action: onReport) {
                            Label("Report this to the ValueLens team", systemImage: "arrow.up.forward.square")
                                .font(.caption)
                        }
                        .buttonStyle(.bordered)
                        .tint(Theme.accent)
                        Text("Opens a prefilled report in your browser. Nothing is sent until you submit it, and it contains only public SEC identifiers — no personal data.")
                            .font(.caption2).foregroundStyle(Theme.textTertiary).fixedSize(horizontal: false, vertical: true)
                    }
                    .padding(.top, 2)
                }
            }
        }
    }
}
