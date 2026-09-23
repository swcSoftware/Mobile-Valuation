import XCTest
import SwiftUI
@testable import ValueLens

/// Proves that **every** layout places the four components that make the app trustworthy, for a
/// fixture of every state that demands them — rather than trusting a code review to notice when one
/// goes missing. See `docs/DESIGN.md`, "The four things every layout must carry".
///
/// This is the test Sprint 5 Track A exists to make possible: with two presentations of the same
/// report, a withheld value or an "assumed" label can be dropped from one of them and nobody would
/// see it until a user did.
@MainActor
final class LayoutContractTests: XCTestCase {

    /// Fixtures chosen for the state they force, not the company they describe.
    private static let fixtures: [(name: String, model: ValuationModel, because: String)] = [
        ("CRWV", .modern,      "the discounted cash flow returns nothing usable — value withheld"),
        ("GateFailed", .traditional, "a data check fails — value withheld (synthesized; see the file)"),
        ("MCD", .traditional,  "negative equity and a share-scale correction warning"),
        ("JPM", .traditional,  "financial sector mode"),
        ("O", .traditional,    "REIT sector mode"),
        ("AGNC", .traditional, "mortgage REIT — no revenue tag at all"),
    ]

    func testEveryLayoutPlacesEveryComponentTheReportDemands() throws {
        for fixture in Self.fixtures {
            let report = try Self.load(fixture.name)
            let result = report.result(for: fixture.model)
            let demanded = RequiredComponent.demanded(by: report, result: result)
            XCTAssertFalse(demanded.isEmpty,
                           "\(fixture.name) was chosen because it demands something; if it demands nothing the fixture is wrong")

            for style in LayoutStyle.allCases {
                let placed = Self.placedComponents(style: style, report: report, model: fixture.model)
                let missing = demanded.subtracting(placed)
                XCTAssertTrue(missing.isEmpty,
                              """
                              \(style.displayName) dropped \(missing.map(\.rawValue).sorted().joined(separator: ", ")) \
                              for \(fixture.name) (\(fixture.because)).
                              demanded: \(demanded.map(\.rawValue).sorted())
                              placed:   \(placed.map(\.rawValue).sorted())
                              """)
            }
        }
    }

    /// The contract is only meaningful if it can fail. A report that demands nothing beyond
    /// provenance must not be satisfied by a layout that renders nothing at all.
    func testContractDetectsAnEmptyLayout() throws {
        let report = try Self.load("CRWV")
        let result = report.result(for: .modern)
        let demanded = RequiredComponent.demanded(by: report, result: result)
        XCTAssertTrue(demanded.contains(.withheldValue))
        let placed = Self.placedComponents(style: nil, report: report, model: .modern)  // renders EmptyView
        XCTAssertFalse(demanded.isSubset(of: placed),
                       "an empty layout satisfied the contract — the harness is not observing preferences")
    }

    func testWithheldIsDemandedByBothRoutes() throws {
        let gate = try Self.load("GateFailed")
        XCTAssertTrue(gate.checksFailed)
        XCTAssertTrue(RequiredComponent.demanded(by: gate, result: gate.result(for: .traditional)).contains(.withheldValue),
                      "a failed data check must demand the withheld-value component")

        let crwv = try Self.load("CRWV")
        XCTAssertFalse(crwv.checksFailed, "CRWV passes its gate; it is the *model* that produces nothing")
        XCTAssertEqual(crwv.result(for: .modern).marginOfSafety.verdict, .insufficientData)
        XCTAssertTrue(RequiredComponent.demanded(by: crwv, result: crwv.result(for: .modern)).contains(.withheldValue),
                      "an insufficient-data verdict must demand the withheld-value component")
    }

    // MARK: - harness

    private static func load(_ name: String) throws -> ValuationReport {
        let url = try XCTUnwrap(Bundle(for: LayoutContractTests.self).url(forResource: name, withExtension: "json"),
                                "fixture \(name).json is not in the test bundle")
        return try JSONDecoder.engine.decode(ValuationReport.self, from: Data(contentsOf: url))
    }

    /// Renders a layout off-screen and collects what it reported placing. `style: nil` renders
    /// nothing, which is how `testContractDetectsAnEmptyLayout` proves the harness can fail.
    private static func placedComponents(style: LayoutStyle?, report: ValuationReport, model: ValuationModel) -> Set<RequiredComponent> {
        let box = Box()
        let host = UIHostingController(rootView: Harness(style: style, report: report, model: model, box: box))
        // SwiftUI only runs a real render pass for a view in a visible window; `layoutIfNeeded` on a
        // detached view returns without ever evaluating `body`, so preferences never fire.
        let window = UIWindow(frame: CGRect(x: 0, y: 0, width: 393, height: 852))
        window.rootViewController = host
        window.isHidden = false
        window.makeKeyAndVisible()
        defer { window.isHidden = true; window.rootViewController = nil }
        host.view.setNeedsLayout()
        host.view.layoutIfNeeded()
        // Preference changes land on later run-loop turns; spin a bounded number of them rather
        // than sleeping for an arbitrary interval.
        for _ in 0..<100 where box.value.isEmpty {
            RunLoop.current.run(until: Date().addingTimeInterval(0.01))
        }
        return box.value
    }

    private final class Box {
        var value: Set<RequiredComponent> = []
    }

    private struct Harness: View {
        let style: LayoutStyle?
        let report: ValuationReport
        let model: ValuationModel
        let box: Box
        @State private var expandAll: Bool? = nil
        @State private var expandVersion = 0
        @State private var selected: ValuationModel = .traditional

        var body: some View {
            let ctx = LayoutContext(
                report: report,
                result: report.result(for: model),
                // Deliberately nil: a layout must carry the four whether or not the core's
                // plain-language summary loaded.
                explain: nil,
                expert: false,
                showingMathTemporarily: false,
                model: $selected,
                editPrice: {},
                setShowMath: { _ in },
                expandAll: $expandAll,
                expandVersion: $expandVersion,
                reportIssue: {}
            )
            Group {
                switch style {
                case .classic: ClassicLayout(ctx: ctx)
                case .reportCard: ReportCardLayout(ctx: ctx)
                case nil: EmptyView()
                }
            }
            .onPreferenceChange(PlacedComponentsKey.self) { placed in
                box.value.formUnion(placed)
            }
            .onAppear { selected = model }
        }
    }
}
