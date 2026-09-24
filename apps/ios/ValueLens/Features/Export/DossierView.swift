import SwiftUI

/// Print-ready PDF dossier body (rendered on a white page by Exporter).
struct DossierView: View {
    let report: ValuationReport
    let model: ValuationModel

    var body: some View {
        let r = report.result(for: model)
        VStack(alignment: .leading, spacing: 14) {
            HStack(alignment: .firstTextBaseline) {
                VStack(alignment: .leading) {
                    Text("Alpha Valuation Dossier").font(.title2.bold())
                    Text("\(report.company.displayName) (\(report.company.ticker)) · CIK " + String(report.company.cik)).font(.subheadline)
                    if report.company.displayName != report.company.name {
                        Text("Filed with the SEC as \(report.company.name)").font(.caption)
                    }
                }
                Spacer()
                Text(Fmt.shortDate(report.generatedAt)).font(.caption)
            }
            Divider()

            block("Executive summary") {
                Text("Market price \(Fmt.money(report.price)) vs. \(model.rawValue) intrinsic value \(Fmt.money(r.intrinsicValuePerShare)). Verdict: \(r.marginOfSafety.verdict.title).")
                if let m = r.marginOfSafety.marginOfSafetyPct { Text("Margin of safety: \(Fmt.pct(m, decimals: 1)). Buy-below levels: " + r.marginOfSafety.bands.map { "\(Int($0.discountPct))% → \(Fmt.money($0.buyBelow))" }.joined(separator: ", ")) }
                Text("Model: \(r.name)").font(.caption)
            }

            block("Valuation metrics") {
                ForEach(r.metrics.filter { $0.key != "fcff_projection" }) { m in
                    VStack(alignment: .leading, spacing: 1) {
                        HStack { Text(m.label); Spacer(); Text(Fmt.metric(m)).monospacedDigit() }
                        Text(Labels.formula(m.formula)).font(.system(size: 8, design: .monospaced)).foregroundStyle(.secondary)
                    }
                }
            }

            block("Key balance sheet ratios (TTM)") {
                ratio("Book value", "equity", Fmt.compact); ratio("Cash", "cash", Fmt.compact); ratio("Total debt", "total_debt", Fmt.compact)
                ratio("Current ratio", "current_ratio") { Fmt.number($0) + "×" }
                ratio("Debt / equity", "debt_to_equity") { Fmt.number($0) + "×" }
                ratio("ROIC", "roic") { Fmt.pct($0, isFraction: true) }
                ratio("ROE", "roe") { Fmt.pct($0, isFraction: true) }
            }

            block("Historical owner earnings") {
                ForEach(report.history) { h in
                    HStack { Text("FY" + String(h.fiscalYear ?? 0)); Spacer(); Text(Fmt.compact(h.ownerEarnings)).monospacedDigit(); Text("NI \(Fmt.compact(h.netIncome))").foregroundStyle(.secondary).frame(width: 110, alignment: .trailing) }
                }
            }

            block("Assumptions") {
                let a = report.assumptions
                Text("AAA yield \(Fmt.pct(a.aaaYieldPct, decimals: 2)) · 10-yr Treasury \(Fmt.pct(a.treasury10YPct, decimals: 2)) · hurdle \(Fmt.pct(a.hurdleRatePct)) · ERP \(Fmt.pct(a.equityRiskPremiumPct)) · β \(Fmt.number(a.beta)) · terminal g \(Fmt.pct(a.terminalGrowthPct)) · exit \(Fmt.number(a.exitMultiple, decimals: 0))× · source: \(a.rateSource)")
            }
            Divider()
            Text(report.disclaimer).font(.caption2).foregroundStyle(.secondary)
        }
        .font(.system(size: 10))
        .padding(36)
        .foregroundStyle(.black)
    }

    private func block<C: View>(_ title: String, @ViewBuilder _ c: () -> C) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title.uppercased()).font(.system(size: 9, weight: .bold)).tracking(1).foregroundStyle(.secondary)
            c()
        }
    }

    private func ratio(_ label: String, _ key: String, _ fmt: @escaping (Double) -> String) -> some View {
        HStack { Text(label); Spacer(); Text(report.snapshot[key].map { fmt($0.value) } ?? "—").monospacedDigit() }
    }
}
