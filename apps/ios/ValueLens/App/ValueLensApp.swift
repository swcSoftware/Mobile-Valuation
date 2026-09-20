import SwiftUI

/// App-wide navigation state so deep links (valuelens://ticker/AAPL) can push a company
/// from anywhere.
@Observable
final class AppRouter {
    var tab: Tab = .watchlist
    var watchlistPath: [CompanyRef] = []
    var searchPath: [CompanyRef] = []
    /// Ticker requested via URL before the report is known; resolved by the receiving view.
    var pendingTicker: String?

    enum Tab: Hashable { case watchlist, search, settings }

    /// valuelens://ticker/AAPL  or  valuelens://company/AAPL
    func handle(_ url: URL) -> Bool {
        guard url.scheme?.lowercased() == "valuelens" else { return false }
        let host = url.host()?.lowercased()
        let path = url.pathComponents.filter { $0 != "/" }
        guard ["ticker", "company"].contains(host ?? ""), let ticker = path.first, !ticker.isEmpty else { return false }
        tab = .watchlist
        pendingTicker = ticker.uppercased()
        return true
    }
}

@main
struct ValueLensApp: App {
    @State private var settings = AppSettings()
    @State private var watchlist = WatchlistStore()
    @State private var router = AppRouter()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(settings)
                .environment(watchlist)
                .environment(router)
                .preferredColorScheme(.dark)
                .tint(Theme.value)
                .onOpenURL { _ = router.handle($0) }
        }
    }
}

struct RootView: View {
    @Environment(AppSettings.self) private var settings
    @Environment(WatchlistStore.self) private var watchlist
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        Group {
            if settings.hasCompletedOnboarding, settings.identity != nil {
                MainTabView()
            } else {
                OnboardingFlow()
            }
        }
        .background(Theme.background)
        .task { await settings.checkEngine() }
        .onChange(of: scenePhase) { _, phase in
            guard phase == .active else { return }
            Task {
                await settings.checkEngine()
                // Refresh the watchlist when the app comes to the foreground and data is > 15 min old.
                if settings.engineReachable, watchlist.lastUpdated.map({ Date.now.timeIntervalSince($0) > 900 }) ?? true {
                    await watchlist.refreshAll(using: settings.repository, overrides: settings.overrides)
                }
            }
        }
    }
}

struct MainTabView: View {
    @Environment(AppRouter.self) private var router

    var body: some View {
        @Bindable var router = router
        TabView(selection: $router.tab) {
            WatchlistView()
                .tabItem { Label("Watchlist", systemImage: "list.star") }
                .tag(AppRouter.Tab.watchlist)
            SearchView()
                .tabItem { Label("Search", systemImage: "magnifyingglass") }
                .tag(AppRouter.Tab.search)
            SettingsView()
                .tabItem { Label("Settings", systemImage: "slider.horizontal.3") }
                .tag(AppRouter.Tab.settings)
        }
    }
}
