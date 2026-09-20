import SwiftUI

/// Data-verification gate results. Basic: one line; expanded: the full list.
struct DataChecksCard: View {
    let checks: [DataCheck]
    let summary: String
    @State private var open: Bool
    init(checks: [DataCheck], summary: String, expanded: Bool) { self.checks = checks; self.summary = summary; _open = State(initialValue: expanded) }

    private var worst: Color {
        if checks.contains(where: { $0.status == "fail" }) { return Theme.danger }
        if checks.contains(where: { $0.status == "warn" }) { return Theme.warning }
        return Theme.value
    }

    var body: some View {
        Card {
            Button { withAnimation(.snappy) { open.toggle() } } label: {
                VStack(alignment: .leading, spacing: 4) {
                    HStack {
                        Circle().fill(worst).frame(width: 10, height: 10)
                        Text("Data checks").font(.vlHeadline).foregroundStyle(Theme.textPrimary)
                        Spacer()
                        Image(systemName: "chevron.down").font(.caption2.weight(.bold)).foregroundStyle(Theme.textTertiary).rotationEffect(.degrees(open ? 180 : 0))
                    }
                    Text(summary).font(.caption).foregroundStyle(Theme.textSecondary)
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Data checks. \(summary)")
            if open {
                VStack(alignment: .leading, spacing: 8) {
                    ForEach(checks) { c in
                        HStack(alignment: .top, spacing: 8) {
                            Image(systemName: c.status == "pass" ? "checkmark" : (c.status == "warn" ? "exclamationmark" : "xmark"))
                                .font(.caption.weight(.bold))
                                .foregroundStyle(c.status == "pass" ? Theme.value : (c.status == "warn" ? Theme.warning : Theme.danger))
                                .frame(width: 16)
                            VStack(alignment: .leading, spacing: 2) {
                                Text(c.label).font(.subheadline).foregroundStyle(Theme.textPrimary)
                                Text(c.message).font(.caption).foregroundStyle(Theme.textSecondary).fixedSize(horizontal: false, vertical: true)
                            }
                        }
                    }
                }
                .padding(.top, 10)
            }
        }
    }
}

/// A value with a tap-to-reveal plain-English explanation (basic mode's ⓘ).
struct FactTile: View {
    let fact: ExplainSummary.Fact
    @State private var open = false
    private var color: Color { fact.tone == "good" ? Theme.value : (fact.tone == "bad" ? Theme.danger : Theme.textPrimary) }
    var body: some View {
        Button { withAnimation(.snappy) { open.toggle() } } label: {
            VStack(alignment: .leading, spacing: 4) {
                HStack { Text(fact.label).font(.caption).foregroundStyle(Theme.textSecondary); Spacer(); Image(systemName: "info.circle").font(.caption2).foregroundStyle(Theme.textTertiary) }
                Text(fact.value).font(.subheadline.weight(.semibold)).foregroundStyle(color).fixedSize(horizontal: false, vertical: true)
                if open { Text(fact.plain).font(.caption).foregroundStyle(Theme.textSecondary).fixedSize(horizontal: false, vertical: true).padding(.top, 4) }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(12)
            .background(Theme.surfaceRaised, in: RoundedRectangle(cornerRadius: 12))
        }
        .buttonStyle(.plain)
        .accessibilityLabel("\(fact.label): \(fact.value). \(fact.plain)")
    }
}
