import XCTest
@testable import ValueLens

/// Sprint 5 Track D: the choices a user makes about presentation, and the defaults they start from.
final class PresentationSettingsTests: XCTestCase {

    func testClassicStaysTheDefaultUntilTheOwnerPromotesTheReportCard() {
        // Changing this is an owner decision taken after the report card has been used on a
        // physical device (docs/TASKS.md, Track D) — not a refactor.
        XCTAssertEqual(LayoutStyle.default, .classic)
    }

    func testValueIsTheDefaultLens() {
        XCTAssertEqual(InvestorLens.default, .value)
    }

    func testAnUnknownStoredValueFallsBackToTheDefaults() {
        // A value written by a future build and read by this one must not flip anyone's layout.
        XCTAssertNil(LayoutStyle(rawValue: "somethingWeRemoved"))
        XCTAssertNil(InvestorLens(rawValue: "income"))
    }

    func testLensCopyComesFromTheCoreWithItsLiveRule() {
        let lenses = SampleValuationRepository().lenses()
        XCTAssertEqual(lenses.map(\.lens), [.value, .growth], "Value is offered first — it is the default")
        XCTAssertEqual(lenses.first?.growthRule, "A at 10% a year or better",
                       "the onboarding example quotes the rule; if this moved, the grading moved")
        XCTAssertTrue(lenses.allSatisfy { $0.blurb.count > 40 })
    }
}
