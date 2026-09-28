import Foundation
import Observation

/// Downloads every SEC filing behind the company being viewed into temporary files, and deletes them when the
/// page closes (Sprint 9, owner's choice: all of them up front). One at a time, with a pause between requests,
/// under the user's own SEC identity — the fair-access policy asks for a declared User-Agent and ≤ 10 req/s.
/// SEC serves these compressed: a 12 MB 10-K is under 1 MB over the network.
@MainActor
@Observable
final class FilingsDownloader {
    enum Status: Equatable { case waiting, downloading, done(bytes: Int), failed, noDocument }

    private(set) var documents: [FilingDocument] = []
    private(set) var status: [String: Status] = [:]
    private(set) var ticker: String?
    private var task: Task<Void, Never>?

    /// Everything lives under one folder in the app's temporary directory, so a launch can sweep leftovers.
    nonisolated static let root = FileManager.default.temporaryDirectory.appending(path: "filings", directoryHint: .isDirectory)

    func start(_ report: ValuationReport, repository: CoreValuationRepository, userAgent: String?, pause: Duration = .milliseconds(200)) {
        discard()
        let ticker = report.company.ticker
        self.ticker = ticker
        task = Task { [weak self] in
            let docs = await repository.filingDocuments(report)
            guard let self, !Task.isCancelled else { return }
            self.documents = docs
            for d in docs { self.status[d.id] = d.documentUrl == nil ? .noDocument : .waiting }
            let dir = Self.root.appending(path: ticker, directoryHint: .isDirectory)
            try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
            for d in docs {
                guard !Task.isCancelled else { return }
                guard let s = d.documentUrl, let url = URL(string: s), let userAgent else { continue }
                self.status[d.id] = .downloading
                var req = URLRequest(url: url)
                req.setValue(userAgent, forHTTPHeaderField: "User-Agent")
                do {
                    let (tmp, resp) = try await URLSession.shared.download(for: req)
                    guard (resp as? HTTPURLResponse)?.statusCode == 200 else { throw URLError(.badServerResponse) }
                    let dest = self.fileURL(d, in: dir)
                    try? FileManager.default.removeItem(at: dest)
                    try FileManager.default.moveItem(at: tmp, to: dest)
                    let bytes = (try? FileManager.default.attributesOfItem(atPath: dest.path)[.size] as? Int) ?? 0
                    self.status[d.id] = .done(bytes: bytes)
                } catch {
                    if Task.isCancelled { return }
                    self.status[d.id] = .failed
                }
                try? await Task.sleep(for: pause)
            }
        }
    }

    /// The downloaded document for a filing, if it has finished.
    func localFile(_ d: FilingDocument) -> URL? {
        guard case .done = status[d.id], let ticker else { return nil }
        return fileURL(d, in: Self.root.appending(path: ticker, directoryHint: .isDirectory))
    }

    var downloadedCount: Int { status.values.filter { if case .done = $0 { return true }; return false }.count }
    var downloadableCount: Int { documents.filter { $0.documentUrl != nil }.count }
    var downloadedBytes: Int { status.values.reduce(0) { if case .done(let b) = $1 { return $0 + b }; return $0 } }
    var isFinished: Bool { !documents.isEmpty && !status.values.contains { $0 == .waiting || $0 == .downloading } }

    /// Stops any download and deletes this company's files. Called when the company page closes.
    func discard() {
        task?.cancel(); task = nil
        if let ticker { try? FileManager.default.removeItem(at: Self.root.appending(path: ticker, directoryHint: .isDirectory)) }
        documents = []; status = [:]; ticker = nil
    }

    /// Deletes anything a crash or a killed app left behind. Called at launch.
    nonisolated static func sweep() { try? FileManager.default.removeItem(at: root) }

    private func fileURL(_ d: FilingDocument, in dir: URL) -> URL { dir.appending(path: "\(d.filing.accession).htm") }
}
