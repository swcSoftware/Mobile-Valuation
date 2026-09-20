import SwiftUI
import UIKit

struct CompanyDetailView: View {
    @Environment(AppSettings.self) private var settings
    @Environment(WatchlistStore.self) private var watchlist
    @Environment(AppRouter.self) private var router
    @State private var vm: CompanyViewModel
    @State private var showPriceSheet = false
    @State private var exportItem: ExportItem?
    @State private var showExportMenu = false

    init(company: CompanyRef) {
        _vm = State(initialValue: CompanyViewModel(company: company))
    }

    var body: some View {
        ScrollView {
            if let r = vm.report {
                content(r)
            } else if vm.loading {
                VStack(spacing: 12) {
                    ProgressView()
                    Text("Pulling 10-K / 10-Q filings from SEC EDGAR…").font(.caption).foregroundStyle(Theme.textSecondary)
                }
                .frame(maxWidth: .infinity).padding(.top, 120)
            } else if let e = vm.error {
                ContentUnavailableView {
                    Label(vm.errorTitle ?? "Couldn't value \(vm.company.ticker)", systemImage: vm.errorIcon)
                } description: {
                    Text(e)
                } actions: {
                    Button("Retry") { Task { await vm.load(using: settings.repository, overrides: settings.overrides) } }
                    if vm.needsIdentity { Button("Open Settings") { router.tab = .settings } }
                }
            }
        }
        .background(Theme.background)
        .navigationTitle(vm.company.ticker)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItemGroup(placement: .topBarTrailing) {
                if let r = vm.report {
                    Button {
                        if watchlist.contains(r.company.ticker) { watchlist.remove(ticker: r.company.ticker) } else { watchlist.add(r) }
                    } label: {
                        Image(systemName: watchlist.contains(r.company.ticker) ? "star.fill" : "star")
                    }
                    Menu {
                        Button { export(.pdf, r) } label: { Label("PDF valuation dossier", systemImage: "doc.richtext") }
                        Button { export(.cardSquare, r) } label: { Label("Share card (1:1)", systemImage: "square") }
                        Button { export(.cardWide, r) } label: { Label("Share card (16:9)", systemImage: "rectangle") }
                    } label: { Image(systemName: "square.and.arrow.up") }
                }
            }
        }
        .task { if vm.report == nil { await vm.load(using: settings.repository, overrides: settings.overrides) } }
        .onChange(of: vm.model) { old, new in
            guard let r = vm.report else { return }
            let before = r.result(for: old).marginOfSafety.verdict, after = r.result(for: new).marginOfSafety.verdict
            Haptics.verdictChanged(before != after, improved: Verdict.rank(after) < Verdict.rank(before))
        }
        .sheet(isPresented: $showPriceSheet) { priceSheet }
        .sheet(item: $exportItem) { item in ShareSheet(items: [item.url]) }
    }

    private func export(_ kind: ExportKind, _ r: ValuationReport) {
        Task { @MainActor in
            if let url = await Exporter.export(kind, report: r, model: vm.model) { exportItem = ExportItem(url: url) }
        }
    }

    // MARK: - content
    @ViewBuilder
    private func content(_ r: ValuationReport) -> some View {
        let result = r.result(for: vm.model)
        VStack(alignment: .leading, spacing: 16) {
            header(r)
            ModelToggle(model: $vm.model)
            Card { MarginOfSafetyView(mos: result.marginOfSafety) }
            if result.marginOfSafety.verdict == .insufficientData {
                InsufficientDataCard(report: r, result: result)
            }

            SectionHeader(title: result.name, subtitle: "Tap any metric for the formula and SEC line items")
            Card {
                VStack(spacing: 14) {
                    MetricRow(metric: result.composite, emphasize: true)
                    Divider().overlay(Theme.border)
                    ForEach(result.metrics.filter { $0.key != "fcff_projection" }) { m in
                        MetricRow(metric: m)
                    }
                }
            }

            SectionHeader(title: "Balance sheet & quality", subtitle: "Trailing twelve months")
            Card { SnapshotGrid(snapshot: r.snapshot) }

            SectionHeader(title: "10-K history", subtitle: "\(r.history.count) fiscal years from annual filings")
            Card { HistoryCharts(history: r.history) }
            Card(padding: 0) { HistoryTable(history: r.history) }

            SectionHeader(title: "Growth (CAGR)")
            Card { GrowthGrid(growth: r.growth) }

            SectionHeader(title: "Assumptions", subtitle: "Rates: \(r.assumptions.rateSource)")
            Card { AssumptionsGrid(a: r.assumptions) }

            if !r.warnings.isEmpty {
                SectionHeader(title: "Data notes")
                Card {
                    VStack(alignment: .leading, spacing: 6) {
                        ForEach(r.warnings, id: \.self) { w in
                            Label(w, systemImage: "info.circle").font(.caption).foregroundStyle(Theme.textSecondary)
                        }
                    }
                }
            }
            Text(r.disclaimer).font(.caption2).foregroundStyle(Theme.textTertiary).multilineTextAlignment(.center).frame(maxWidth: .infinity)
        }
        .padding(16)
    }

    private func header(_ r: ValuationReport) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(r.company.name).font(.vlTitle).foregroundStyle(Theme.textPrimary)
            HStack(alignment: .firstTextBaseline, spacing: 12) {
                Button { showPriceSheet = true } label: {
                    HStack(spacing: 4) {
                        Text(Fmt.money(r.price)).font(.vlDisplay).foregroundStyle(Theme.price)
                        Image(systemName: "pencil.circle").font(.caption).foregroundStyle(Theme.textTertiary)
                    }
                }.buttonStyle(.plain)
                VStack(alignment: .leading, spacing: 0) {
                    Text(r.quote.map { "\($0.source) · \(Fmt.shortDate($0.asOf))" } ?? "no quote").font(.caption2).foregroundStyle(Theme.textTertiary)
                    Text("CIK " + String(r.company.cik)).font(.caption2).foregroundStyle(Theme.textTertiary)
                }
            }
        }
    }

    private var priceSheet: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("Market price", value: $vm.priceOverride, format: .number)
                        .keyboardType(.decimalPad)
                } header: { Text("Override market price") } footer: {
                    Text("SEC filings carry no prices. The default comes from a free quote provider; override it here to test a hypothetical entry price.")
                }
                Button("Clear override") { vm.priceOverride = nil }
            }
            .navigationTitle("Market price")
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Apply") {
                        showPriceSheet = false
                        Task { await vm.load(using: settings.repository, overrides: settings.overrides) }
                    }
                }
            }
        }
        .presentationDetents([.medium])
    }
}

// MARK: - sub-views

struct SnapshotGrid: View {
    let snapshot: [String: SourcedValue]
    private let items: [(String, String, (Double) -> String)] = [
        ("equity", "Book value", { Fmt.compact($0) }),
        ("book_value_per_share", "Book / share", { Fmt.money($0) }),
        ("cash", "Cash", { Fmt.compact($0) }),
        ("total_debt", "Total debt", { Fmt.compact($0) }),
        ("current_ratio", "Current ratio", { Fmt.number($0) + "×" }),
        ("debt_to_equity", "Debt / equity", { Fmt.number($0) + "×" }),
        ("roe", "ROE", { Fmt.pct($0, isFraction: true) }),
        ("roic", "ROIC", { Fmt.pct($0, isFraction: true) }),
        ("operating_margin", "Op. margin", { Fmt.pct($0, isFraction: true) }),
        ("net_margin", "Net margin", { Fmt.pct($0, isFraction: true) }),
    ]
    var body: some View {
        LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 12) {
            ForEach(items, id: \.0) { key, label, fmt in
                VStack(alignment: .leading, spacing: 2) {
                    Text(label).font(.caption).foregroundStyle(Theme.textSecondary)
                    Text(snapshot[key].map { fmt($0.value) } ?? "—").font(.body.monospacedDigit()).foregroundStyle(Theme.textPrimary)
                    if let s = snapshot[key] { Text(s.tag.replacingOccurrences(of: "us-gaap:", with: "")).font(.caption2).foregroundStyle(Theme.textTertiary).lineLimit(1) }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
    }
}

struct GrowthGrid: View {
    let growth: [String: GrowthEntry]
    private let labels = [("revenue", "Revenue"), ("net_income", "Net income"), ("eps_diluted", "EPS"), ("equity", "Book value"), ("fcf", "Free cash flow"), ("owner_earnings", "Owner earnings")]
    var body: some View {
        VStack(spacing: 8) {
            HStack {
                Text("").frame(maxWidth: .infinity, alignment: .leading)
                Text("5-yr").font(.caption).foregroundStyle(Theme.textSecondary).frame(width: 70, alignment: .trailing)
                Text("Full").font(.caption).foregroundStyle(Theme.textSecondary).frame(width: 70, alignment: .trailing)
            }
            ForEach(labels, id: \.0) { key, label in
                let g = growth[key]
                HStack {
                    Text(label).foregroundStyle(Theme.textPrimary).frame(maxWidth: .infinity, alignment: .leading)
                    Text(Fmt.pct(g?.fiveYearCagr, isFraction: true)).font(.body.monospacedDigit()).foregroundStyle(color(g?.fiveYearCagr)).frame(width: 70, alignment: .trailing)
                    Text(Fmt.pct(g?.fullPeriodCagr, isFraction: true)).font(.body.monospacedDigit()).foregroundStyle(color(g?.fullPeriodCagr)).frame(width: 70, alignment: .trailing)
                }
            }
        }
    }
    private func color(_ v: Double?) -> Color { guard let v else { return Theme.textTertiary }; return v >= 0 ? Theme.textPrimary : Theme.danger }
}

struct AssumptionsGrid: View {
    let a: Assumptions
    var body: some View {
        let rows: [(String, String)] = [
            ("AAA corporate yield (Y)", Fmt.pct(a.aaaYieldPct, decimals: 2)),
            ("10-yr Treasury (rf)", Fmt.pct(a.treasury10YPct, decimals: 2)),
            ("Hurdle rate", Fmt.pct(a.hurdleRatePct)),
            ("Equity risk premium", Fmt.pct(a.equityRiskPremiumPct)),
            ("Beta", Fmt.number(a.beta)),
            ("Terminal growth", Fmt.pct(a.terminalGrowthPct)),
            ("Exit multiple", Fmt.number(a.exitMultiple, decimals: 0) + "×"),
            ("Projection years", "\(a.projectionYears)"),
            ("Growth cap", Fmt.pct(a.maxGrowthPct, decimals: 0)),
        ]
        VStack(spacing: 8) {
            ForEach(rows, id: \.0) { k, v in
                HStack { Text(k).foregroundStyle(Theme.textSecondary); Spacer(); Text(v).font(.body.monospacedDigit()).foregroundStyle(Theme.textPrimary) }
            }
        }
    }
}


/// Explains *why* a per-share value is missing instead of showing a bare dash (ISSUES #1).
struct InsufficientDataCard: View {
    let report: ValuationReport
    let result: ModelResult

    private var reason: String {
        let w = report.warnings.joined(separator: " ").lowercased()
        if report.price == nil && result.intrinsicValuePerShare != nil {
            return "No market quote was available, so the margin of safety can't be computed. Tap the price to enter one manually."
        }
        if w.contains("multi-class") || w.contains("no usable share count") {
            return "This company reports per-share data by share class (e.g. Class A / Class B), which SEC's company-facts feed doesn't expose. Total-company figures below are still valid; per-share values will arrive with the Sprint 2 XBRL upgrade."
        }
        if w.contains("missing concepts") && w.contains("eps") {
            return "The latest 10-K doesn't tag diluted EPS in a way the engine recognizes yet, so the Graham formulas can't run. Owner-earnings totals are still shown."
        }
        return "The filings don't carry enough of the line items this model needs. See the data notes at the bottom for the specifics."
    }

    var body: some View {
        Card {
            VStack(alignment: .leading, spacing: 8) {
                Label("Why is the intrinsic value missing?", systemImage: "questionmark.circle")
                    .font(.vlHeadline).foregroundStyle(Theme.textPrimary)
                Text(reason).font(.caption).foregroundStyle(Theme.textSecondary).fixedSize(horizontal: false, vertical: true)
            }
        }
    }
}

enum Haptics {
    /// Notification haptic when flipping models changes the verdict; a light tap otherwise.
    static func verdictChanged(_ changed: Bool, improved: Bool) {
        if changed {
            UINotificationFeedbackGenerator().notificationOccurred(improved ? .success : .warning)
        } else {
            UIImpactFeedbackGenerator(style: .light).impactOccurred()
        }
    }
}

extension Verdict {
    /// Lower is better; used to decide success vs warning haptic.
    static func rank(_ v: Verdict) -> Int {
        switch v {
        case .deepValue: 0
        case .withinMargin: 1
        case .thinMargin: 2
        case .aboveIntrinsic: 3
        case .insufficientData: 4
        }
    }
}
