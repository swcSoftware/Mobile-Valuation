import SwiftUI

struct WatchlistView: View {
    @Environment(WatchlistStore.self) private var watchlist
    @Environment(AppSettings.self) private var settings
    @Environment(AppRouter.self) private var router
    @State private var refreshing = false

    var body: some View {
        @Bindable var router = router
        NavigationStack(path: $router.watchlistPath) {
            Group {
                if watchlist.entries.isEmpty {
                    ContentUnavailableView {
                        Label("No companies yet", systemImage: "list.star")
                    } description: {
                        Text("Search for a ticker to value it from its SEC filings.")
                    } actions: {
                        Button("Search") { router.tab = .search }
                    }
                } else {
                    List {
                        Section {
                            ForEach(watchlist.entries) { entry in
                                NavigationLink(value: entry.company) {
                                    WatchlistRow(entry: entry, expert: settings.expertMode)
                                }
                                .listRowBackground(Theme.surface)
                            }
                            .onDelete { watchlist.remove(at: $0) }
                        } footer: {
                            LastUpdatedStamp(date: watchlist.lastUpdated, refreshing: refreshing)
                        }
                    }
                    .listStyle(.insetGrouped)
                    .scrollContentBackground(.hidden)
                    .refreshable { await refresh() }
                }
            }
            .background(Theme.background)
            .navigationTitle("Watchlist")
            .navigationDestination(for: CompanyRef.self) { company in
                CompanyDetailView(company: company)
            }
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    HStack(spacing: 12) {
                        if refreshing { ProgressView().controlSize(.small) }
                        if !watchlist.entries.isEmpty {
                            Button { Task { await refresh() } } label: { Image(systemName: "arrow.clockwise") }
                                .accessibilityLabel("Refresh watchlist")
                        }
                    }
                }
            }
            .onChange(of: router.pendingTicker, initial: true) { _, ticker in
                guard let ticker else { return }
                router.pendingTicker = nil
                let known = watchlist.entries.first { $0.company.ticker == ticker }?.company
                router.watchlistPath = [known ?? CompanyRef(ticker: ticker, cik: 0, name: ticker)]
            }
        }
    }

    private func refresh() async {
        refreshing = true
        defer { refreshing = false }
        await watchlist.refreshAll(using: settings.repository, overrides: settings.overrides)
    }
}

struct LastUpdatedStamp: View {
    let date: Date?
    var refreshing = false
    var body: some View {
        HStack(spacing: 6) {
            Image(systemName: "clock").font(.caption2)
            if refreshing {
                Text("Refreshing…")
            } else if let date {
                Text("Updated \(date, format: .relative(presentation: .named))")
            } else {
                Text("Not refreshed yet")
            }
        }
        .font(.caption).foregroundStyle(Theme.textTertiary)
        .frame(maxWidth: .infinity, alignment: .center)
        .padding(.top, 4)
    }
}

struct WatchlistRow: View {
    let entry: WatchlistEntry
    var expert = true
    var body: some View {
        let mos = entry.lastReport?.modelA.marginOfSafety
        HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 3) {
                Text(entry.company.ticker).font(.vlHeadline).foregroundStyle(Theme.textPrimary)
                Text(entry.company.name).font(.caption).foregroundStyle(Theme.textSecondary).lineLimit(1)
            }
            Spacer()
            VStack(alignment: .trailing, spacing: 3) {
                HStack(spacing: 6) {
                    Text(Fmt.money(mos?.marketPrice)).font(.body.monospacedDigit()).foregroundStyle(Theme.price)
                    if expert {
                        Text("vs").font(.caption2).foregroundStyle(Theme.textTertiary)
                        Text(Fmt.money(mos?.intrinsicValue)).font(.body.monospacedDigit()).foregroundStyle(Theme.value)
                    }
                }
                if let v = mos?.verdict {
                    Text(v.title).font(.caption2).foregroundStyle(Theme.verdictColor(v))
                }
            }
        }
        .padding(.vertical, 4)
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(entry.company.name), price \(Fmt.money(mos?.marketPrice)), intrinsic value \(Fmt.money(mos?.intrinsicValue)), \(mos?.verdict.title ?? "not loaded")")
    }
}
