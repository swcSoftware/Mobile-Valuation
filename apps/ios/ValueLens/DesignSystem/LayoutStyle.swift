import SwiftUI

/// Which presentation of a company the user has chosen.
///
/// A layout is *presentation only*. It reads `ValuationReport`, `ModelResult` and `ExplainSummary`
/// and renders them; it never computes a number, a label or a verdict. That rule is what keeps two
/// layouts from disagreeing about the same company (CLAUDE.md non-negotiable 7).
enum LayoutStyle: String, CaseIterable, Codable, Sendable {
    /// The layout that shipped in Sprints 0–4. Dense, everything on one page.
    case classic
    /// Sprint 5's second presentation: the decision first, health facts graded against a printed rule.
    case reportCard

    /// The default, and it stays the default until the owner says otherwise after using the other
    /// one on a physical device (Sprint 5 Track D).
    static let `default`: LayoutStyle = .classic

    var displayName: String {
        switch self {
        case .classic: "Classic"
        case .reportCard: "Report card"
        }
    }

    var blurb: String {
        switch self {
        case .classic: "Dense, everything on one page."
        case .reportCard: "Graded health facts, the decision first."
        }
    }
}

/// The investor lens: the bar the report card grades a business against.
///
/// It changes **only** how a health fact is graded and which fact is read first. It never changes a
/// valuation, a margin of safety, a verdict or which model opens — two people looking at the same
/// company see one fair value (owner decision, 2026-09-23). The grading itself lives in the core.
enum InvestorLens: String, CaseIterable, Codable, Sendable {
    case value = "VALUE"
    case growth = "GROWTH"

    static let `default`: InvestorLens = .value

    var toggled: InvestorLens { self == .value ? .growth : .value }
}

// MARK: - the contract every layout owes the reader

/// The four things that make the app trustworthy, and the four things a second layout can silently
/// drop. Each is a component a layout is *required to place* when the report calls for it, and
/// `LayoutContractTests` proves every layout places them rather than taking our word for it.
///
/// See `docs/DESIGN.md`, "The four things every layout must carry".
enum RequiredComponent: String, CaseIterable, Sendable {
    /// A value we refused to show, presented as a deliberate act rather than an error or a blank.
    case withheldValue
    /// `warnings` and any data check that did not pass.
    case dataNotes
    /// Which inputs were measured and which were assumed.
    case provenance
    /// Operating company / bank / REIT — a company is not valued the same way as its neighbour.
    case sectorMode

    /// What *this* report obliges a layout to show. A report with nothing withheld does not owe a
    /// withheld-value card; one that withholds owes it in every layout.
    static func demanded(by report: ValuationReport, result: ModelResult) -> Set<RequiredComponent> {
        var out: Set<RequiredComponent> = [.provenance]
        if report.checksFailed || result.marginOfSafety.verdict == .insufficientData {
            out.insert(.withheldValue)
        }
        if !report.warnings.isEmpty || report.dataChecks.contains(where: { $0.status != "pass" }) {
            out.insert(.dataNotes)
        }
        if let sector = report.sector, sector.mode != "general" {
            out.insert(.sectorMode)
        }
        return out
    }
}

/// Collects what a layout actually placed, so the contract can be checked instead of assumed.
/// Preferences flow up the view tree, so a component reports itself wherever a layout puts it.
struct PlacedComponentsKey: PreferenceKey {
    static let defaultValue: Set<RequiredComponent> = []
    static func reduce(value: inout Set<RequiredComponent>, nextValue: () -> Set<RequiredComponent>) {
        value.formUnion(nextValue())
    }
}

extension View {
    /// Marks this view as satisfying one of the four obligations.
    func places(_ component: RequiredComponent) -> some View {
        preference(key: PlacedComponentsKey.self, value: [component])
    }
}
