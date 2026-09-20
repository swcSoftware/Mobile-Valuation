import SwiftUI

/// One valuation metric with a tap-to-expand disclosure showing the exact formula,
/// inputs, and the SEC line items used (blueprint §7.2: deterministic & transparent).
struct MetricRow: View {
    let metric: Metric
    var emphasize: Bool = false
    /// Bump `expandVersion` with a new `expandAll` value to force every row open/closed.
    var expandAll: Bool? = nil
    var expandVersion: Int = 0
    @State private var expanded = false

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Button {
                withAnimation(.snappy) { expanded.toggle() }
            } label: {
                HStack(alignment: .firstTextBaseline) {
                    Text(metric.label).font(emphasize ? .vlHeadline : .vlBody).foregroundStyle(Theme.textPrimary)
                    Spacer()
                    Text(Fmt.metric(metric))
                        .font(emphasize ? .vlNumber : .body.monospacedDigit())
                        .foregroundStyle(metric.value == nil ? Theme.textTertiary : (metric.unit == "USD/share" ? Theme.value : Theme.textPrimary))
                    Image(systemName: "chevron.down")
                        .font(.caption2.weight(.bold))
                        .foregroundStyle(Theme.textTertiary)
                        .rotationEffect(.degrees(expanded ? 180 : 0))
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .onChange(of: expandVersion) { _, _ in if let expandAll { expanded = expandAll } }

            if expanded {
                VStack(alignment: .leading, spacing: 8) {
                    Text(metric.formula).font(.vlMono).foregroundStyle(Theme.info)
                        .fixedSize(horizontal: false, vertical: true)
                    if !metric.inputs.isEmpty {
                        VStack(alignment: .leading, spacing: 3) {
                            ForEach(metric.inputs.keys.sorted(), id: \.self) { k in
                                HStack {
                                    Text(k).font(.vlMono).foregroundStyle(Theme.textSecondary)
                                    Spacer()
                                    Text(Fmt.input(k, metric.inputs[k] ?? nil)).font(.vlMono).foregroundStyle(Theme.textPrimary)
                                }
                            }
                        }
                    }
                    if !metric.sources.isEmpty {
                        Text("SEC SOURCES").font(.caption2.weight(.semibold)).tracking(1).foregroundStyle(Theme.textTertiary)
                        ForEach(Array(metric.sources.enumerated()), id: \.offset) { _, s in
                            SourceLine(source: s)
                        }
                    }
                    ForEach(metric.notes, id: \.self) { n in
                        Text(n).font(.caption).foregroundStyle(Theme.textSecondary).fixedSize(horizontal: false, vertical: true)
                    }
                }
                .padding(12)
                .background(Theme.surfaceRaised, in: RoundedRectangle(cornerRadius: 10))
                .transition(.opacity.combined(with: .move(edge: .top)))
            }
        }
    }
}

struct SourceLine: View {
    let source: SourcedValue
    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            HStack {
                Text(source.tag).font(.vlMono).foregroundStyle(Theme.textPrimary).lineLimit(1).truncationMode(.middle)
                Spacer()
                Text(Fmt.compact(source.value)).font(.vlMono).foregroundStyle(Theme.textSecondary)
            }
            Text("\(source.form) · period ending \(source.periodEnd) · filed \(source.filed) · \(source.accession)")
                .font(.caption2).foregroundStyle(Theme.textTertiary)
            if !source.note.isEmpty {
                Text(source.note).font(.caption2).foregroundStyle(Theme.textTertiary).fixedSize(horizontal: false, vertical: true)
            }
        }
    }
}
