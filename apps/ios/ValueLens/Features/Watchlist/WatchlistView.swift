import SwiftUI

struct WatchlistView: View {
    @Environment(WatchlistStore.self) private var watchlist
    @Environment(AppSettings.self) private var settings
    @State private var refreshing = false

    var body: some View {
        NavigationStack {
            Group {
                if watchlist.entries.isEmpty {
                    ContentUnavailableView {
                        Label("No companies yet", systemImage: "list.star")
                    } description: {
                        Text("Search for a ticker to value it from its SEC filings.")
                    }
                } else {
                    List {
                        ForEach(watchlist.entries) { entry in
                            NavigationLink(value: entry.company) {
                                WatchlistRow(entry: entry)
                            }
                            .listRowBackground(Theme.surface)
                        }
                        .onDelete { watchlist.remove(at: $0) }
                    }
                    .listStyle(.insetGrouped)
                    .scrollContentBackground(.hidden)
                    .refreshable { await refreshAll() }
                }
            }
            .background(Theme.background)
            .navigationTitle("Watchlist")
            .navigationDestination(for: CompanyRef.self) { company in
                CompanyDetailView(company: company)
            }
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    if !settings.engineReachable {
                        Image(systemName: "wifi.slash").foregroundStyle(Theme.warning)
                            .help("Valuation engine offline")
                    }
                }
            }
        }
    }

    private func refreshAll() async {
        for entry in watchlist.entries {
            if let r = try? await settings.repository.valuation(ticker: entry.company.ticker, priceOverride: nil, overrides: settings.overrides) {
                watchlist.update(r)
            }
        }
    }
}

struct WatchlistRow: View {
    let entry: WatchlistEntry
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
                    Text("vs").font(.caption2).foregroundStyle(Theme.textTertiary)
                    Text(Fmt.money(mos?.intrinsicValue)).font(.body.monospacedDigit()).foregroundStyle(Theme.value)
                }
                if let v = mos?.verdict {
                    Text(v.title).font(.caption2).foregroundStyle(Theme.verdictColor(v))
                }
            }
        }
        .padding(.vertical, 4)
    }
}
