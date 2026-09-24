import SwiftUI

/// The four obligations from `docs/DESIGN.md`, as components rather than as markup repeated in each
/// layout. Every one of them marks itself with `.places(_:)` so `LayoutContractTests` can prove a
/// layout carried it.
///
/// They are deliberately plain: a layout styles its surroundings, but the *substance* — which
/// numbers were withheld, what was assumed, which industry rules applied — is written once here so
/// two layouts cannot drift into telling the reader different things.

// MARK: - 1. Value withheld

/// Shown when the data gate refused to publish a value, or a model produced nothing usable.
/// It has to read as a deliberate act of honesty, never as an error or an empty state.
struct WithheldValueCard: View {
    let report: ValuationReport
    let result: ModelResult

    /// Absorbed from the pre-Sprint-5 `InsufficientDataCard`, so the specific explanations survive
    /// the move into a shared component.
    private var reason: String {
        if report.checksFailed {
            let failed = report.dataChecks.filter { $0.status == "fail" }
            let names = failed.map(\.label).joined(separator: ", ")
            return "Some of the numbers pulled from SEC didn't pass verification\(failed.isEmpty ? "" : " (\(names))"), so ValueLens won't show a fair value it can't stand behind."
        }
        if report.price == nil, result.intrinsicValuePerShare != nil {
            return "No market quote was available, so the margin of safety can't be computed. Tap the price to enter one manually."
        }
        let warnings = report.warnings.joined(separator: " ").lowercased()
        if warnings.contains("missing concepts"), warnings.contains("eps") {
            return "The latest 10-K doesn't tag diluted EPS in a way the concept map recognizes yet, so the Graham formulas can't run. Owner-earnings totals are still shown."
        }
        if warnings.contains("negative") || warnings.contains("no usable") {
            return "This model produced no figure a share price could be read from — a discounted cash flow can come out negative, and a negative per-share value is not a price. Nothing is shown rather than a number we can't stand behind."
        }
        return "The filings don't carry enough of the line items this model needs. The data notes say which ones."
    }

    private var title: String {
        report.checksFailed ? "Value withheld" : "Why is the fair value missing?"
    }

    var body: some View {
        Card {
            VStack(alignment: .leading, spacing: 6) {
                Label(title, systemImage: report.checksFailed ? "hand.raised" : "questionmark.circle")
                    .font(.vlHeadline)
                    .foregroundStyle(report.checksFailed ? Theme.danger : Theme.textPrimary)
                Text(reason)
                    .font(.caption)
                    .foregroundStyle(Theme.textSecondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .places(.withheldValue)
    }
}

// MARK: - 2. Data notes and one-off flags

/// `warnings` plus, optionally, every data check that did not pass. These are the one-off
/// corrections — MCD's share-scale fix, BRK-B's derived TTM EPS, PLTR's split restatement — and a
/// reader who is not told about them is being misled by omission.
///
/// `includeChecks` is false where a layout already shows the check list separately (the classic
/// layout has `DataChecksCard`), so the reader never sees the same finding twice.
struct DataNotesSection: View {
    let report: ValuationReport
    var includeChecks: Bool = true
    var startExpanded: Bool = false
    @State private var expanded: Bool = false

    private var flagged: [DataCheck] {
        includeChecks ? report.dataChecks.filter { $0.status != "pass" } : []
    }
    private var count: Int { flagged.count + report.warnings.count }

    private var summary: String {
        guard includeChecks else { return "\(count) note\(count == 1 ? "" : "s")" }
        let passed = report.dataChecks.filter { $0.status == "pass" }.count
        return "\(passed) of \(report.dataChecks.count) checks cleared · \(count) note\(count == 1 ? "" : "s")"
    }

    var body: some View {
        // Nothing to say, so say nothing — and do not claim to have placed the component.
        if count > 0 {
            Card {
                VStack(alignment: .leading, spacing: 10) {
                    Button {
                        withAnimation(.snappy) { expanded.toggle() }
                    } label: {
                        HStack(spacing: 8) {
                            Image(systemName: "info.circle")
                                .font(.caption.weight(.bold))
                                .foregroundStyle(Theme.warning)
                            Text("Data notes").font(.vlHeadline).foregroundStyle(Theme.textPrimary)
                            Spacer()
                            Text(summary).font(.caption).foregroundStyle(Theme.textSecondary)
                            Image(systemName: "chevron.down")
                                .font(.caption2.weight(.bold))
                                .foregroundStyle(Theme.textTertiary)
                                .rotationEffect(.degrees(expanded ? 180 : 0))
                        }
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Data notes. \(summary)")

                    if expanded {
                        VStack(alignment: .leading, spacing: 8) {
                            ForEach(flagged) { check in
                                noteRow(check.label, Labels.sentence(check.message),
                                        tint: check.status == "fail" ? Theme.danger : Theme.warning)
                            }
                            ForEach(report.warnings, id: \.self) { warning in
                                noteRow(nil, Labels.sentence(warning), tint: Theme.border)
                            }
                        }
                    }
                }
            }
            .onAppear { if startExpanded { expanded = true } }
            .places(.dataNotes)
        }
    }

    private func noteRow(_ title: String?, _ body: String, tint: Color) -> some View {
        HStack(alignment: .top, spacing: 9) {
            Capsule().fill(tint).frame(width: 2)
            VStack(alignment: .leading, spacing: 2) {
                if let title {
                    Text(title).font(.caption.weight(.semibold)).foregroundStyle(Theme.textPrimary)
                }
                Text(body).font(.caption).foregroundStyle(Theme.textSecondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }
}

// MARK: - 3. Assumed vs measured

/// Beta is measured from five years of prices; cost of debt very often is not. An unlabeled input is
/// a bug (CLAUDE.md non-negotiable 2), so every layout carries this.
struct ProvenanceRow: View {
    let report: ValuationReport

    /// Provenance key → the label a reader understands, and what counts as "from the filings".
    private static let tracked: [(key: String, label: String, measured: Set<String>)] = [
        ("beta", "Beta", ["measured"]),
        ("tax_rate", "Tax rate", ["sec"]),
        ("cost_of_debt", "Cost of debt", ["sec"]),
        ("rates", "Treasury rates", ["fred"]),
    ]

    private struct Item: Identifiable {
        let id: String
        let text: String
        let measured: Bool
    }

    private var items: [Item] {
        Self.tracked.compactMap { entry in
            guard let source = report.provenance[entry.key] else { return nil }
            let measured = entry.measured.contains(source)
            let detail: String
            switch (entry.key, measured) {
            case ("beta", true): detail = "measured over 5 years"
            case ("rates", true): detail = "from FRED"
            case (_, true): detail = "from the filings"
            case (_, false): detail = "assumed"
            }
            return Item(id: entry.key, text: "\(entry.label) \(detail)", measured: measured)
        }
    }

    var body: some View {
        FlowRow(spacing: 10, rowSpacing: 4) {
            ForEach(items) { item in
                HStack(spacing: 4) {
                    Image(systemName: item.measured ? "checkmark" : "circle")
                        .font(.system(size: 8, weight: .semibold))
                    Text(item.text).font(.caption2)
                }
                .foregroundStyle(item.measured ? Theme.textTertiary : Theme.warning)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .accessibilityElement(children: .combine)
        .places(.provenance)
    }
}

// MARK: - 4. Sector mode

/// An operating company, a bank and a REIT are not valued the same way. When a company is valued by
/// anything other than the general rules, the screen says so.
struct SectorModeBadge: View {
    let sector: SectorInfo

    var body: some View {
        Label(sector.note, systemImage: icon)
            .font(.caption)
            .foregroundStyle(Theme.info)
            .fixedSize(horizontal: false, vertical: true)
            .frame(maxWidth: .infinity, alignment: .leading)
            .places(.sectorMode)
    }

    private var icon: String {
        switch sector.mode {
        case "financial": "building.columns"
        case "reit": "building.2"
        default: "shippingbox"
        }
    }
}

// MARK: - layout helper

/// Wraps its children onto as many rows as they need. Used by `ProvenanceRow`, whose labels vary in
/// length with the filer and must never be clipped.
struct FlowRow: Layout {
    var spacing: CGFloat = 8
    var rowSpacing: CGFloat = 4

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let maxWidth = proposal.width ?? .infinity
        var x: CGFloat = 0, y: CGFloat = 0, rowHeight: CGFloat = 0, widest: CGFloat = 0
        for view in subviews {
            let size = view.sizeThatFits(.unspecified)
            if x > 0, x + size.width > maxWidth {
                widest = max(widest, x - spacing)
                x = 0
                y += rowHeight + rowSpacing
                rowHeight = 0
            }
            x += size.width + spacing
            rowHeight = max(rowHeight, size.height)
        }
        widest = max(widest, x - spacing)
        return CGSize(width: min(widest, maxWidth), height: y + rowHeight)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var x = bounds.minX, y = bounds.minY, rowHeight: CGFloat = 0
        for view in subviews {
            let size = view.sizeThatFits(.unspecified)
            if x > bounds.minX, x + size.width > bounds.maxX {
                x = bounds.minX
                y += rowHeight + rowSpacing
                rowHeight = 0
            }
            view.place(at: CGPoint(x: x, y: y), proposal: ProposedViewSize(size))
            x += size.width + spacing
            rowHeight = max(rowHeight, size.height)
        }
    }
}
