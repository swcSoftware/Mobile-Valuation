import SwiftUI

/// Sprint 5's second presentation: the decision first, then the business behind it, graded.
///
/// Built to the design the owner signed off on a phone (share-site r4, docs/DESIGN.md). Every grade,
/// rule, historical read and chip label comes from the core's `ReportCardSummary`; this view decides
/// only how they look (CLAUDE.md non-negotiable 7). It must render sensibly before the core has
/// answered, so everything that depends on `ctx.reportCard` has a quiet placeholder.
struct ReportCardLayout: View {
    let ctx: LayoutContext

    private var report: ValuationReport { ctx.report }
    private var result: ModelResult { ctx.result }
    private var mos: MarginOfSafety { result.marginOfSafety }
    private var card: ReportCardSummary? { ctx.reportCard }
    private var model: ValuationModel { ctx.model.wrappedValue }

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            heading

            if let sector = report.sector, sector.mode != "general" {
                SectorModeBadge(sector: sector)
            }

            if report.checksFailed {
                WithheldValueCard(report: report, result: result)
            } else {
                summaryCard
                if mos.verdict == .insufficientData {
                    WithheldValueCard(report: report, result: result)
                }
            }

            ModelToggle(model: ctx.model,
                        expert: ctx.expert,
                        blurbOverride: ctx.expert ? nil : (model == .traditional ? ctx.explain?.blurbA : ctx.explain?.blurbB))

            if !ctx.expert {
                health
            }

            CheckStripCard(checks: report.dataChecks)

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
                .tint(Theme.accent)
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

    // MARK: heading

    private var heading: some View {
        HStack(alignment: .top, spacing: 10) {
            VStack(alignment: .leading, spacing: 3) {
                Text(report.company.displayName)
                    .font(ReportCardType.display(26))
                    .foregroundStyle(Theme.textPrimary)
                    .fixedSize(horizontal: false, vertical: true)
                Text(subtitle)
                    .font(.caption)
                    .foregroundStyle(Theme.textSecondary)
            }
            Spacer(minLength: 0)
            if let chip = card?.chip(for: model) {
                VerdictChipView(chip: chip)
            }
        }
    }

    private var subtitle: String {
        var parts = [report.company.ticker]
        if let mode = card?.modeLabel { parts.append(mode) }
        parts.append(report.quote.map { "priced \(Fmt.shortDate($0.asOf))" } ?? "no quote")
        return parts.joined(separator: " · ")
    }

    // MARK: price against value — what the reader came for

    private var summaryCard: some View {
        Card {
            VStack(alignment: .leading, spacing: 0) {
                Text(model == .traditional ? "EARNINGS POWER" : "DISCOUNTED CASH FLOW")
                    .font(ReportCardType.eyebrow).tracking(1)
                    .foregroundStyle(Theme.textTertiary)

                if let explain = ctx.explain {
                    Text(model == .traditional ? explain.verdictA : explain.verdictB)
                        // Sentence-length copy reads badly at condensed width; the display face is
                        // for the name and the two figures, not for paragraphs.
                        .font(.system(size: 18, weight: .medium))
                        .foregroundStyle(Theme.textPrimary)
                        .fixedSize(horizontal: false, vertical: true)
                        .padding(.top, 8)
                }

                HStack(alignment: .top, spacing: 24) {
                    figure("You pay", Fmt.money(mos.marketPrice), Theme.price, action: ctx.editPrice)
                    figure("It's worth", mos.intrinsicValue.map { Fmt.money($0) } ?? "Withheld",
                           mos.intrinsicValue == nil ? Theme.textTertiary : Theme.value, action: nil)
                }
                .padding(.top, 16)

                PriceValueBar(price: mos.marketPrice, value: mos.intrinsicValue)
                    .padding(.top, 14)

                HStack(alignment: .firstTextBaseline, spacing: 7) {
                    Text(marginText)
                        .font(.subheadline.weight(.bold)).monospacedDigit()
                        .foregroundStyle(ReportCardTone.color(card?.chip(for: model).tone ?? "none"))
                    Text(marginCaption)
                        .font(.caption)
                        .foregroundStyle(Theme.textSecondary)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .padding(.top, 10)
            }
        }
    }

    private var marginText: String {
        guard let pct = mos.marginOfSafetyPct else { return "No figure" }
        return (pct > 0 ? "+" : pct < 0 ? "−" : "") + "\(Int(abs(pct).rounded()))%"
    }

    private var marginCaption: String {
        guard mos.intrinsicValue != nil else { return "see the notes below" }
        if let band = mos.bands.first(where: { $0.discountPct == 25 }), let buyBelow = band.buyBelow {
            return "margin today · Graham's 25% discount would be \(Fmt.money(buyBelow))"
        }
        return "margin today"
    }

    @ViewBuilder
    private func figure(_ label: String, _ value: String, _ tint: Color, action: (() -> Void)?) -> some View {
        let content = VStack(alignment: .leading, spacing: 2) {
            Text(label.uppercased()).font(ReportCardType.eyebrow).tracking(0.8).foregroundStyle(tint)
            Text(value).font(ReportCardType.display(28)).monospacedDigit().foregroundStyle(tint)
        }
        if let action {
            Button(action: action) { content }
                .buttonStyle(.plain)
                .accessibilityLabel("\(label) \(value). Tap to override the market price.")
        } else {
            content
        }
    }

    // MARK: business health, graded

    @ViewBuilder
    private var health: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .center) {
                Text("BUSINESS HEALTH, GRADED")
                    .font(ReportCardType.eyebrow).tracking(1)
                    .foregroundStyle(Theme.textTertiary)
                Spacer()
                if let card {
                    // The lens is shown on the screen it affects — a grade that silently depends on
                    // a setting buried in Settings is exactly a "silent number".
                    Button(action: ctx.swapLens) {
                        Label("\(card.lensName) lens", systemImage: "arrow.left.arrow.right")
                            .font(.caption.weight(.semibold))
                            .padding(.horizontal, 10).padding(.vertical, 6)
                            .background(Theme.surfaceRaised, in: Capsule())
                            .foregroundStyle(Theme.textSecondary)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("\(card.lensName) lens. Switch lens.")
                }
            }

            if let card {
                Text("\(card.lensBlurb) Thresholds are shown on every row; the valuation above is the same whichever lens you pick.")
                    .font(.caption)
                    .foregroundStyle(Theme.textTertiary)
                    .fixedSize(horizontal: false, vertical: true)

                if let blank = card.blankNote {
                    Card {
                        VStack(alignment: .leading, spacing: 4) {
                            Text("These four facts don't fit this filer")
                                .font(.subheadline.weight(.semibold)).foregroundStyle(Theme.textPrimary)
                            Text(blank).font(.caption).foregroundStyle(Theme.textSecondary)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                    }
                } else {
                    ForEach(card.facts) { GradedFactRow(fact: $0) }
                }
            } else {
                // The core has not answered yet. A quiet placeholder, not a spinner shouting over
                // the valuation above it.
                ForEach(0..<4, id: \.self) { _ in
                    RoundedRectangle(cornerRadius: 12).fill(Theme.surface).frame(height: 56)
                }
                .redacted(reason: .placeholder)
            }
        }
    }
}
