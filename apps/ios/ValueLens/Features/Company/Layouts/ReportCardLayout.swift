import SwiftUI

/// Sprint 5's second presentation: the decision first, then the business behind it.
///
/// **Track A ships the structure; Track C ships the design.** The arrangement here is the one the
/// owner signed off on the prototype — price against value at the top, health below, notes and
/// provenance under that — but the health facts are still the core's current plain-language facts.
/// They become graded facts with a printed threshold once `Explain.Fact` carries `grade` and `rule`
/// (Track C + F). Nothing here computes a grade locally; that would break non-negotiable 7.
struct ReportCardLayout: View {
    let ctx: LayoutContext

    private var report: ValuationReport { ctx.report }
    private var result: ModelResult { ctx.result }
    private var mos: MarginOfSafety { result.marginOfSafety }

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            heading

            if let sector = report.sector, sector.mode != "general" {
                SectorModeBadge(sector: sector)
            }

            if report.checksFailed || mos.verdict == .insufficientData {
                WithheldValueCard(report: report, result: result)
            } else {
                verdictCard
            }

            ModelToggle(model: ctx.model,
                        expert: ctx.expert,
                        blurbOverride: ctx.expert ? nil : (ctx.model.wrappedValue == .traditional ? ctx.explain?.blurbA : ctx.explain?.blurbB))

            if !ctx.expert {
                SectionHeader(title: "Business health", subtitle: "Tap a fact for a plain-English explanation")
                VStack(spacing: 8) {
                    ForEach(ctx.explain?.facts ?? []) { FactTile(fact: $0) }
                }
            }

            DataChecksCard(checks: report.dataChecks,
                           summary: ctx.explain?.checksSummary ?? "\(report.dataChecks.count) checks run",
                           expanded: ctx.expert)

            if let coverage = report.coverage, !coverage.gaps.isEmpty {
                CoverageGapCard(coverage: coverage, expert: ctx.expert, onReport: ctx.reportIssue)
            }

            DataNotesSection(report: report, includeChecks: false)

            if ctx.expert {
                ExpertBody(ctx: ctx)
            } else {
                Button { withAnimation { ctx.setShowMath(true) } } label: {
                    Text("Show me the math").frame(maxWidth: .infinity)
                }
                .buttonStyle(.bordered)
                .tint(Theme.value)
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

    private var heading: some View {
        VStack(alignment: .leading, spacing: 3) {
            Text(report.company.name)
                .font(.vlTitle)
                .foregroundStyle(Theme.textPrimary)
                .fixedSize(horizontal: false, vertical: true)
            Text(report.company.ticker + (report.quote.map { " · priced \(Fmt.shortDate($0.asOf))" } ?? " · no quote"))
                .font(.caption)
                .foregroundStyle(Theme.textSecondary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    /// Price against value, which is what the reader came for.
    private var verdictCard: some View {
        Card {
            VStack(alignment: .leading, spacing: 12) {
                if !ctx.expert, let explain = ctx.explain {
                    Text(ctx.model.wrappedValue == .traditional ? explain.verdictA : explain.verdictB)
                        .font(.vlBody)
                        .foregroundStyle(Theme.textPrimary)
                        .fixedSize(horizontal: false, vertical: true)
                }
                HStack(alignment: .top, spacing: 22) {
                    figure("You pay", Fmt.money(mos.marketPrice), Theme.price, action: ctx.editPrice)
                    figure("It's worth", Fmt.money(mos.intrinsicValue), Theme.value, action: nil)
                }
                MarginOfSafetyView(mos: mos, compact: !ctx.expert)
            }
        }
    }

    @ViewBuilder
    private func figure(_ label: String, _ value: String, _ tint: Color, action: (() -> Void)?) -> some View {
        let content = VStack(alignment: .leading, spacing: 2) {
            Text(label.uppercased())
                .font(.caption2.weight(.semibold))
                .tracking(0.8)
                .foregroundStyle(tint)
            Text(value)
                .font(.vlNumber)
                .foregroundStyle(tint)
        }
        if let action {
            Button(action: action) { content }
                .buttonStyle(.plain)
                .accessibilityLabel("\(label) \(value). Tap to override.")
        } else {
            content
        }
    }
}
