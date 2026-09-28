import XCTest
import PDFKit
@testable import ValueLens

/// Sprint 9: private notes per company — stored on the device, shown on the company page, and appended
/// to the PDF dossier only when the user chooses the version with notes.
final class NotesTests: XCTestCase {
    private var url: URL!

    override func setUp() {
        url = FileManager.default.temporaryDirectory.appending(path: "notes-\(UUID().uuidString).json")
    }
    override func tearDown() { try? FileManager.default.removeItem(at: url) }

    func testNotesBelongToTheirCompanyNewestFirst() {
        let store = NotesStore(fileURL: url)
        let t0 = Date(timeIntervalSince1970: 1_790_000_000)
        store.add(ticker: "mcd", companyName: "McDonald's", text: "First thought", now: t0)
        store.add(ticker: "MCD", companyName: "McDonald's", text: "Second thought", now: t0.addingTimeInterval(60))
        store.add(ticker: "KO", companyName: "Coca-Cola", text: "Different company", now: t0)
        XCTAssertEqual(store.notes(for: "MCD").map(\.text), ["Second thought", "First thought"])
        XCTAssertEqual(store.notes(for: "ko").count, 1, "tickers are matched without regard to case")
        XCTAssertTrue(store.notes(for: "AAPL").isEmpty)
    }

    func testBlankTextIsNotANote() {
        let store = NotesStore(fileURL: url)
        XCTAssertNil(store.add(ticker: "MCD", companyName: "McDonald's", text: "   \n "))
        XCTAssertTrue(store.notes.isEmpty)
    }

    func testEditingMovesANoteToTheTopAndClearingItDeletesIt() throws {
        let store = NotesStore(fileURL: url)
        let t0 = Date(timeIntervalSince1970: 1_790_000_000)
        let a = try XCTUnwrap(store.add(ticker: "MCD", companyName: "McDonald's", text: "A", now: t0))
        store.add(ticker: "MCD", companyName: "McDonald's", text: "B", now: t0.addingTimeInterval(60))
        store.update(id: a.id, text: "A, revised", now: t0.addingTimeInterval(120))
        XCTAssertEqual(store.notes(for: "MCD").map(\.text), ["A, revised", "B"])
        XCTAssertEqual(store.notes(for: "MCD").first?.createdAt, t0, "editing keeps when it was written")
        store.update(id: a.id, text: "  ")
        XCTAssertEqual(store.notes(for: "MCD").map(\.text), ["B"])
    }

    func testNotesSurviveARestartAndDeleteIsPermanent() throws {
        let store = NotesStore(fileURL: url)
        let n = try XCTUnwrap(store.add(ticker: "MCD", companyName: "McDonald's", text: "Keep me"))
        store.add(ticker: "MCD", companyName: "McDonald's", text: "Delete me")
        store.delete(id: try XCTUnwrap(store.notes(for: "MCD").first { $0.text == "Delete me" }).id)
        let reopened = NotesStore(fileURL: url)
        XCTAssertEqual(reopened.notes(for: "MCD").map(\.id), [n.id])
        XCTAssertEqual(reopened.notes.first?.companyName, "McDonald's")
    }

    @MainActor
    func testTheDossierCarriesNotesOnlyWhenAsked() async throws {
        let fixture = try XCTUnwrap(Bundle(for: Self.self).url(forResource: "MCD", withExtension: "json"))
        let report = try JSONDecoder.engine.decode(ValuationReport.self, from: Data(contentsOf: fixture))
        let store = NotesStore(fileURL: url)
        store.add(ticker: "MCD", companyName: "McDonald's", text: "Franchise model, watch the debt")

        let withURL = await Exporter.export(.pdf, report: report, model: .traditional, notes: store.notes(for: "MCD"))
        let withoutURL = await Exporter.export(.pdf, report: report, model: .traditional)
        let with = try XCTUnwrap(withURL), without = try XCTUnwrap(withoutURL)
        defer { try? FileManager.default.removeItem(at: with); try? FileManager.default.removeItem(at: without) }

        XCTAssertNotEqual(with, without, "the two versions must not overwrite each other")
        let withText = try XCTUnwrap(PDFDocument(url: with)?.string)
        let withoutText = try XCTUnwrap(PDFDocument(url: without)?.string)
        // Section headings are letter-spaced in the PDF ("A S S U M P T I O N S"), so anchor on body text.
        let marker = "Written by you in Alpha"
        XCTAssertTrue(withText.contains("Franchise model, watch the debt"))
        XCTAssertTrue(withText.contains(marker))
        XCTAssertFalse(withoutText.contains("Franchise model"))
        XCTAssertFalse(withoutText.contains(marker))
        // The notes come after the report (its assumptions) and before the closing disclaimer.
        let notesAt = try XCTUnwrap(withText.range(of: marker)).lowerBound
        XCTAssertLessThan(try XCTUnwrap(withText.range(of: "AAA yield")).lowerBound, notesAt)
        XCTAssertLessThan(notesAt, try XCTUnwrap(withText.range(of: "Not Financial Advice")).lowerBound)
    }
}
