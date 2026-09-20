import XCTest
@testable import ValueLens

final class ValuationReportDecodingTests: XCTestCase {
    private func sample(_ ticker: String) throws -> ValuationReport {
        let url = try XCTUnwrap(Bundle(for: Self.self).url(forResource: ticker, withExtension: "json")
                                ?? Bundle.main.url(forResource: ticker, withExtension: "json"))
        return try JSONDecoder.engine.decode(ValuationReport.self, from: Data(contentsOf: url))
    }

    func testBundledSamplesDecode() throws {
        for t in ["AAPL", "KO", "MSFT"] {
            let r = try sample(t)
            XCTAssertEqual(r.company.ticker, t)
            XCTAssertEqual(r.history.count, 10)
            XCTAssertFalse(r.modelA.metrics.isEmpty)
            XCTAssertNotNil(r.modelA.metric("graham_revised"))
            XCTAssertNotNil(r.modelB.metric("wacc"))
            XCTAssertGreaterThan(r.assumptions.treasury10YPct, 0)  // snake-case key regression (ISSUES/ARCHITECTURE gotcha)
        }
    }

    func testMetricCarriesFormulaAndSources() throws {
        let m = try XCTUnwrap(sample("AAPL").modelA.metric("graham_classic"))
        XCTAssertTrue(m.formula.contains("8.5"))
        XCTAssertEqual(m.sources.first?.tag, "us-gaap:EarningsPerShareDiluted")
        XCTAssertNotNil(m.inputs["eps_ttm"] ?? nil)
    }
}

final class RepositoryErrorTests: XCTestCase {
    func testEngineErrorEnvelopeMapsToTypedCases() {
        let body = #"{"error":{"code":"unknown_ticker","message":"'ZZZZ' is not a ticker.","detail":{}},"detail":"'ZZZZ' is not a ticker."}"#.data(using: .utf8)!
        XCTAssertEqual(RepositoryError.from(status: 404, body: body), .unknownTicker("'ZZZZ' is not a ticker."))
        let rl = #"{"error":{"code":"rate_limited","message":"slow"}}"#.data(using: .utf8)!
        XCTAssertEqual(RepositoryError.from(status: 429, body: rl), .rateLimited("slow"))
    }

    func testLegacyDetailOnlyBody() {
        let body = #"{"detail":"Unknown ticker: X"}"#.data(using: .utf8)!
        XCTAssertEqual(RepositoryError.from(status: 404, body: body), .unknownTicker("Unknown ticker: X"))
        XCTAssertEqual(RepositoryError.from(status: 500, body: Data()), .server(500, ""))
    }
}

final class FormatterTests: XCTestCase {
    func testCompactCurrency() {
        XCTAssertEqual(Fmt.compact(416_161_000_000), "$416.2B")
        XCTAssertEqual(Fmt.compact(1_250_000_000_000), "$1.25T")
        XCTAssertEqual(Fmt.compact(-184_252_500_000), "−$184.3B")
        XCTAssertEqual(Fmt.compact(nil), "—")
    }

    func testMetricFormattingByUnit() {
        XCTAssertEqual(Fmt.metric(Metric(key: "k", label: "l", value: 72.1, unit: "USD/share", formula: "")), "$72.10")
        XCTAssertEqual(Fmt.metric(Metric(key: "k", label: "l", value: 9.15, unit: "%", formula: "")), "9.2%")
        XCTAssertEqual(Fmt.metric(Metric(key: "k", label: "l", value: 10, unit: "x", formula: "")), "10×")
    }
}

final class RateOverridesTests: XCTestCase {
    func testOnlySetValuesBecomeQueryItems() {
        var o = RateOverrides.none
        XCTAssertTrue(o.queryItems.isEmpty)
        o.hurdleRatePct = 8
        o.beta = 1.2
        let items = Dictionary(uniqueKeysWithValues: o.queryItems.map { ($0.name, $0.value) })
        XCTAssertEqual(items["hurdle_rate_pct"], "8.0")
        XCTAssertEqual(items["beta"], "1.2")
        XCTAssertNil(items["aaa_yield_pct"])
    }
}

final class IdentityTests: XCTestCase {
    func testValidationAndUserAgent() {
        XCTAssertFalse(SECIdentity(fullName: "Will", email: "w@x.com").isValid)
        XCTAssertFalse(SECIdentity(fullName: "Will Tester", email: "nope").isValid)
        let id = SECIdentity(fullName: " Will Tester ", email: "will@example.com ")
        XCTAssertTrue(id.isValid)
        XCTAssertEqual(id.userAgent, "Will Tester will@example.com")
    }
}

final class DeepLinkTests: XCTestCase {
    @MainActor func testTickerURLSetsPendingTicker() {
        let r = AppRouter()
        XCTAssertTrue(r.handle(URL(string: "valuelens://ticker/aapl")!))
        XCTAssertEqual(r.pendingTicker, "AAPL")
        XCTAssertEqual(r.tab, .watchlist)
        XCTAssertFalse(r.handle(URL(string: "https://example.com/ticker/AAPL")!))
        XCTAssertFalse(r.handle(URL(string: "valuelens://ticker/")!))
    }
}

final class VerdictTests: XCTestCase {
    func testRankOrdering() {
        XCTAssertLessThan(Verdict.rank(.deepValue), Verdict.rank(.withinMargin))
        XCTAssertLessThan(Verdict.rank(.aboveIntrinsic), Verdict.rank(.insufficientData))
    }
}

final class Sprint2ContractTests: XCTestCase {
    func testDataChecksAndProvenanceDecodeWithDefaults() throws {
        // Sprint-1 payloads (no data_checks/provenance) must still decode.
        let url = try XCTUnwrap(Bundle.main.url(forResource: "AAPL", withExtension: "json"))
        let r = try JSONDecoder.engine.decode(ValuationReport.self, from: Data(contentsOf: url))
        XCTAssertNotNil(r.dataChecks); XCTAssertNotNil(r.provenance)
        XCTAssertFalse(r.checksFailed)
    }

    func testRatesInfoDecodesPublishedFormat() throws {
        let json = #"{"as_of":"2026-09-17","published_at":"2026-09-19T21:00:00Z","aaa_yield_pct":5.94,"treasury_10y_pct":4.94,"source":"FRED"}"#
        let r = try JSONDecoder().decode(RatesInfo.self, from: Data(json.utf8))
        XCTAssertEqual(r.asOf, "2026-09-17"); XCTAssertEqual(r.treasury10YPct, 4.94)
    }

    func testBundledRatesSnapshotPresent() throws {
        let url = try XCTUnwrap(Bundle.main.url(forResource: "rates", withExtension: "json"))
        let r = try JSONDecoder().decode(RatesInfo.self, from: Data(contentsOf: url))
        XCTAssertGreaterThan(r.aaaYieldPct, 0)
    }

    func testGlossaryComesFromCore() {
        let g = CoreValuationRepository(userAgent: nil).glossary()
        XCTAssertGreaterThanOrEqual(g.count, 8)
        XCTAssertTrue(g.contains { $0.key == "beta" && !$0.plain.isEmpty && !$0.expert.isEmpty })
    }

    func testExplainRoundTripsThroughCore() async throws {
        let repo = SampleValuationRepository()
        let r = try await repo.valuation(ticker: "KO", priceOverride: nil, overrides: .none)
        let e = await repo.explain(r)
        let summary = try XCTUnwrap(e)
        XCTAssertTrue(summary.verdictA.contains("COCA COLA") || summary.verdictA.contains("Coca"))
        XCTAssertFalse(summary.facts.isEmpty)
    }
}
