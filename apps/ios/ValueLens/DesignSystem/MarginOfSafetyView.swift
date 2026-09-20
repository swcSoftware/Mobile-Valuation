import SwiftUI

/// Price vs. intrinsic value on one horizontal scale, with the 25% / 50% buy-below bands marked.
struct MarginOfSafetyView: View {
    let mos: MarginOfSafety
    var compact = false

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .firstTextBaseline) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("MARKET PRICE").font(.caption2.weight(.semibold)).tracking(1).foregroundStyle(Theme.price)
                    Text(Fmt.money(mos.marketPrice)).font(.vlNumber).foregroundStyle(Theme.price)
                }
                Spacer()
                VStack(alignment: .trailing, spacing: 2) {
                    Text("INTRINSIC VALUE").font(.caption2.weight(.semibold)).tracking(1).foregroundStyle(Theme.value)
                    Text(Fmt.money(mos.intrinsicValue)).font(.vlNumber).foregroundStyle(Theme.value)
                }
            }
            scale
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(accessibilityDescription)
            HStack {
                Pill(text: mos.verdict.title, color: Theme.verdictColor(mos.verdict))
                Spacer()
                if let m = mos.marginOfSafetyPct {
                    Text("MoS \(m >= 0 ? "" : "−")\(Fmt.pct(abs(m), decimals: 0))")
                        .font(.caption.weight(.semibold).monospacedDigit())
                        .foregroundStyle(Theme.verdictColor(mos.verdict))
                }
            }
            if !compact {
                HStack(spacing: 16) {
                    ForEach(mos.bands) { b in
                        VStack(alignment: .leading, spacing: 1) {
                            Text("Buy below (\(Int(b.discountPct))% MoS)").font(.caption2).foregroundStyle(Theme.textTertiary)
                            Text(Fmt.money(b.buyBelow)).font(.caption.monospacedDigit().weight(.semibold)).foregroundStyle(Theme.textSecondary)
                        }
                    }
                    Spacer()
                }
            }
        }
    }

    /// Spoken summary of the gauge for VoiceOver.
    var accessibilityDescription: String {
        guard let iv = mos.intrinsicValue else { return "Intrinsic value unavailable." }
        var s = "Intrinsic value \(Fmt.money(iv))."
        if let p = mos.marketPrice { s += " Market price \(Fmt.money(p))." }
        if let m = mos.marginOfSafetyPct { s += m >= 0 ? " Margin of safety \(Int(m)) percent." : " Priced \(Int(-m)) percent above intrinsic value." }
        s += " " + mos.verdict.title + "."
        return s
    }

    private var maxScale: Double { max(mos.intrinsicValue ?? 0, mos.marketPrice ?? 0) * 1.15 }

    private func xPos(_ v: Double?, width: CGFloat) -> CGFloat {
        guard let v, maxScale > 0 else { return 0 }
        return CGFloat(v / maxScale) * width
    }

    private var scale: some View {
        GeometryReader { geo in
            let w = geo.size.width
            let x: (Double?) -> CGFloat = { self.xPos($0, width: w) }
            ZStack(alignment: .leading) {
                Capsule().fill(Theme.surfaceRaised).frame(height: 10)
                if let iv = mos.intrinsicValue {
                    // value zone: 0 → intrinsic in mint, band markers
                    Capsule().fill(Theme.value.opacity(0.35)).frame(width: x(iv), height: 10)
                    ForEach(mos.bands) { b in
                        Rectangle().fill(Theme.value.opacity(0.9)).frame(width: 2, height: 16).offset(x: x(b.buyBelow))
                    }
                    Rectangle().fill(Theme.value).frame(width: 3, height: 22).offset(x: x(iv) - 1.5)
                }
                if let p = mos.marketPrice {
                    Circle().fill(Theme.price).frame(width: 14, height: 14)
                        .overlay(Circle().stroke(Theme.background, lineWidth: 2))
                        .offset(x: x(p) - 7)
                }
            }
            .frame(height: 22)
        }
        .frame(height: 22)
    }
}
