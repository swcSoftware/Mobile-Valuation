import Foundation

enum Fmt {
    static func money(_ v: Double?, decimals: Int = 2) -> String {
        guard let v else { return "—" }
        return v.formatted(.currency(code: "USD").precision(.fractionLength(decimals)))
    }

    /// Compact currency for statement-scale numbers: $416.2B, $1.2T, $95.3M.
    static func compact(_ v: Double?) -> String {
        guard let v else { return "—" }
        let sign = v < 0 ? "−" : ""
        let a = abs(v)
        switch a {
        case 1e12...: return "\(sign)$\((a / 1e12).formatted(.number.precision(.fractionLength(2))))T"
        case 1e9...: return "\(sign)$\((a / 1e9).formatted(.number.precision(.fractionLength(1))))B"
        case 1e6...: return "\(sign)$\((a / 1e6).formatted(.number.precision(.fractionLength(1))))M"
        case 1e3...: return "\(sign)$\((a / 1e3).formatted(.number.precision(.fractionLength(1))))K"
        default: return "\(sign)$\(a.formatted(.number.precision(.fractionLength(2))))"
        }
    }

    static func pct(_ v: Double?, decimals: Int = 1, isFraction: Bool = false) -> String {
        guard let v else { return "—" }
        let x = isFraction ? v * 100 : v
        return "\(x.formatted(.number.precision(.fractionLength(decimals))))%"
    }

    static func number(_ v: Double?, decimals: Int = 2) -> String {
        guard let v else { return "—" }
        if abs(v) >= 1e6 { return compact(v).replacingOccurrences(of: "$", with: "") }
        return v.formatted(.number.precision(.fractionLength(decimals)))
    }

    /// Format a metric by its declared unit.
    static func metric(_ m: Metric) -> String {
        switch m.unit {
        case "USD/share": money(m.value)
        case "USD": compact(m.value)
        case "%": pct(m.value)
        case "x": number(m.value, decimals: m.value.map { $0 == $0.rounded() } == true ? 0 : 1) + "×"
        case "shares": number(m.value, decimals: 0)
        default: number(m.value)
        }
    }

    static func input(_ key: String, _ v: Double?) -> String {
        guard let v else { return "—" }
        if key.hasSuffix("_pct") || key == "tax_rate" && v < 1 { return key == "tax_rate" ? pct(v, isFraction: true) : pct(v) }
        if key.contains("weight") || key == "beta" { return number(v, decimals: 3) }
        if key == "years" || key.contains("shares") { return number(v, decimals: 0) }
        if abs(v) >= 1e5 { return compact(v) }
        return number(v)
    }

    static func shortDate(_ iso: String) -> String {
        String(iso.prefix(10))
    }
}
