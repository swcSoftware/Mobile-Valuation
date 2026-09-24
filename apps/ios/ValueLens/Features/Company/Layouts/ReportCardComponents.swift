import SwiftUI

/// Pieces of the report-card layout. Every value they show — grades, rules, phrases, chip labels —
/// arrives from the core in `ReportCardSummary`; these views decide only how it looks.

// MARK: - type

/// The report card's type roles. It uses the app's one display face (`Font.vlRounded`) like every
/// other screen: the owner chose SF Pro Rounded over the prototype's Bricolage Grotesque, globally,
/// on 2026-09-24. The report card differs from classic in size and weight, not in typeface.
enum ReportCardType {
    static func display(_ size: CGFloat, _ weight: Font.Weight = .bold) -> Font {
        .vlRounded(size, weight)
    }
    static let eyebrow = Font.caption2.weight(.bold)
}

// MARK: - tone → colour (the only mapping a layout owns)

@MainActor
enum ReportCardTone {
    static func color(_ tone: String) -> Color {
        switch tone {
        case "good": Theme.value
        case "mid": Theme.warning
        case "bad": Theme.danger
        default: Theme.textTertiary
        }
    }

    static func grade(_ letter: String?) -> Color {
        switch letter {
        case "A", "B": Theme.value
        case "C": Theme.warning
        case "D", "F": Theme.danger
        default: Theme.textTertiary
        }
    }
}

// MARK: - verdict chip

struct VerdictChipView: View {
    let chip: VerdictChip
    var body: some View {
        let tint = ReportCardTone.color(chip.tone)
        Text(chip.label.uppercased())
            .font(ReportCardType.eyebrow)
            .tracking(0.6)
            .padding(.horizontal, 9).padding(.vertical, 5)
            .background(tint.opacity(0.16), in: Capsule())
            .foregroundStyle(tint)
            .fixedSize()
    }
}

// MARK: - price against value

/// What you pay against what it's worth, on one scale. Positions are drawing, not numbers the reader
/// is told; the figures printed are the core's.
struct PriceValueBar: View {
    let price: Double?
    let value: Double?

    var body: some View {
        GeometryReader { geo in
            let span = max(price ?? 0, value ?? 0, 1) * 1.1
            let w = geo.size.width
            ZStack(alignment: .leading) {
                Capsule().fill(Theme.surfaceRaised)
                if let value {
                    Capsule().fill(Theme.value.opacity(0.35))
                        .frame(width: max(0, CGFloat(value / span) * w))
                }
                if let price {
                    RoundedRectangle(cornerRadius: 1.5)
                        .fill(Theme.price)
                        .frame(width: 3, height: 14)
                        .offset(x: min(w - 3, max(0, CGFloat(price / span) * w - 1.5)))
                }
            }
        }
        .frame(height: 8)
        .accessibilityHidden(true)   // the figures beside it carry the meaning
    }
}

// MARK: - a graded fact

struct GradeBadge: View {
    let grade: String?
    var body: some View {
        let tint = ReportCardTone.grade(grade)
        Text(grade ?? "—")
            .font(ReportCardType.display(16))
            .frame(width: 30, height: 30)
            .background(tint.opacity(grade == nil ? 0.10 : 0.16), in: RoundedRectangle(cornerRadius: 8, style: .continuous))
            .foregroundStyle(tint)
    }
}

/// The trend behind a fact, from the filed years. Fundamentals only — never price (blueprint §1).
struct Sparkline: View {
    let points: [TrendPoint]
    let tone: String

    var body: some View {
        GeometryReader { geo in
            let values = points.map(\.value)
            if let lo = values.min(), let hi = values.max(), points.count >= 2 {
                let range = max(hi - lo, .ulpOfOne)
                let step = geo.size.width / CGFloat(points.count - 1)
                let y: (Double) -> CGFloat = { v in geo.size.height - 2 - CGFloat((v - lo) / range) * (geo.size.height - 4) }
                let tint = ReportCardTone.color(tone)
                Path { path in
                    for (i, p) in points.enumerated() {
                        let pt = CGPoint(x: CGFloat(i) * step, y: y(p.value))
                        i == 0 ? path.move(to: pt) : path.addLine(to: pt)
                    }
                }
                .stroke(tint.opacity(0.75), style: StrokeStyle(lineWidth: 1.5, lineCap: .round, lineJoin: .round))
                Circle()
                    .fill(tint)
                    .frame(width: 4.5, height: 4.5)
                    .position(x: CGFloat(points.count - 1) * step, y: y(points[points.count - 1].value))
            }
        }
        .frame(width: 62, height: 22)
        .accessibilityHidden(true)
    }
}

/// One health fact: the grade against a printed rule, the value, and the company against its own
/// filed history. Tap for the plain-English reasoning.
struct GradedFactRow: View {
    let fact: GradedFact
    @State private var open = false

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Button { withAnimation(.snappy) { open.toggle() } } label: {
                // Two lines, so the label gets the full width instead of being squeezed between the
                // sparkline and the value: label · trend · value, then rule · historical read.
                HStack(alignment: .center, spacing: 10) {
                    GradeBadge(grade: fact.grade)
                    VStack(alignment: .leading, spacing: 3) {
                        HStack(alignment: .center, spacing: 8) {
                            Text(fact.label)
                                .font(.subheadline.weight(.semibold))
                                .foregroundStyle(Theme.textPrimary)
                                .lineLimit(1)
                                .minimumScaleFactor(0.85)
                            Spacer(minLength: 4)
                            if let history = fact.history {
                                Sparkline(points: history.points, tone: history.tone)
                            }
                            Text(fact.value ?? (fact.state == "missing" ? "—" : "Not graded"))
                                .font(ReportCardType.display(17))
                                .monospacedDigit()
                                .foregroundStyle(fact.value == nil ? Theme.textSecondary : Theme.textPrimary)
                                .fixedSize()
                        }
                        HStack(alignment: .firstTextBaseline, spacing: 8) {
                            Text(fact.rule)
                                .font(.caption2)
                                .foregroundStyle(Theme.textTertiary)
                            Spacer(minLength: 4)
                            Text(fact.history?.phrase ?? "No trend yet")
                                .font(.caption2)
                                .foregroundStyle(fact.history.map { ReportCardTone.color($0.tone) } ?? Theme.textTertiary)
                                .multilineTextAlignment(.trailing)
                        }
                    }
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(accessibilityText)
            .accessibilityHint(open ? "Hides the explanation" : "Shows the explanation")

            if open {
                Text(fact.why)
                    .font(.caption)
                    .foregroundStyle(Theme.textSecondary)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.top, 10)
                    .overlay(alignment: .top) { Divider().overlay(Theme.border) }
            }
        }
        .padding(12)
        .background(Theme.surface, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 12, style: .continuous).stroke(Theme.border))
    }

    private var accessibilityText: String {
        var parts = [fact.label]
        if let grade = fact.grade { parts.append("grade \(grade), \(fact.rule)") } else { parts.append(fact.rule) }
        if let value = fact.value { parts.append(value) }
        if let phrase = fact.history?.phrase { parts.append(phrase) }
        return parts.joined(separator: ". ")
    }
}

// MARK: - data checks as a strip

/// "14 of 16 data checks cleared" over one segment per check. Always shown — a clean gate is worth
/// seeing too — and it names every check that did not pass when opened.
struct CheckStripCard: View {
    let checks: [DataCheck]
    @State private var open = false

    private var flagged: [DataCheck] { checks.filter { $0.status != "pass" } }

    var body: some View {
        Card {
            VStack(alignment: .leading, spacing: 10) {
                Button { withAnimation(.snappy) { open.toggle() } } label: {
                    VStack(alignment: .leading, spacing: 9) {
                        HStack {
                            Text("\(checks.count - flagged.count) of \(checks.count) data checks cleared")
                                .font(.subheadline.weight(.semibold))
                                .foregroundStyle(Theme.textPrimary)
                            Spacer()
                            Text(flagged.isEmpty ? (open ? "Hide" : "See all") : (open ? "Hide" : "What needs a look?"))
                                .font(.caption.weight(.semibold))
                                .foregroundStyle(Theme.accent)
                        }
                        HStack(spacing: 2) {
                            ForEach(checks) { check in
                                RoundedRectangle(cornerRadius: 1.5)
                                    .fill(color(check.status))
                                    .frame(height: 5)
                            }
                        }
                    }
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel("\(checks.count - flagged.count) of \(checks.count) data checks cleared")

                if open {
                    VStack(alignment: .leading, spacing: 8) {
                        ForEach(flagged.isEmpty ? checks : flagged) { check in
                            HStack(alignment: .top, spacing: 9) {
                                Capsule().fill(color(check.status)).frame(width: 2)
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(check.label).font(.caption.weight(.semibold)).foregroundStyle(Theme.textPrimary)
                                    Text(check.message).font(.caption).foregroundStyle(Theme.textSecondary)
                                        .fixedSize(horizontal: false, vertical: true)
                                }
                            }
                        }
                    }
                }
            }
        }
        // Names every failing and warning check — the same half of the obligation `DataChecksCard`
        // carries in the classic layout.
        .places(.dataNotes)
    }

    private func color(_ status: String) -> Color {
        switch status {
        case "pass": Theme.value.opacity(0.7)
        case "warn": Theme.warning
        default: Theme.danger
        }
    }
}
