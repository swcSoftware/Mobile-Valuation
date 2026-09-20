import Foundation
import ValuationCore
import CryptoKit

// MARK: - Kotlin port adapters

/// Blocking HTTP GET for the core (called off the main thread by the repository).
final class URLSessionFetcher: NSObject, Fetcher {
    func get(url: String, headers: [String: String]) -> FetchResult {
        guard let u = URL(string: url) else { return FetchResult(status: 0, body: nil) }
        var req = URLRequest(url: u)
        req.timeoutInterval = 60
        headers.forEach { req.setValue($1, forHTTPHeaderField: $0) }
        let sem = DispatchSemaphore(value: 0)
        var result = FetchResult(status: 0, body: nil)
        URLSession.shared.dataTask(with: req) { data, resp, _ in
            if let http = resp as? HTTPURLResponse {
                result = FetchResult(status: Int32(http.statusCode), body: (200..<300).contains(http.statusCode) ? String(data: data ?? Data(), encoding: .utf8) : nil)
            }
            sem.signal()
        }.resume()
        sem.wait()
        return result
    }
}

/// One file per key under Caches/valuelens-core.
final class FileKVCache: NSObject, KeyValueCache {
    private let dir: URL
    override init() {
        dir = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0].appending(path: "valuelens-core")
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
    }
    private func file(_ key: String) -> URL {
        let digest = Insecure.SHA1.hash(data: Data(key.utf8)).map { String(format: "%02x", $0) }.joined()
        return dir.appending(path: digest)
    }
    func get(key: String) -> String? { try? String(contentsOf: file(key), encoding: .utf8) }
    func put(key: String, value: String) { try? value.write(to: file(key), atomically: true, encoding: .utf8) }
}

final class SystemClock: NSObject, Clock {
    func nowMillis() -> Int64 { Int64(Date().timeIntervalSince1970 * 1000) }
}

// MARK: - Repository

/// Runs the shared Kotlin valuation core on-device. EDGAR, quotes and the published rates file
/// are fetched directly from the phone with the user's identity; bundled samples cover offline.
struct CoreValuationRepository: ValuationRepository {
    let userAgent: String?
    private let fallback = SampleValuationRepository()
    private static let core: ValuationCore = {
        let bundled = Bundle.main.url(forResource: "rates", withExtension: "json")
            .flatMap { try? String(contentsOf: $0, encoding: .utf8) }
            .flatMap { RatesSnapshot.companion.parse(text: $0) }
        return ValuationCore(fetcher: URLSessionFetcher(), cache: FileKVCache(), clock: SystemClock(),
                             publishedBaseUrl: "https://swcsoftware.github.io/Mobile-Valuation", bundledRates: bundled)
    }()

    private func ua() throws -> String {
        guard let userAgent else { throw RepositoryError.invalidIdentity("Set your name and email in Settings so SEC EDGAR can identify you.") }
        return userAgent
    }

    /// Kotlin exceptions arrive as NSError; map them onto the typed RepositoryError.
    private static func map(_ error: Error) -> RepositoryError {
        let ns = error as NSError
        let kotlin = ns.userInfo["KotlinException"] as? KotlinThrowable
        let code = kotlin.map { ValuationCore.companion.errorCode(e: $0) } ?? "engine_error"
        let message = kotlin?.message ?? ns.localizedDescription
        switch code {
        case "unknown_ticker": return .unknownTicker(message)
        case "no_annual_data": return .noAnnualData(message)
        case "rate_limited": return .rateLimited(message)
        case "invalid_identity": return .invalidIdentity(message)
        case "upstream_unavailable": return .upstreamUnavailable(message)
        case "offline": return .offline
        default: return .server(500, message)
        }
    }

    private func onCore<T>(_ work: @escaping () throws -> T) async throws -> T {
        try await withCheckedThrowingContinuation { cont in
            DispatchQueue.global(qos: .userInitiated).async {
                do { cont.resume(returning: try work()) } catch { cont.resume(throwing: Self.map(error)) }
            }
        }
    }

    func search(query: String) async throws -> [CompanyRef] {
        let ua = try ua()
        do {
            let json = try await onCore { try Self.core.searchJson(query: query, userAgent: ua) }
            return try JSONDecoder.engine.decode([CompanyRef].self, from: Data(json.utf8))
        } catch RepositoryError.offline {
            return try await fallback.search(query: query)
        }
    }

    func valuation(ticker: String, priceOverride: Double?, overrides: RateOverrides) async throws -> ValuationReport {
        let ua = try ua()
        let ovJson = String(data: try JSONEncoder().encode(overrides), encoding: .utf8)
        do {
            let json = try await onCore {
                try Self.core.valuationJson(ticker: ticker, userAgent: ua, priceOverride: priceOverride.map { KotlinDouble(value: $0) }, overridesJson: ovJson)
            }
            var report = try JSONDecoder.engine.decode(ValuationReport.self, from: Data(json.utf8))
            report.rawJSON = json
            return report
        } catch RepositoryError.offline {
            if SampleValuationRepository.tickers.contains(ticker.uppercased()) {
                return try await fallback.valuation(ticker: ticker, priceOverride: priceOverride, overrides: overrides)
            }
            throw RepositoryError.offline
        }
    }

    func rates() async -> RatesInfo? {
        guard let json = try? await onCore({ try Self.core.ratesJson() }) else { return nil }
        return try? JSONDecoder().decode(RatesInfo.self, from: Data(json.utf8))
    }

    func explain(_ report: ValuationReport) async -> ExplainSummary? {
        // Prefer the core's own JSON; a Swift re-encode would not round-trip keys like treasury_10y_pct.
        let s: String
        if let raw = report.rawJSON { s = raw }
        else if let data = try? JSONEncoder.engine.encode(report), let str = String(data: data, encoding: .utf8) { s = str }
        else { return nil }
        guard let json = try? await onCore({ try Self.core.explainJson(reportJson: s) }) else { return nil }
        return try? JSONDecoder().decode(ExplainSummary.self, from: Data(json.utf8))
    }

    func glossary() -> [GlossaryEntry] {
        guard let g = try? Self.core.glossaryJson() else { return [] }
        return (try? JSONDecoder().decode([GlossaryEntry].self, from: Data(g.utf8))) ?? []
    }
}

/// Bundled JSON captured from the engine for AAPL, KO and MSFT (offline fallback).
struct SampleValuationRepository: ValuationRepository {
    static let tickers = ["AAPL", "KO", "MSFT"]

    func search(query: String) async throws -> [CompanyRef] {
        let q = query.uppercased()
        var out: [CompanyRef] = []
        for t in Self.tickers {
            let c = try await valuation(ticker: t, priceOverride: nil, overrides: .none).company
            if c.ticker.hasPrefix(q) || c.name.uppercased().contains(q) { out.append(c) }
        }
        return out
    }

    func valuation(ticker: String, priceOverride: Double?, overrides: RateOverrides) async throws -> ValuationReport {
        guard let url = Bundle.main.url(forResource: ticker.uppercased(), withExtension: "json") else {
            throw RepositoryError.unknownTicker("'\(ticker.uppercased())' is not bundled and the device is offline.")
        }
        let data = try Data(contentsOf: url)
        var r = try JSONDecoder.engine.decode(ValuationReport.self, from: data)
        r.rawJSON = String(data: data, encoding: .utf8)
        return r
    }

    func rates() async -> RatesInfo? { nil }
    func explain(_ report: ValuationReport) async -> ExplainSummary? { await CoreValuationRepository(userAgent: nil).explain(report) }
    func glossary() -> [GlossaryEntry] { [] }
}
