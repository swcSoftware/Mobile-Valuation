import SwiftUI
import WebKit

/// "SEC filings behind these numbers" (Sprint 9): every filing the report read a figure from, downloaded when the
/// page opened, readable in the app, and deleted when the page closes. Placed by both layouts; omitted where no
/// downloader is provided (layout tests, the Settings previews).
struct FilingsUsedCard: View {
    @Environment(FilingsDownloader.self) private var downloader: FilingsDownloader?
    @State private var showList = false

    var body: some View {
        if let downloader, !downloader.documents.isEmpty {
            Button { showList = true } label: {
                HStack(alignment: .center, spacing: 12) {
                    Image(systemName: "doc.text.magnifyingglass").font(.title3).foregroundStyle(Theme.accent)
                    VStack(alignment: .leading, spacing: 2) {
                        Text("SEC filings behind these numbers").font(.vlHeadline).foregroundStyle(Theme.textPrimary)
                        Text(Self.summary(downloader)).font(.caption).foregroundStyle(Theme.textSecondary)
                    }
                    Spacer()
                    Image(systemName: "chevron.right").font(.caption.weight(.bold)).foregroundStyle(Theme.textTertiary)
                }
                .padding(16)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Theme.surface, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).stroke(Theme.border))
            }
            .buttonStyle(.plain)
            .sheet(isPresented: $showList) { FilingsListSheet(downloader: downloader) }
        }
    }

    static func summary(_ d: FilingsDownloader) -> String {
        let n = d.documents.count
        let size = ByteCountFormatter.string(fromByteCount: Int64(d.downloadedBytes), countStyle: .file)
        if d.isFinished { return "\(n) filings · downloaded for this visit (\(size)) · deleted when you leave" }
        return "\(n) filings · downloading \(d.downloadedCount) of \(d.downloadableCount)…"
    }
}

private struct FilingsListSheet: View {
    let downloader: FilingsDownloader
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            List {
                Section {
                    ForEach(downloader.documents) { d in
                        NavigationLink { FilingReader(document: d, file: downloader.localFile(d)) } label: { row(d) }
                            .disabled(downloader.localFile(d) == nil && d.documentUrl != nil)
                    }
                } footer: {
                    Text("Every figure Alpha read from SEC EDGAR came from one of these filings. \"Supplied\" is the years Alpha took from each: when a later filing restates a year, Alpha uses the newer figure, so a 10-K often supplies only earlier years. Downloaded when you opened this company; deleted when you leave it.")
                }
            }
            .navigationTitle("Filings used")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } } }
        }
        .tint(Theme.accent)
    }

    private func row(_ d: FilingDocument) -> some View {
        VStack(alignment: .leading, spacing: 3) {
            HStack {
                Text(d.title).font(.subheadline.weight(.semibold))
                Spacer()
                statusLabel(downloader.status[d.id])
            }
            Text("Filed \(d.filing.filed) · supplied \(d.supplied) figures").font(.caption).foregroundStyle(Theme.textSecondary)
            Text(d.filing.accession).font(.caption2).foregroundStyle(Theme.textTertiary)
            Text(d.filing.concepts.map { Labels.concept($0) }.joined(separator: ", ")).font(.caption2).foregroundStyle(Theme.textTertiary).lineLimit(2)
        }
    }

    @ViewBuilder private func statusLabel(_ s: FilingsDownloader.Status?) -> some View {
        switch s {
        case .done(let b): Text(ByteCountFormatter.string(fromByteCount: Int64(b), countStyle: .file)).font(.caption2).foregroundStyle(Theme.textTertiary)
        case .downloading: ProgressView().controlSize(.small)
        case .failed: Text("Couldn't download").font(.caption2).foregroundStyle(Theme.warning)
        case .noDocument: Text("On SEC.gov").font(.caption2).foregroundStyle(Theme.textTertiary)
        default: Text("Waiting").font(.caption2).foregroundStyle(Theme.textTertiary)
        }
    }
}

/// Reads one downloaded filing. The HTML is the local copy; its images and styles load from the filing's folder on
/// sec.gov, so a connection is still needed for those. "Open on SEC.gov" opens the original.
private struct FilingReader: View {
    let document: FilingDocument
    let file: URL?
    @Environment(\.openURL) private var openURL

    var body: some View {
        Group {
            if let file { LocalFilingView(file: file, base: URL(string: document.documentUrl ?? document.indexUrl)) }
            else { ContentUnavailableView("Not downloaded", systemImage: "doc", description: Text("EDGAR's filing list doesn't name this filing's main document. Open it on SEC.gov instead.")) }
        }
        .navigationTitle("\(document.filing.form) · \(document.reportDate ?? document.filing.filed)")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button { if let u = URL(string: document.documentUrl ?? document.indexUrl) { openURL(u) } } label: { Label("Open on SEC.gov", systemImage: "safari") }
            }
        }
    }
}

private struct LocalFilingView: UIViewRepresentable {
    let file: URL
    let base: URL?
    func makeUIView(context: Context) -> WKWebView {
        let web = WKWebView()
        let data = (try? Data(contentsOf: file)) ?? Data()
        let html = String(data: data, encoding: .utf8) ?? String(data: data, encoding: .isoLatin1) ?? ""
        web.loadHTMLString(html, baseURL: base?.deletingLastPathComponent())
        return web
    }
    func updateUIView(_ web: WKWebView, context: Context) {}
}
