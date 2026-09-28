import XCTest
@testable import ValueLens

/// Sprint 9: the filings behind the numbers — how each is described, and that the temporary copies go away.
@MainActor
final class FilingsTests: XCTestCase {
    // How a filing is titled and which years it "supplied" is worded by the core; see FilingsTest there.

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
