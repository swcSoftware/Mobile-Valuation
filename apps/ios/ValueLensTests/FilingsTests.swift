import XCTest
@testable import ValueLens

/// Sprint 9: the filings behind the numbers — how each is described, and that the temporary copies go away.
@MainActor
final class FilingsTests: XCTestCase {
    private func doc(_ periods: [String], report: String? = "2024-12-31") -> FilingDocument {
        FilingDocument(filing: FilingUsed(accession: "0000021344-25-000011", form: "10-K", filed: "2025-02-20", periods: periods, concepts: ["revenue"]),
                       documentUrl: "https://www.sec.gov/x.htm", indexUrl: "https://www.sec.gov/i.htm", reportDate: report)
    }

    func testAFilingIsTitledByItsOwnPeriodAndSaysWhichYearsItSupplied() {
        // The KO case: the FY2024 10-K supplied only FY2022–FY2023, because the FY2025 10-K restated FY2024.
        XCTAssertEqual(doc(["FY2022", "FY2023"]).title, "10-K for the period ending 2024-12-31")
        XCTAssertEqual(doc(["FY2022", "FY2023"]).supplied, "FY2022–FY2023")
        XCTAssertEqual(doc(["FY2025", "TTM"]).supplied, "FY2025 and TTM")
        XCTAssertEqual(doc(["TTM"]).supplied, "TTM")
        XCTAssertEqual(doc(["FY2023"], report: nil).title, "10-K filed 2025-02-20", "no report date: say when it was filed, not a guessed period")
    }

    func testLeavingDeletesTheCompanysFilesAndLaunchSweepsLeftovers() throws {
        let fm = FileManager.default
        let dir = FilingsDownloader.root.appending(path: "ZZTEST", directoryHint: .isDirectory)
        try fm.createDirectory(at: dir, withIntermediateDirectories: true)
        try Data("x".utf8).write(to: dir.appending(path: "a.htm"))
        FilingsDownloader.sweep()
        XCTAssertFalse(fm.fileExists(atPath: FilingsDownloader.root.path), "a launch removes anything a crash left behind")
        XCTAssertTrue(FilingsDownloader.root.path.hasPrefix(fm.temporaryDirectory.path), "filings only ever live in the temporary directory")
    }
}
