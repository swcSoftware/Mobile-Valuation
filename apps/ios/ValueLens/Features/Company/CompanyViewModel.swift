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
    var errorTitle: String?
    var errorIcon: String = "exclamationmark.triangle"
    var needsIdentity = false
    var priceOverride: Double?

    init(company: CompanyRef) { self.company = company }

    var result: ModelResult? { report?.result(for: model) }

    func load(using repo: ValuationRepository, overrides: RateOverrides) async {
        loading = true; error = nil; errorTitle = nil; needsIdentity = false
        defer { loading = false }
        do {
            report = try await repo.valuation(ticker: company.ticker, priceOverride: priceOverride, overrides: overrides)
        } catch let e as RepositoryError {
            error = e.errorDescription
            errorTitle = e.title
            needsIdentity = (e == .invalidIdentity("")) || { if case .invalidIdentity = e { return true } else { return false } }()
            errorIcon = {
                switch e {
                case .offline, .upstreamUnavailable: "wifi.slash"
                case .unknownTicker, .noAnnualData: "doc.questionmark"
                case .rateLimited: "hourglass"
                case .invalidIdentity: "person.crop.circle.badge.exclamationmark"
                default: "exclamationmark.triangle"
                }
            }()
        } catch {
            self.error = error.localizedDescription
        }
    }
}
