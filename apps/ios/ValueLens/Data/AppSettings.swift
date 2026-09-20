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

    /// Build-time default from Info.plist (Debug: localhost; Release: hosted placeholder).
    static let builtInEngineURL: String = (Bundle.main.object(forInfoDictionaryKey: "ENGINE_BASE_URL") as? String)
        .flatMap { $0.isEmpty ? nil : $0 } ?? "http://127.0.0.1:8000"

    /// LAN addresses the engine reported in its last /health response (for physical devices).
    var engineLANAddresses: [String] = []
    var lastHealthCheck: Date?

    init() {
        identity = KeychainStore.load()
        hasCompletedOnboarding = defaults.bool(forKey: "onboarded")
        engineURL = defaults.string(forKey: "engineURL") ?? Self.builtInEngineURL
        if let data = defaults.data(forKey: "overrides"), let o = try? JSONDecoder().decode(RateOverrides.self, from: data) {
            overrides = o
        } else {
            overrides = .none
        }
    }

    var repository: ValuationRepository {
        let url = URL(string: engineURL.trimmingCharacters(in: .whitespaces)) ?? URL(string: Self.builtInEngineURL)!
        return RemoteValuationRepository(client: APIClient(baseURL: url, userAgent: identity?.userAgent))
    }

    /// Pings /health and records reachability + LAN addresses.
    @MainActor
    func checkEngine() async {
        let health = await repository.healthDetails()
        engineReachable = health != nil
        engineLANAddresses = health?.lanAddresses ?? []
        lastHealthCheck = .now
    }

    func resetEngineURL() { engineURL = Self.builtInEngineURL }

    func resetOnboarding() {
        hasCompletedOnboarding = false
    }
}
