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
    /// Light / Dark / System. Independent of `layoutStyle` — either layout works on either ground.
    var themePreference: ThemePreference { didSet { defaults.set(themePreference.rawValue, forKey: "themePreference") } }
    /// The user's chrome color, or `nil` for the theme's own. Never applied to price or value.
    var accentHex: UInt32? {
        didSet {
            if let accentHex { defaults.set(Int(accentHex), forKey: "accentHex") }
            else { defaults.removeObject(forKey: "accentHex") }
        }
    }
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
        themePreference = defaults.string(forKey: "themePreference").flatMap(ThemePreference.init(rawValue:)) ?? .dark
        accentHex = defaults.object(forKey: "accentHex").flatMap { ($0 as? Int).map(UInt32.init) }
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
