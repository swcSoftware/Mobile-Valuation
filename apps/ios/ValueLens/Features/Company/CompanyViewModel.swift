import Foundation
import Observation

@Observable
@MainActor
final class CompanyViewModel {
    let company: CompanyRef
    var report: ValuationReport?
    var model: ValuationModel = .traditional
    var loading = false
    var error: String?
    var priceOverride: Double?

    init(company: CompanyRef) { self.company = company }

    var result: ModelResult? { report?.result(for: model) }

    func load(using repo: ValuationRepository, overrides: RateOverrides) async {
        loading = true; error = nil
        defer { loading = false }
        do {
            report = try await repo.valuation(ticker: company.ticker, priceOverride: priceOverride, overrides: overrides)
        } catch {
            self.error = error.localizedDescription
        }
    }
}
