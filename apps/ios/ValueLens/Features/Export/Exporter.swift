import SwiftUI
import UIKit

enum ExportKind { case pdf, cardSquare, cardWide }

struct ExportItem: Identifiable { let url: URL; var id: URL { url } }

/// Renders SwiftUI views to a PDF (dossier) or PNG (share card) in the app's Documents folder,
/// which is exposed in the Files app via UIFileSharingEnabled / LSSupportsOpeningDocumentsInPlace.
@MainActor
enum Exporter {
    static func export(_ kind: ExportKind, report: ValuationReport, model: ValuationModel) async -> URL? {
        let docs = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        let stamp = ISO8601DateFormatter().string(from: .now).replacingOccurrences(of: ":", with: "-")
        switch kind {
        case .pdf:
            let url = docs.appending(path: "ValueLens-\(report.company.ticker)-\(stamp).pdf")
            return renderPDF(DossierView(report: report, model: model), to: url) ? url : nil
        case .cardSquare:
            let url = docs.appending(path: "ValueLens-\(report.company.ticker)-1x1.png")
            return renderPNG(ShareCardView(report: report, model: model, aspect: .square).frame(width: 1080, height: 1080), to: url) ? url : nil
        case .cardWide:
            let url = docs.appending(path: "ValueLens-\(report.company.ticker)-16x9.png")
            return renderPNG(ShareCardView(report: report, model: model, aspect: .wide).frame(width: 1920, height: 1080), to: url) ? url : nil
        }
    }

    private static func renderPDF<V: View>(_ view: V, to url: URL) -> Bool {
        let renderer = ImageRenderer(content: view.frame(width: 612).background(Color.white).environment(\.colorScheme, .light))
        renderer.proposedSize = ProposedViewSize(width: 612, height: nil)
        var ok = false
        renderer.render { size, draw in
            var box = CGRect(origin: .zero, size: CGSize(width: 612, height: max(size.height, 792)))
            guard let ctx = CGContext(url as CFURL, mediaBox: &box, nil) else { return }
            ctx.beginPDFPage(nil)
            draw(ctx)
            ctx.endPDFPage()
            ctx.closePDF()
            ok = true
        }
        return ok
    }

    private static func renderPNG<V: View>(_ view: V, to url: URL) -> Bool {
        let renderer = ImageRenderer(content: view)
        renderer.scale = 1
        guard let img = renderer.uiImage, let data = img.pngData() else { return false }
        return (try? data.write(to: url, options: .atomic)) != nil
    }
}

/// UIKit share sheet bridge.
struct ShareSheet: UIViewControllerRepresentable {
    let items: [Any]
    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: items, applicationActivities: nil)
    }
    func updateUIViewController(_ vc: UIActivityViewController, context: Context) {}
}
