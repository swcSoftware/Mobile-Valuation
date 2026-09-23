import Foundation
import Observation

/// App-wide user preferences (UserDefaults) + the SEC identity (Keychain).
@Observable
final class AppSettings {
    private let defaults = UserDefaults.standard

    var identity: SECIdentity? {
        didSet {
            if let identity { try? KeychainStore.save(identity) } else { KeychainStore.clear() }
        }
    }
    var hasCompletedOnboarding: Bool { didSet { defaults.set(hasCompletedOnboarding, forKey: "onboarded") } }
    var expertMode: Bool { didSet { defaults.set(expertMode, forKey: "expertMode") } }
    /// Which presentation of a company to render. Stays `.classic` until the owner promotes the
    /// other one after using it on a physical device (Sprint 5 Track D).
    var layoutStyle: LayoutStyle { didSet { defaults.set(layoutStyle.rawValue, forKey: "layoutStyle") } }
    var overrides: RateOverrides {
        didSet { defaults.set(try? JSONEncoder().encode(overrides), forKey: "overrides") }
    }
    /// Latest published FRED rates (nil until loaded).
    var rates: RatesInfo?

    init() {
        identity = KeychainStore.load()
        hasCompletedOnboarding = defaults.bool(forKey: "onboarded")
        expertMode = defaults.bool(forKey: "expertMode")
        layoutStyle = defaults.string(forKey: "layoutStyle").flatMap(LayoutStyle.init(rawValue:)) ?? .default
        if let data = defaults.data(forKey: "overrides"), let o = try? JSONDecoder().decode(RateOverrides.self, from: data) {
            overrides = o
        } else {
            overrides = .none
        }
    }

    var repository: ValuationRepository {
        CoreValuationRepository(userAgent: identity?.userAgent)
    }

    @MainActor
    func refreshRates() async {
        rates = await repository.rates()
    }

    func resetOnboarding() {
        hasCompletedOnboarding = false
    }
}
