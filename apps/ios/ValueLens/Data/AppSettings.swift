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
    var engineURL: String { didSet { defaults.set(engineURL, forKey: "engineURL") } }
    var overrides: RateOverrides {
        didSet { defaults.set(try? JSONEncoder().encode(overrides), forKey: "overrides") }
    }
    var engineReachable: Bool = false

    init() {
        identity = KeychainStore.load()
        hasCompletedOnboarding = defaults.bool(forKey: "onboarded")
        engineURL = defaults.string(forKey: "engineURL") ?? "http://127.0.0.1:8000"
        if let data = defaults.data(forKey: "overrides"), let o = try? JSONDecoder().decode(RateOverrides.self, from: data) {
            overrides = o
        } else {
            overrides = .none
        }
    }

    var repository: ValuationRepository {
        let url = URL(string: engineURL) ?? URL(string: "http://127.0.0.1:8000")!
        return RemoteValuationRepository(client: APIClient(baseURL: url, userAgent: identity?.userAgent))
    }

    func resetOnboarding() {
        hasCompletedOnboarding = false
    }
}
