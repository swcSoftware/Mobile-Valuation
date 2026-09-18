import SwiftUI

/// Branded snapshot card for social sharing (1:1 or 16:9).
struct ShareCardView: View {
    enum Aspect { case square, wide }
    let report: ValuationReport
    let model: ValuationModel
    let aspect: Aspect

    var body: some View {
        let r = report.result(for: model)
        let mos = r.marginOfSafety
        ZStack {
            LinearGradient(colors: [Theme.background, Theme.surface], startPoint: .top, endPoint: .bottom)
            VStack(alignment: .leading, spacing: aspect == .square ? 40 : 28) {
                HStack {
                    HStack(spacing: 14) {
                        Circle().stroke(Theme.value, lineWidth: 8).frame(width: 56, height: 56)
                        Text("ValueLens").font(.system(size: 44, weight: .bold, design: .rounded)).foregroundStyle(Theme.textPrimary)
                    }
                    Spacer()
                    Text(model.rawValue + " · " + model.subtitle).font(.system(size: 26, weight: .medium)).foregroundStyle(Theme.textSecondary)
                }
                Spacer(minLength: 0)
                VStack(alignment: .leading, spacing: 8) {
                    Text(report.company.ticker).font(.system(size: 110, weight: .heavy, design: .rounded)).foregroundStyle(Theme.textPrimary)
                    Text(report.company.name).font(.system(size: 36)).foregroundStyle(Theme.textSecondary)
                }
                HStack(alignment: .top, spacing: 60) {
                    stat("MARKET PRICE", Fmt.money(mos.marketPrice), Theme.price)
                    stat("INTRINSIC VALUE", Fmt.money(mos.intrinsicValue), Theme.value)
                    if let m = mos.marginOfSafetyPct { stat("MARGIN OF SAFETY", (m < 0 ? "−" : "") + Fmt.pct(abs(m), decimals: 0), Theme.verdictColor(mos.verdict)) }
                }
                Text(mos.verdict.title.uppercased()).font(.system(size: 30, weight: .bold)).tracking(2).foregroundStyle(Theme.verdictColor(mos.verdict))
                Spacer(minLength: 0)
                Text(report.disclaimer).font(.system(size: 22)).foregroundStyle(Theme.textTertiary)
            }
            .padding(aspect == .square ? 80 : 70)
        }
    }

    private func stat(_ label: String, _ value: String, _ color: Color) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(label).font(.system(size: 22, weight: .semibold)).tracking(2).foregroundStyle(color.opacity(0.8))
            Text(value).font(.system(size: 64, weight: .bold, design: .rounded).monospacedDigit()).foregroundStyle(color)
        }
    }
}
