import Foundation
import Observation

struct WatchlistEntry: Codable, Identifiable, Hashable {
    let company: CompanyRef
    var lastReport: ValuationReport?
    var addedAt: Date
    var id: String { company.ticker }
}

/// Local-only persistence of tracked tickers (JSON file in Application Support).
@Observable
final class WatchlistStore {
    private(set) var entries: [WatchlistEntry] = []
    private let fileURL: URL

    init() {
        let dir = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        fileURL = dir.appending(path: "watchlist.json")
        if let data = try? Data(contentsOf: fileURL), let saved = try? JSONDecoder().decode([WatchlistEntry].self, from: data) {
            entries = saved
        }
    }

    func contains(_ ticker: String) -> Bool { entries.contains { $0.company.ticker == ticker } }

    func add(_ report: ValuationReport) {
        if let i = entries.firstIndex(where: { $0.company.ticker == report.company.ticker }) {
            entries[i].lastReport = report
        } else {
            entries.append(WatchlistEntry(company: report.company, lastReport: report, addedAt: .now))
        }
        persist()
    }

    func update(_ report: ValuationReport) {
        guard let i = entries.firstIndex(where: { $0.company.ticker == report.company.ticker }) else { return }
        entries[i].lastReport = report
        persist()
    }

    func remove(ticker: String) {
        entries.removeAll { $0.company.ticker == ticker }
        persist()
    }

    func remove(at offsets: IndexSet) {
        entries.remove(atOffsets: offsets)
        persist()
    }

    private func persist() {
        try? JSONEncoder().encode(entries).write(to: fileURL, options: .atomic)
    }
}
