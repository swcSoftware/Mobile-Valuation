import SwiftUI

/// "Show me the math" — the formulas, the SEC line items and the filed history.
///
/// **One shared presentation for every layout.** Two layouts times two modes would be four states to
/// keep honest; this keeps it to three that actually differ (Sprint 5 Track A). A layout chooses
/// *where* this sits and what surrounds it, never what it says.
struct ExpertBody: View {
    let ctx: LayoutContext

    private var report: ValuationReport { ctx.report }
    private var result: ModelResult { ctx.result }

    var body: some View {
        if report.company.displayName != report.company.name {
            // The display form is for reading; the filed name is the evidence.
            Text("Filed with the SEC as \(report.company.name) · CIK \(String(report.company.cik))")
                .font(.caption.monospaced())
                .foregroundStyle(Theme.textTertiary)
        }
        SectionHeader(title: result.name, subtitle: "Tap any metric for the formula and SEC line items")

        HStack {
            Spacer()
            Button("Expand all") { ctx.expandAll.wrappedValue = true; ctx.expandVersion.wrappedValue += 1 }
                .font(.caption)
            Button("Collapse all") { ctx.expandAll.wrappedValue = false; ctx.expandVersion.wrappedValue += 1 }
                .font(.caption)
                .foregroundStyle(Theme.textSecondary)
        }

        Card {
            VStack(spacing: 14) {
                MetricRow(metric: result.composite,
                          emphasize: true,
                          expandAll: ctx.expandAll.wrappedValue,
                          expandVersion: ctx.expandVersion.wrappedValue)
                Divider().overlay(Theme.border)
                ForEach(result.metrics.filter { $0.key != "fcff_projection" }) { metric in
                    MetricRow(metric: metric,
                              expandAll: ctx.expandAll.wrappedValue,
                              expandVersion: ctx.expandVersion.wrappedValue)
                }
            }
        }

        if let betaDetail = report.provenance["beta_detail"] {
            Text(betaDetail).font(.caption).foregroundStyle(Theme.textTertiary)
        }

        if !report.shareClasses.isEmpty {
            SectionHeader(title: "Share classes",
                          subtitle: "From the filing cover page; ratios from per-class EPS, in \(report.company.ticker) share terms")
            Card {
                VStack(spacing: 8) {
                    ForEach(report.shareClasses) { shareClass in
                        HStack {
                            Text("Class \(shareClass.cls)\(shareClass.ticker.map { " · \($0)" } ?? " · not traded")")
                                .foregroundStyle(Theme.textPrimary)
                            Spacer()
                            Text(Fmt.number(shareClass.shares, decimals: 0))
                                .font(.body.monospacedDigit())
                                .foregroundStyle(Theme.textSecondary)
                            Text("× \(Fmt.number(shareClass.ratioToSearched, decimals: shareClass.ratioToSearched >= 10 ? 0 : 3))")
                                .font(.caption.monospacedDigit())
                                .foregroundStyle(Theme.textTertiary)
                                .frame(width: 70, alignment: .trailing)
                        }
                    }
                }
            }
        }

        SectionHeader(title: "Balance sheet & quality", subtitle: "Trailing twelve months")
        Card { SnapshotGrid(snapshot: report.snapshot) }

        SectionHeader(title: "10-K history", subtitle: "\(report.history.count) fiscal years from annual filings")
        Card { HistoryCharts(history: report.history) }
        Card(padding: 0) { HistoryTable(history: report.history) }

        SectionHeader(title: "Growth (CAGR)")
        Card { GrowthGrid(growth: report.growth) }

        SectionHeader(title: "Assumptions",
                      subtitle: "Rates: \(report.assumptions.rateSource) · beta: \(report.provenance["beta"] ?? "?")")
        Card { AssumptionsGrid(a: report.assumptions, betaSource: report.provenance["beta"]) }

        // Warnings used to sit here, expert-only. They are now `DataNotesSection`, which every
        // layout places in both modes.

        if ctx.showingMathTemporarily {
            Button("Hide the math") { withAnimation { ctx.setShowMath(false) } }
                .font(.caption)
                .foregroundStyle(Theme.textSecondary)
                .frame(maxWidth: .infinity)
        }
    }
}
