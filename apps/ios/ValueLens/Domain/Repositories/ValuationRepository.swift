import Foundation

/// Abstraction over the valuation engine. Remote implementation talks to FastAPI; the
/// sample implementation serves bundled JSON so the app is fully tappable offline.
/// Abstraction over the on-device valuation core. The sample implementation serves bundled JSON
/// so the app stays tappable offline.
protocol ValuationRepository: Sendable {
    func search(query: String) async throws -> [CompanyRef]
    func valuation(ticker: String, priceOverride: Double?, overrides: RateOverrides) async throws -> ValuationReport
    func rates() async -> RatesInfo?
    func explain(_ report: ValuationReport) async -> ExplainSummary?
    /// Grades, rules, historical reads and verdict chips for the report-card layout, for one
    /// investor lens. Every judgement comes from the core (Grading.kt); the layout only draws it.
    func reportCard(_ report: ValuationReport, lens: InvestorLens) async -> ReportCardSummary?
    func glossary() -> [GlossaryEntry]
    /// Prefilled GitHub issue URL for a concept-map gap report. No token, no server — the user
    /// reviews the text in their browser and decides whether to submit it.
    func coverageIssueURL(for report: ValuationReport) -> URL?
}

/// Published FRED snapshot (rates.json on GitHub Pages, or the bundled copy).
struct RatesInfo: Codable, Sendable {
    let asOf: String
    let aaaYieldPct: Double
    let treasury10YPct: Double
    let source: String?
    enum CodingKeys: String, CodingKey { case asOf = "as_of", aaaYieldPct = "aaa_yield_pct", treasury10YPct = "treasury_10y_pct", source }
}

/// Plain-language layer computed by the core (Explain.kt).
struct ExplainSummary: Codable, Sendable {
    struct Fact: Codable, Sendable, Identifiable { let label: String; let value: String; let tone: String; let plain: String; var id: String { label } }
    let verdictA: String
    let verdictB: String
    let facts: [Fact]
    let checksSummary: String
    var blurbA: String? = nil
    var blurbB: String? = nil
    var sectorNote: String? = nil
}

struct GlossaryEntry: Codable, Sendable, Identifiable {
    let key: String; let term: String; let plain: String; let expert: String
    var id: String { key }
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

/// Mirrors the engine's error taxonomy (docs/API.md). Each case carries a user-facing message.
enum RepositoryError: LocalizedError, Equatable {
    case unknownTicker(String)
    case noAnnualData(String)
    case rateLimited(String)
    case invalidIdentity(String)
    case upstreamUnavailable(String)
    case server(Int, String)
    case offline
    case decoding(String)

    /// Engine wire format: {"error": {"code", "message", "detail"}, "detail": "…"}
    struct Envelope: Decodable {
        struct Body: Decodable { let code: String; let message: String }
        let error: Body?
        let detail: String?
    }

    static func from(status: Int, body: Data) -> RepositoryError {
        let env = try? JSONDecoder().decode(Envelope.self, from: body)
        let message = env?.error?.message ?? env?.detail ?? String(data: body, encoding: .utf8) ?? ""
        switch env?.error?.code {
        case "unknown_ticker": return .unknownTicker(message)
        case "no_annual_data": return .noAnnualData(message)
        case "rate_limited": return .rateLimited(message)
        case "invalid_identity": return .invalidIdentity(message)
        case "upstream_unavailable": return .upstreamUnavailable(message)
        default: return status == 404 ? .unknownTicker(message) : .server(status, message)
        }
    }

    var errorDescription: String? {
        switch self {
        case .unknownTicker(let m): m.isEmpty ? "Not a ticker in SEC's company list." : m
        case .noAnnualData(let m): m.isEmpty ? "No 10-K data on EDGAR for this company." : m
        case .rateLimited(let m): m.isEmpty ? "Too many requests. Try again in a minute." : m
        case .invalidIdentity(let m): m.isEmpty ? "Set your name and email in Settings so SEC EDGAR can identify you." : m
        case .upstreamUnavailable(let m): m.isEmpty ? "SEC EDGAR or a data provider is unavailable right now." : m
        case .server(let code, let msg): "Engine error \(code): \(msg)"
        case .offline: "The valuation engine is unreachable. Check the engine URL in Settings, or open one of the bundled sample companies."
        case .decoding(let m): "Could not read engine response: \(m)"
        }
    }

    /// Short title for empty/error states.
    var title: String {
        switch self {
        case .unknownTicker: "Unknown ticker"
        case .noAnnualData: "No annual filings"
        case .rateLimited: "Slow down"
        case .invalidIdentity: "Identity needed"
        case .upstreamUnavailable: "Data source unavailable"
        case .server: "Engine error"
        case .offline: "Engine offline"
        case .decoding: "Unexpected response"
        }
    }
}

// MARK: - report card (Sprint 5 Track C) — mirrors Grading.kt

/// Everything on the report card that is a judgement, produced by the core for one lens.
struct ReportCardSummary: Codable, Sendable, Equatable {
    let lens: String
    let lensName: String
    let lensBlurb: String
    /// "Operating company" / "Bank / insurer" / "REIT".
    let modeLabel: String
    /// Already in the lens's reading order.
    let facts: [GradedFact]
    let gradedCount: Int
    /// Present only when nothing could be graded; replaces the section rather than four dashes.
    let blankNote: String?
    let chipA: VerdictChip
    let chipB: VerdictChip
    let rulesVersion: String

    func chip(for model: ValuationModel) -> VerdictChip { model == .traditional ? chipA : chipB }
}

struct GradedFact: Codable, Sendable, Equatable, Identifiable {
    /// profit / debt / conversion / growth
    let slot: String
    let label: String
    /// Formatted with its unit, or nil when there is nothing to show — never "—× equity".
    let value: String?
    /// A–F, or nil when refused, ungradable or missing.
    let grade: String?
    /// The threshold, printed beside the grade so it can be checked; or why there is none.
    let rule: String
    /// graded / refused / ungradable / missing
    let state: String
    let why: String
    let history: HistoryRead?
    var id: String { slot }
}

struct HistoryRead: Codable, Sendable, Equatable {
    let phrase: String
    /// good / bad
    let tone: String
    let points: [TrendPoint]
    let min: Double
    let max: Double
    let average: Double
    let firstYear: Int
    let lastYear: Int
}

struct TrendPoint: Codable, Sendable, Equatable {
    let year: Int
    let value: Double
}

struct VerdictChip: Codable, Sendable, Equatable {
    let label: String
    /// good / mid / bad / none
    let tone: String
}

