import Foundation
import Observation

/// A private note the user wrote about one company (Sprint 9).
struct CompanyNote: Codable, Identifiable, Hashable, Sendable {
    let id: UUID
    let ticker: String
    /// The company's display name when the note was written, so an export or list reads well offline.
    var companyName: String
    var text: String
    let createdAt: Date
    var updatedAt: Date
}

/// On-device notes, keyed by ticker. A JSON file in Application Support with complete file protection
/// (unreadable while the phone is locked); nothing is sent anywhere. Removing a company from the
/// watchlist does not delete its notes.
@Observable
final class NotesStore {
    private(set) var notes: [CompanyNote] = []
    private let fileURL: URL

    /// `fileURL` is injectable so tests use a temporary file instead of the app's notes.
    init(fileURL: URL? = nil) {
        if let fileURL {
            self.fileURL = fileURL
        } else {
            let dir = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
            self.fileURL = dir.appending(path: "notes.json")
        }
        let decoder = JSONDecoder(); decoder.dateDecodingStrategy = .iso8601
        if let data = try? Data(contentsOf: self.fileURL), let saved = try? decoder.decode([CompanyNote].self, from: data) {
            notes = saved
        }
    }

    /// A company's notes, most recently edited first.
    func notes(for ticker: String) -> [CompanyNote] {
        let t = ticker.uppercased()
        return notes.filter { $0.ticker == t }.sorted { $0.updatedAt > $1.updatedAt }
    }

    /// Adds a note; blank text adds nothing.
    @discardableResult
    func add(ticker: String, companyName: String, text: String, now: Date = .now) -> CompanyNote? {
        let body = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !body.isEmpty else { return nil }
        let note = CompanyNote(id: UUID(), ticker: ticker.uppercased(), companyName: companyName, text: body, createdAt: now, updatedAt: now)
        notes.append(note)
        persist()
        return note
    }

    /// Replaces a note's text. Clearing the text deletes the note, as in Notes.
    func update(id: UUID, text: String, now: Date = .now) {
        guard let i = notes.firstIndex(where: { $0.id == id }) else { return }
        let body = text.trimmingCharacters(in: .whitespacesAndNewlines)
        if body.isEmpty { notes.remove(at: i) } else if body != notes[i].text {
            notes[i].text = body
            notes[i].updatedAt = now
        } else { return }
        persist()
    }

    func delete(id: UUID) {
        notes.removeAll { $0.id == id }
        persist()
    }

    private func persist() {
        let encoder = JSONEncoder(); encoder.dateEncodingStrategy = .iso8601
        guard let data = try? encoder.encode(notes) else { return }
        try? data.write(to: fileURL, options: [.atomic, .completeFileProtection])
    }
}
