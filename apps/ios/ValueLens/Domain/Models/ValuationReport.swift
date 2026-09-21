import Foundation

// MARK: - Codable mirrors of the valuation-engine JSON contract (docs/API.md)

struct CompanyRef: Codable, Hashable, Identifiable {
    let ticker: String
    let cik: Int
    let name: String
    var id: String { ticker }
}

struct Quote: Codable, Hashable {
    let ticker: String
    let price: Double
    let currency: String
    let asOf: String
    let source: String
}

struct Assumptions: Codable, Hashable {
    let aaaYieldPct: Double
    let treasury10YPct: Double  // JSON: treasury_10y_pct (snake-case strategy capitalizes the Y)
    let hurdleRatePct: Double
    let equityRiskPremiumPct: Double
    let beta: Double
    let terminalGrowthPct: Double
    let exitMultiple: Double
    let taxRatePct: Double
    let projectionYears: Int
    let maxGrowthPct: Double
    let mosBandsPct: [Double]
    let rateSource: String

}

/// A number traced back to the SEC line item (tag + accession + period) it came from.
struct SourcedValue: Codable, Hashable {
    let value: Double
    let tag: String
    let accession: String
    let form: String
    let periodEnd: String
    let periodStart: String?
    let filed: String
    let derived: Bool
    let note: String
}

/// Every model output: value + the exact formula + inputs + SEC sources.
struct Metric: Codable, Hashable, Identifiable {
    let key: String
    let label: String
    let value: Double?
    let unit: String
    let formula: String
    let inputs: [String: Double?]
    let sources: [SourcedValue]
    let notes: [String]
    var id: String { key }

    enum CodingKeys: String, CodingKey { case key, label, value, unit, formula, inputs, sources, notes }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        key = try c.decode(String.self, forKey: .key)
        label = try c.decode(String.self, forKey: .label)
        value = try c.decodeIfPresent(Double.self, forKey: .value)
        unit = try c.decode(String.self, forKey: .unit)
        formula = try c.decode(String.self, forKey: .formula)
        inputs = try c.decodeIfPresent([String: Double?].self, forKey: .inputs) ?? [:]
        sources = try c.decodeIfPresent([SourcedValue].self, forKey: .sources) ?? []
        notes = try c.decodeIfPresent([String].self, forKey: .notes) ?? []
    }

    init(key: String, label: String, value: Double?, unit: String, formula: String,
         inputs: [String: Double?] = [:], sources: [SourcedValue] = [], notes: [String] = []) {
        self.key = key; self.label = label; self.value = value; self.unit = unit
        self.formula = formula; self.inputs = inputs; self.sources = sources; self.notes = notes
    }
}

struct MoSBand: Codable, Hashable, Identifiable {
    let discountPct: Double
    let buyBelow: Double?
    var id: Double { discountPct }
}

enum Verdict: String, Codable {
    case deepValue = "deep_value"
    case withinMargin = "within_margin"
    case thinMargin = "below_intrinsic_thin_margin"
    case aboveIntrinsic = "above_intrinsic"
    case insufficientData = "insufficient_data"

    var title: String {
        switch self {
        case .deepValue: "Deep value"
        case .withinMargin: "Within margin of safety"
        case .thinMargin: "Below intrinsic, thin margin"
        case .aboveIntrinsic: "Priced above intrinsic value"
        case .insufficientData: "Insufficient data"
        }
    }
}

struct MarginOfSafety: Codable, Hashable {
    let intrinsicValue: Double?
    let marketPrice: Double?
    let marginOfSafetyPct: Double?
    let bands: [MoSBand]
    let formula: String
    let verdict: Verdict
}

struct ModelResult: Codable, Hashable {
    let name: String
    let intrinsicValuePerShare: Double?
    let composite: Metric
    let metrics: [Metric]
    let marginOfSafety: MarginOfSafety

    func metric(_ key: String) -> Metric? { metrics.first { $0.key == key } }
}

struct HistoryPoint: Codable, Hashable, Identifiable {
    let fiscalYear: Int?
    let periodEnd: String
    let revenue: Double?
    let netIncome: Double?
    let epsDiluted: Double?
    let fcf: Double?
    let ownerEarnings: Double?
    let equity: Double?
    let roic: Double?
    let bookValuePerShare: Double?
    let cfo: Double?
    let capex: Double?
    var id: String { periodEnd }
}

struct GrowthEntry: Codable, Hashable {
    let fullPeriodYears: Int?
    let fullPeriodCagr: Double?
    let fiveYearCagr: Double?
}

struct SectorInfo: Codable, Hashable {
    let sic: String?
    let sicDescription: String?
    let mode: String        // general | financial | reit
    let note: String
}

struct DataCheck: Codable, Hashable, Identifiable {
    let key: String
    let label: String
    let status: String   // pass | warn | fail
    let message: String
    let inputs: [String]
    var id: String { key }
}

struct ValuationReport: Codable, Hashable {
    let company: CompanyRef
    let quote: Quote?
    let assumptions: Assumptions
    let snapshot: [String: SourcedValue]
    let history: [HistoryPoint]
    let growth: [String: GrowthEntry]
    let modelA: ModelResult
    let modelB: ModelResult
    let warnings: [String]
    let disclaimer: String
    let generatedAt: String
    var dataChecks: [DataCheck] = []
    var provenance: [String: String] = [:]
    var sector: SectorInfo? = nil
    /// The exact JSON the core produced (not part of Codable); handed back to the core for `explain`.
    var rawJSON: String? = nil

    var price: Double? { quote?.price }
    var checksFailed: Bool { dataChecks.contains { $0.status == "fail" } }

    enum CodingKeys: String, CodingKey { case company, quote, assumptions, snapshot, history, growth, modelA, modelB, warnings, disclaimer, generatedAt, dataChecks, provenance, sector }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        company = try c.decode(CompanyRef.self, forKey: .company)
        quote = try c.decodeIfPresent(Quote.self, forKey: .quote)
        assumptions = try c.decode(Assumptions.self, forKey: .assumptions)
        snapshot = try c.decode([String: SourcedValue].self, forKey: .snapshot)
        history = try c.decode([HistoryPoint].self, forKey: .history)
        growth = try c.decode([String: GrowthEntry].self, forKey: .growth)
        modelA = try c.decode(ModelResult.self, forKey: .modelA)
        modelB = try c.decode(ModelResult.self, forKey: .modelB)
        warnings = try c.decode([String].self, forKey: .warnings)
        disclaimer = try c.decode(String.self, forKey: .disclaimer)
        generatedAt = try c.decode(String.self, forKey: .generatedAt)
        dataChecks = try c.decodeIfPresent([DataCheck].self, forKey: .dataChecks) ?? []
        provenance = try c.decodeIfPresent([String: String].self, forKey: .provenance) ?? [:]
        sector = try c.decodeIfPresent(SectorInfo.self, forKey: .sector)
    }
}

enum ValuationModel: String, CaseIterable, Identifiable {
    case traditional = "Model A"
    case modern = "Model B"
    var id: String { rawValue }
    var subtitle: String {
        switch self {
        case .traditional: "Graham · Buffett · Munger"
        case .modern: "DCF · WACC · ROIC"
        }
    }
    /// Basic-mode names (Explain.modelName in the core).
    var friendlyName: String { self == .traditional ? "Classic value" : "Cash-flow value" }
    var friendlyBlurb: String {
        self == .traditional
            ? "The classic approach: what the business earns for its owners, priced the way Graham and Buffett would."
            : "The modern approach: project the cash the business will generate and discount it back to today."
    }
}

extension ValuationReport {
    func result(for model: ValuationModel) -> ModelResult {
        model == .traditional ? modelA : modelB
    }
}

/// JSONDecoder/Encoder configured for the core's snake_case payloads.
extension JSONDecoder {
    static let engine: JSONDecoder = {
        let d = JSONDecoder()
        d.keyDecodingStrategy = .convertFromSnakeCase
        return d
    }()
}
extension JSONEncoder {
    static let engine: JSONEncoder = {
        let e = JSONEncoder()
        e.keyEncodingStrategy = .convertToSnakeCase
        return e
    }()
}
