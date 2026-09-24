import SwiftUI

/// What a layout is allowed to see.
///
/// Deliberately narrow: the report, the selected model's result, the core's plain-language summary,
/// and a few callbacks for things only the container can do (present a sheet, switch modes). A
/// layout cannot reach the repository, the network or the core, which is the mechanical reason it
/// cannot compute a number of its own (CLAUDE.md non-negotiable 7).
struct LayoutContext {
    let report: ValuationReport
    let result: ModelResult
    let explain: ExplainSummary?
    /// Expert Mode, or "show me the math" tapped for this company. One shared presentation across
    /// layouts — see `ExpertBody`.
    let expert: Bool
    /// True when Expert Mode is off and the reader turned the math on just for this screen.
    let showingMathTemporarily: Bool
    let model: Binding<ValuationModel>
    let editPrice: () -> Void
    let setShowMath: (Bool) -> Void
    let expandAll: Binding<Bool?>
    let expandVersion: Binding<Int>
    let reportIssue: () -> Void
    /// The core's grades, rules and chips for the active lens. Only the report card reads it; nil
    /// until the core has answered, and a layout must render sensibly without it.
    var reportCard: ReportCardSummary? = nil
    /// Flips Value ⇄ Growth. The lens is shown on the screen it affects, so it is switched there too.
    var swapLens: () -> Void = {}

    var demandedComponents: Set<RequiredComponent> {
        RequiredComponent.demanded(by: report, result: result)
    }
}

/// The layout that shipped in Sprints 0–4, unchanged in substance and in order.
///
/// Two deliberate differences from the pre-Sprint-5 file, both flagged in the commit:
///  1. The inline "value withheld", sector-mode and warnings markup became the shared
///     `RequiredComponents`, so the second layout cannot quietly tell a different story.
///  2. Data notes are no longer expert-only. They were hidden in basic mode, which is exactly the
///     omission `RequiredComponent.dataNotes` exists to prevent.
struct ClassicLayout: View {
    let ctx: LayoutContext

    private var report: ValuationReport { ctx.report }
    private var result: ModelResult { ctx.result }

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            header

            ModelToggle(model: ctx.model,
                        expert: ctx.expert,
                        blurbOverride: ctx.expert ? nil : (ctx.model.wrappedValue == .traditional ? ctx.explain?.blurbA : ctx.explain?.blurbB))

            if let sector = report.sector, sector.mode != "general" {
                SectorModeBadge(sector: sector)
            }

            if report.checksFailed {
                WithheldValueCard(report: report, result: result)
            } else {
                Card {
                    VStack(alignment: .leading, spacing: 12) {
                        if !ctx.expert, let explain = ctx.explain {
                            Text(ctx.model.wrappedValue == .traditional ? explain.verdictA : explain.verdictB)
                                .font(.vlBody)
                                .foregroundStyle(Theme.textPrimary)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                        MarginOfSafetyView(mos: result.marginOfSafety, compact: !ctx.expert)
                    }
                }
                if result.marginOfSafety.verdict == .insufficientData {
                    WithheldValueCard(report: report, result: result)
                }
            }

            DataChecksCard(checks: report.dataChecks,
                           summary: ctx.explain?.checksSummary ?? "\(report.dataChecks.count) checks run",
                           expanded: ctx.expert)

            if let coverage = report.coverage, !coverage.gaps.isEmpty {
                CoverageGapCard(coverage: coverage, expert: ctx.expert, onReport: ctx.reportIssue)
            }

            // Was expert-only before Sprint 5; a basic-mode reader was not told about MCD's
            // share-scale correction or BRK-B's derived EPS at all.
            DataNotesSection(report: report, includeChecks: false)

            if ctx.expert {
                ExpertBody(ctx: ctx)
            } else {
                basicBody
            }

            ProvenanceRow(report: report)

            Text(report.disclaimer)
                .font(.caption2)
                .foregroundStyle(Theme.textTertiary)
                .multilineTextAlignment(.center)
                .frame(maxWidth: .infinity)
        }
        .padding(16)
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(report.company.name).font(.vlTitle).foregroundStyle(Theme.textPrimary)
            HStack(alignment: .firstTextBaseline, spacing: 12) {
                Button(action: ctx.editPrice) {
                    HStack(spacing: 4) {
                        Text(Fmt.money(report.price)).font(.vlDisplay).foregroundStyle(Theme.price)
                        Image(systemName: "pencil.circle").font(.caption).foregroundStyle(Theme.textTertiary)
                    }
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Market price \(Fmt.money(report.price)). Tap to override.")
                VStack(alignment: .leading, spacing: 0) {
                    Text(report.quote.map { "\($0.source) · \(Fmt.shortDate($0.asOf))" } ?? "no quote — tap to enter")
                        .font(.caption2).foregroundStyle(Theme.textTertiary)
                    if ctx.expert {
                        Text("CIK " + String(report.company.cik)).font(.caption2).foregroundStyle(Theme.textTertiary)
                    }
                }
            }
        }
    }

    @ViewBuilder
    private var basicBody: some View {
        SectionHeader(title: "How healthy is the business?", subtitle: "Tap a tile for a plain-English explanation")
        LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], alignment: .leading, spacing: 8) {
            ForEach(ctx.explain?.facts ?? []) { FactTile(fact: $0) }
        }
        Button { withAnimation { ctx.setShowMath(true) } } label: {
            Text("Show me the math").frame(maxWidth: .infinity)
        }
        .buttonStyle(.bordered)
        .tint(Theme.accent)
        Text("Turn on Expert Mode in Settings to always see formulas and SEC line items.")
            .font(.caption)
            .foregroundStyle(Theme.textTertiary)
            .multilineTextAlignment(.center)
            .frame(maxWidth: .infinity)
    }
}
