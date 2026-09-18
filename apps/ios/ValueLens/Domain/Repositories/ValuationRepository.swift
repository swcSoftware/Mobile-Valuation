import Foundation

/// Abstraction over the valuation engine. Remote implementation talks to FastAPI; the
/// sample implementation serves bundled JSON so the app is fully tappable offline.
protocol ValuationRepository: Sendable {
    func search(query: String) async throws -> [CompanyRef]
    func valuation(ticker: String, priceOverride: Double?, overrides: RateOverrides) async throws -> ValuationReport
    func health() async -> Bool
}

/// User-editable assumptions sent as query parameters. `nil` means "use the engine's live/default value".
struct RateOverrides: Codable, Equatable, Sendable {
    var aaaYieldPct: Double?
    var treasury10yPct: Double?
    var hurdleRatePct: Double?
    var equityRiskPremiumPct: Double?
    var beta: Double?
    var terminalGrowthPct: Double?
    var exitMultiple: Double?

    static let none = RateOverrides()

    var queryItems: [URLQueryItem] {
        var items: [URLQueryItem] = []
        func add(_ name: String, _ v: Double?) { if let v { items.append(.init(name: name, value: String(v))) } }
        add("aaa_yield_pct", aaaYieldPct)
        add("treasury_10y_pct", treasury10yPct)
        add("hurdle_rate_pct", hurdleRatePct)
        add("equity_risk_premium_pct", equityRiskPremiumPct)
        add("beta", beta)
        add("terminal_growth_pct", terminalGrowthPct)
        add("exit_multiple", exitMultiple)
        return items
    }
}

enum RepositoryError: LocalizedError {
    case unknownTicker(String)
    case server(Int, String)
    case offline
    case decoding(String)

    var errorDescription: String? {
        switch self {
        case .unknownTicker(let t): "'\(t)' is not a ticker in SEC's company list."
        case .server(let code, let msg): "Engine error \(code): \(msg)"
        case .offline: "The valuation engine is unreachable. Start it with `uvicorn valuation_engine.main:app` or use bundled sample companies."
        case .decoding(let m): "Could not read engine response: \(m)"
        }
    }
}
