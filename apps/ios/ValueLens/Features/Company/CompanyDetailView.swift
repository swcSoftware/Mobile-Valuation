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
    @State private var showMath = false
    @State private var expandAll: Bool? = nil
    @State private var expandVersion = 0
    @State private var explain: ExplainSummary?
    @State private var reportCard: ReportCardSummary?

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
                        Button { openIssue(r) } label: { Label("Report a data problem", systemImage: "exclamationmark.bubble") }
                    Divider()
                    Button { export(.pdf, r) } label: { Label("PDF valuation dossier", systemImage: "doc.richtext") }
                        Button { export(.cardSquare, r) } label: { Label("Share card (1:1)", systemImage: "square") }
                        Button { export(.cardWide, r) } label: { Label("Share card (16:9)", systemImage: "rectangle") }
                    } label: { Image(systemName: "square.and.arrow.up") }
                }
            }
        }
        .task { if vm.report == nil { await vm.load(using: settings.repository, overrides: settings.overrides) } }
        .task(id: vm.report) { if let r = vm.report { explain = await settings.repository.explain(r) } }
        // Re-grade when the lens changes; the valuation itself is untouched, so nothing reloads.
        .task(id: ReportCardKey(report: vm.report, lens: settings.investorLens, layout: settings.layoutStyle)) {
            guard settings.layoutStyle == .reportCard, let r = vm.report else { return }
            reportCard = await settings.repository.reportCard(r, lens: settings.investorLens)
        }
        .onChange(of: vm.model) { old, new in
            guard let r = vm.report else { return }
            let before = r.result(for: old).marginOfSafety.verdict, after = r.result(for: new).marginOfSafety.verdict
            Haptics.verdictChanged(before != after, improved: Verdict.rank(after) < Verdict.rank(before))
        }
        .sheet(isPresented: $showPriceSheet) { priceSheet }
        .sheet(item: $exportItem) { item in ShareSheet(items: [item.url]) }
    }

    /// Opens a prefilled GitHub issue in the browser. Nothing is sent until the user submits it.
    private func openIssue(_ r: ValuationReport) {
        if let url = settings.repository.coverageIssueURL(for: r) { UIApplication.shared.open(url) }
    }

    private func export(_ kind: ExportKind, _ r: ValuationReport) {
        Task { @MainActor in
            if let url = await Exporter.export(kind, report: r, model: vm.model) { exportItem = ExportItem(url: url) }
        }
    }

    // MARK: - the layout seam

    /// The container owns loading, errors, the toolbar and the sheets; a *layout* owns the reading
    /// of the report. Which one runs is the user's choice (Sprint 5 Track A) and nothing else in
    /// this file needs to know which it is.
    @ViewBuilder
    private func content(_ r: ValuationReport) -> some View {
        let ctx = LayoutContext(
            report: r,
            result: r.result(for: vm.model),
            explain: explain,
            expert: settings.expertMode || showMath,
            showingMathTemporarily: !settings.expertMode,
            model: $vm.model,
            editPrice: { showPriceSheet = true },
            setShowMath: { showMath = $0 },
            expandAll: $expandAll,
            expandVersion: $expandVersion,
            reportIssue: { openIssue(r) },
            reportCard: reportCard,
            swapLens: { settings.investorLens = settings.investorLens.toggled }
        )
        switch settings.layoutStyle {
        case .classic:
            ClassicLayout(ctx: ctx)
        case .reportCard:
            ReportCardLayout(ctx: ctx)
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
                    if let s = snapshot[key] { Text(Labels.tag(s.tag)).font(.caption2).foregroundStyle(Theme.textTertiary).lineLimit(1) }
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
    var betaSource: String? = nil
    var body: some View {
        let rows: [(String, String)] = [
            ("AAA corporate yield (Y)", Fmt.pct(a.aaaYieldPct, decimals: 2)),
            ("10-yr Treasury (rf)", Fmt.pct(a.treasury10YPct, decimals: 2)),
            ("Hurdle rate", Fmt.pct(a.hurdleRatePct)),
            ("Equity risk premium", Fmt.pct(a.equityRiskPremiumPct)),
            ("Beta (\(betaSource ?? "?"))", Fmt.number(a.beta)),
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
/// What the report card's grades depend on — and, deliberately, nothing that would re-run a valuation.
private struct ReportCardKey: Equatable {
    let report: ValuationReport?
    let lens: InvestorLens
    let layout: LayoutStyle
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
