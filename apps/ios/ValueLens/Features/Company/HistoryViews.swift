import SwiftUI
import Charts

/// Fundamental history only — revenue, earnings, owner earnings, ROIC. Nothing price-derived.
struct HistoryCharts: View {
    let history: [HistoryPoint]
    @State private var series: Series = .earnings

    enum Series: String, CaseIterable, Identifiable {
        case earnings = "Earnings", cash = "Cash flow", roic = "ROIC", book = "Book value"
        var id: String { rawValue }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Picker("Series", selection: $series) { ForEach(Series.allCases) { Text($0.rawValue).tag($0) } }
                .pickerStyle(.segmented)
            chart.frame(height: 180)
            legend
        }
    }

    private var points: [(String, Int, Double)] {
        history.compactMap { h in
            guard let fy = h.fiscalYear else { return nil }
            switch series {
            case .earnings: return h.netIncome.map { ("Net income", fy, $0) }
            case .cash: return h.ownerEarnings.map { ("Owner earnings", fy, $0) }
            case .roic: return h.roic.map { ("ROIC", fy, $0 * 100) }
            case .book: return h.equity.map { ("Book value", fy, $0) }
            }
        } + history.compactMap { h in
            guard let fy = h.fiscalYear else { return nil }
            switch series {
            case .earnings: return h.revenue.map { ("Revenue", fy, $0) }
            case .cash: return h.fcf.map { ("Free cash flow", fy, $0) }
            default: return nil
            }
        }
    }

    @ViewBuilder private var chart: some View {
        Chart {
            ForEach(Array(points.enumerated()), id: \.offset) { _, p in
                BarMark(x: .value("Year", String(p.1)), y: .value(p.0, p.2))
                    .foregroundStyle(by: .value("Series", p.0))
                    .position(by: .value("Series", p.0))
                    .cornerRadius(3)
            }
        }
        .chartForegroundStyleScale([
            "Revenue": Theme.textTertiary, "Net income": Theme.value,
            "Owner earnings": Theme.value, "Free cash flow": Theme.info,
            "ROIC": Theme.value, "Book value": Theme.value,
        ])
        .chartLegend(.hidden)
        .chartYAxis {
            AxisMarks(position: .leading) { v in
                AxisGridLine().foregroundStyle(Theme.border)
                AxisValueLabel {
                    if let d = v.as(Double.self) {
                        Text(series == .roic ? "\(Int(d))%" : Fmt.compact(d)).font(.caption2).foregroundStyle(Theme.textTertiary)
                    }
                }
            }
        }
        .chartXAxis {
            AxisMarks { v in
                AxisValueLabel { if let s = v.as(String.self) { Text(s.suffix(2)).font(.caption2).foregroundStyle(Theme.textTertiary) } }
            }
        }
    }

    private var legend: some View {
        HStack(spacing: 14) {
            ForEach(Array(Set(points.map(\.0))).sorted(), id: \.self) { name in
                HStack(spacing: 4) {
                    Circle().fill(name == "Revenue" ? Theme.textTertiary : (name == "Free cash flow" ? Theme.info : Theme.value)).frame(width: 8, height: 8)
                    Text(name).font(.caption2).foregroundStyle(Theme.textSecondary)
                }
            }
        }
    }
}

/// Horizontally scrolling 10-year table of the key statement lines.
struct HistoryTable: View {
    let history: [HistoryPoint]
    private let rows: [(String, KeyPath<HistoryPoint, Double?>, (Double) -> String)] = [
        ("Revenue", \.revenue, { Fmt.compact($0) }),
        ("Net income", \.netIncome, { Fmt.compact($0) }),
        ("EPS (diluted)", \.epsDiluted, { Fmt.money($0) }),
        ("Cash from ops", \.cfo, { Fmt.compact($0) }),
        ("CapEx", \.capex, { Fmt.compact($0) }),
        ("Free cash flow", \.fcf, { Fmt.compact($0) }),
        ("Owner earnings", \.ownerEarnings, { Fmt.compact($0) }),
        ("Book value", \.equity, { Fmt.compact($0) }),
        ("Book / share", \.bookValuePerShare, { Fmt.money($0) }),
        ("ROIC", \.roic, { Fmt.pct($0, isFraction: true) }),
    ]

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            Grid(alignment: .trailing, horizontalSpacing: 14, verticalSpacing: 8) {
                GridRow {
                    Text("FY").font(.caption.weight(.semibold)).foregroundStyle(Theme.textSecondary).gridColumnAlignment(.leading)
                    ForEach(history) { h in Text(String(h.fiscalYear ?? 0)).font(.caption.weight(.semibold)).foregroundStyle(Theme.textSecondary) }
                }
                Divider().overlay(Theme.border)
                ForEach(rows, id: \.0) { label, kp, fmt in
                    GridRow {
                        Text(label).font(.caption).foregroundStyle(Theme.textPrimary).gridColumnAlignment(.leading)
                        ForEach(history) { h in
                            Text(h[keyPath: kp].map(fmt) ?? "—").font(.caption.monospacedDigit()).foregroundStyle(Theme.textSecondary)
                        }
                    }
                }
            }
            .padding(14)
        }
    }
}
