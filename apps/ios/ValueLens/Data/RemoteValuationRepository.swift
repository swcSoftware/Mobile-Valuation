import Foundation

private struct SearchResponse: Decodable { let results: [CompanyRef] }

/// Talks to the FastAPI engine; falls back to bundled sample data for the case-study tickers
/// when the engine is unreachable so the alpha stays tappable offline.
struct RemoteValuationRepository: ValuationRepository {
    let client: APIClient
    let fallback = SampleValuationRepository()

    func search(query: String) async throws -> [CompanyRef] {
        do {
            let r: SearchResponse = try await client.get("search", query: [.init(name: "q", value: query)])
            return r.results
        } catch RepositoryError.offline {
            return try await fallback.search(query: query)
        }
    }

    func valuation(ticker: String, priceOverride: Double?, overrides: RateOverrides) async throws -> ValuationReport {
        var q = overrides.queryItems
        if let priceOverride { q.append(.init(name: "price", value: String(priceOverride))) }
        do {
            return try await client.get("companies/\(ticker)/valuation", query: q)
        } catch RepositoryError.offline {
            if SampleValuationRepository.tickers.contains(ticker.uppercased()) {
                return try await fallback.valuation(ticker: ticker, priceOverride: priceOverride, overrides: overrides)
            }
            throw RepositoryError.offline
        }
    }

    func health() async -> Bool { await healthDetails()?.status == "ok" }

    func healthDetails() async -> EngineHealth? {
        try? await client.get("health")
    }
}

/// Bundled JSON captured from the engine for AAPL, KO and MSFT.
struct SampleValuationRepository: ValuationRepository {
    static let tickers = ["AAPL", "KO", "MSFT"]

    func search(query: String) async throws -> [CompanyRef] {
        let q = query.uppercased()
        return try await withThrowingTaskGroup(of: CompanyRef.self) { group in
            for t in Self.tickers { group.addTask { try await self.valuation(ticker: t, priceOverride: nil, overrides: .none).company } }
            var out: [CompanyRef] = []
            for try await c in group where c.ticker.hasPrefix(q) || c.name.uppercased().contains(q) { out.append(c) }
            return out.sorted { $0.ticker < $1.ticker }
        }
    }

    func valuation(ticker: String, priceOverride: Double?, overrides: RateOverrides) async throws -> ValuationReport {
        guard let url = Bundle.main.url(forResource: ticker.uppercased(), withExtension: "json") else {
            throw RepositoryError.unknownTicker(ticker)
        }
        let data = try Data(contentsOf: url)
        return try JSONDecoder.engine.decode(ValuationReport.self, from: data)
    }

    func health() async -> Bool { false }
    func healthDetails() async -> EngineHealth? { nil }
}
