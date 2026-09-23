import XCTest
@testable import ValueLens

/// End-to-end through the XCFramework: the core grades, Swift decodes. What this guards is the seam
/// between the two — a renamed Kotlin property or a nullability change would otherwise decode to nil
/// and the report card would quietly show placeholders forever.
///
/// The grades themselves are pinned in the core (`GradingTest`); here we only check they arrive
/// intact on the iOS side.
final class ReportCardTests: XCTestCase {

    private func fixture(_ name: String) throws -> ValuationReport {
        let url = try XCTUnwrap(Bundle(for: Self.self).url(forResource: name, withExtension: "json"))
        let data = try Data(contentsOf: url)
        var r = try JSONDecoder.engine.decode(ValuationReport.self, from: data)
        r.rawJSON = String(data: data, encoding: .utf8)
        return r
    }

    private func card(_ name: String, _ lens: InvestorLens) async throws -> ReportCardSummary {
        let summary = await SampleValuationRepository().reportCard(try fixture(name), lens: lens)
        return try XCTUnwrap(summary, "the core's report card for \(name) did not decode in Swift")
    }

    func testGradesSurviveTheTripFromKotlin() async throws {
        let mcd = try await card("MCD", .value)
        XCTAssertEqual(mcd.facts.map(\.slot), ["profit", "debt", "conversion", "growth"])
        XCTAssertEqual(mcd.facts.map { $0.grade ?? "-" }, ["B", "-", "B", "B"])
        XCTAssertEqual(mcd.facts[1].state, "ungradable")
        XCTAssertNil(mcd.facts[1].value, "an ungradable fact must arrive with no number")
        XCTAssertEqual(mcd.chipA, VerdictChip(label: "Above fair value", tone: "bad"))
        XCTAssertEqual(mcd.modeLabel, "Operating company")
    }

    func testHistoryArrivesWithItsSeries() async throws {
        let mcd = try await card("MCD", .value)
        let profit = try XCTUnwrap(mcd.facts.first { $0.slot == "profit" }?.history)
        XCTAssertEqual(profit.phrase, "Below its 10-yr average")
        XCTAssertEqual(profit.points.count, 10, "the sparkline needs every filed year")
        XCTAssertEqual(profit.points.map(\.year), profit.points.map(\.year).sorted(), "oldest first")
    }

    func testTheLensReordersAndRegradesButNeverRevalues() async throws {
        let value = try await card("KO", .value)
        let growth = try await card("KO", .growth)
        XCTAssertEqual(growth.lens, "GROWTH")
        XCTAssertEqual(growth.facts.first?.slot, "growth", "Growth reads growth first")
        XCTAssertEqual(growth.facts.first?.grade, "D")
        XCTAssertEqual(value.chipA, growth.chipA, "the verdict must not move with the lens")
        XCTAssertEqual(Dictionary(uniqueKeysWithValues: value.facts.map { ($0.slot, $0.value) }),
                       Dictionary(uniqueKeysWithValues: growth.facts.map { ($0.slot, $0.value) }),
                       "a reported figure moved with the lens")
    }

    func testFinancialsArriveWithTheSubstitutedMeasureAndRefusals() async throws {
        let jpm = try await card("JPM", .value)
        let profit = try XCTUnwrap(jpm.facts.first { $0.slot == "profit" })
        XCTAssertEqual(profit.label, "Return on equity")
        XCTAssertEqual(profit.grade, "A")
        XCTAssertEqual(jpm.facts.filter { $0.state == "refused" }.map(\.slot).sorted(), ["conversion", "debt"])
    }
}
