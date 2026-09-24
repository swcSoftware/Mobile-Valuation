import XCTest
@testable import ValueLens

/// Sprint 6, end to end through the XCFramework: the words come from the core's display-labels.json.
/// The file's own rules are tested in the core (`DisplayLabelsTest`); this checks they reach Swift.
final class LabelsTests: XCTestCase {

    func testTagsReadAsEnglish() {
        XCTAssertEqual(Labels.tag("us-gaap:EarningsPerShareDiluted"), "Earnings per share (diluted)")
        XCTAssertEqual(Labels.tag("valuelens:derived"), "Calculated by ValueLens from filed figures")
    }

    func testAKeyKeepsItsMeaningInsideOneMetric() {
        XCTAssertEqual(Labels.input("revenue"), "Revenue")
        XCTAssertEqual(Labels.input("revenue", metric: "fcff_growth"), "Revenue growth, 5-yr")
    }

    func testSentencesAndFormulasLoseTheirKeys() {
        XCTAssertEqual(Labels.sentence("Latest 10-K missing concepts: eps_diluted"),
                       "Latest 10-K missing concepts: diluted earnings per share")
        XCTAssertEqual(Labels.formula("MoS = 1 − price / intrinsic_value"), "MoS = 1 − price / intrinsic value")
    }

    func testTheIndexListsEveryTermWithItsTags() {
        let index = CoreValuationRepository.tagIndex()
        XCTAssertGreaterThan(index.count, 30, "one entry per concept the app reads")
        let revenue = try? XCTUnwrap(index.first { $0.key == "revenue" })
        XCTAssertEqual(revenue?.term, "Revenue")
        XCTAssertEqual(revenue?.tags.first?.tag, "us-gaap:Revenues", "tags are listed in the order they are tried")
        XCTAssertFalse(index.flatMap(\.tags).contains { $0.label.hasPrefix("us-gaap") }, "every tag has a real label")
    }
}
