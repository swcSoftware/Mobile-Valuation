import SwiftUI

@main
struct ValueLensApp: App {
    @State private var settings = AppSettings()
    @State private var watchlist = WatchlistStore()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(settings)
                .environment(watchlist)
                .preferredColorScheme(.dark)
                .tint(Theme.value)
        }
    }
}

struct RootView: View {
    @Environment(AppSettings.self) private var settings

    var body: some View {
        Group {
            if settings.hasCompletedOnboarding, settings.identity != nil {
                MainTabView()
            } else {
                OnboardingFlow()
            }
        }
        .background(Theme.background)
        .task {
            settings.engineReachable = await settings.repository.health()
        }
    }
}

struct MainTabView: View {
    var body: some View {
        TabView {
            WatchlistView()
                .tabItem { Label("Watchlist", systemImage: "list.star") }
            SearchView()
                .tabItem { Label("Search", systemImage: "magnifyingglass") }
            SettingsView()
                .tabItem { Label("Settings", systemImage: "slider.horizontal.3") }
        }
    }
}
