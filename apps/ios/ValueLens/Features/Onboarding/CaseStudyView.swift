import SwiftUI

/// Step 2: interactive case study. The user picks a blue chip and is walked through a live
/// valuation: raw 10-K numbers → Model A → toggle to Model B → margin of safety.
struct CaseStudyView: View {
    @Environment(AppSettings.self) private var settings
    @Environment(WatchlistStore.self) private var watchlist

    private let choices: [(String, String)] = [("AAPL", "Apple"), ("KO", "Coca-Cola"), ("MSFT", "Microsoft")]
    @State private var selected: String? = nil
    @State private var report: ValuationReport?
    @State private var loading = false
    @State private var error: String?
    @State private var page = 0
    @State private var query = ""
    @State private var results: [CompanyRef] = []
    @State private var searchTask: Task<Void, Never>?

    var body: some View {
        Group {
            if let report {
                walkthrough(report)
            } else {
                picker
            }
        }
        .background(Theme.background)
        .navigationTitle("Guided valuation")
        .navigationBarTitleDisplayMode(.inline)
    }

    // MARK: pick a company
    private var picker: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                Text("Pick a company you know.").font(.vlTitle).foregroundStyle(Theme.textPrimary)
                Text("We'll pull its 10-K and 10-Q filings straight from SEC EDGAR and value it together, one step at a time.")
                    .font(.vlBody).foregroundStyle(Theme.textSecondary)
                ForEach(choices, id: \.0) { ticker, name in
                    Button {
                        selected = ticker
                        Task { await load(ticker) }
                    } label: {
                        Card {
                            HStack {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(ticker).font(.vlHeadline).foregroundStyle(Theme.textPrimary)
                                    Text(name).font(.caption).foregroundStyle(Theme.textSecondary)
                                }
                                Spacer()
                                if loading && selected == ticker { ProgressView() } else { Image(systemName: "chevron.right").foregroundStyle(Theme.textTertiary) }
                            }
                        }
                    }
                    .buttonStyle(.plain)
                    .disabled(loading)
                }
                do {
                    Text("…or any other SEC filer").font(.caption).foregroundStyle(Theme.textTertiary).padding(.top, 4)
                    TextField("Ticker or company name", text: $query)
                        .textFieldStyle(.roundedBorder)
                        .textInputAutocapitalization(.characters)
                        .autocorrectionDisabled()
                        .onChange(of: query) { _, q in
                            searchTask?.cancel()
                            guard q.count >= 1 else { results = []; return }
                            searchTask = Task {
                                try? await Task.sleep(for: .milliseconds(300))
                                guard !Task.isCancelled else { return }
                                results = (try? await settings.repository.search(query: q)) ?? []
                            }
                        }
                    ForEach(results.prefix(6)) { c in
                        Button {
                            selected = c.ticker
                            Task { await load(c.ticker) }
                        } label: {
                            Card(padding: 12) {
                                HStack {
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(c.ticker).font(.vlHeadline).foregroundStyle(Theme.textPrimary)
                                        Text(c.name).font(.caption).foregroundStyle(Theme.textSecondary).lineLimit(1)
                                    }
                                    Spacer()
                                    if loading && selected == c.ticker { ProgressView() } else { Image(systemName: "chevron.right").foregroundStyle(Theme.textTertiary) }
                                }
                            }
                        }
                        .buttonStyle(.plain)
                        .disabled(loading)
                    }
                }
                if let error {
                    Text(error).font(.caption).foregroundStyle(Theme.danger)
                }
            }
            .padding(20)
        }
    }

    private func load(_ ticker: String) async {
        loading = true; error = nil
        defer { loading = false }
        do {
            report = try await settings.repository.valuation(ticker: ticker, priceOverride: nil, overrides: settings.overrides)
        } catch {
            self.error = error.localizedDescription
        }
    }

    // MARK: walkthrough pages
    private func walkthrough(_ r: ValuationReport) -> some View {
        VStack(spacing: 0) {
            TabView(selection: $page) {
                CaseStudyPage(step: 1, title: "Raw 10-K numbers", blurb: "Everything starts from audited statements. These are the trailing-twelve-month figures, each traceable to an XBRL tag in a specific SEC filing.") {
                    RawNumbersStep(report: r)
                }.tag(0)
                CaseStudyPage(step: 2, title: "Traditional value", blurb: "Graham's formula turns earnings and growth into a price. Buffett's owner earnings ask what cash the business really throws off. Tap any row to see the math.") {
                    ModelStep(result: r.modelA, keys: ["graham_g", "graham_classic", "graham_revised", "owner_earnings", "owner_earnings_per_share", "oe_value_hurdle"])
                }.tag(1)
                CaseStudyPage(step: 3, title: "Flip to modern fair value", blurb: "Same filings, different lens: discount projected free cash flow at the company's cost of capital, and check whether returns on capital beat that cost.") {
                    ToggleStep(report: r)
                }.tag(2)
                CaseStudyPage(step: 4, title: "Margin of safety", blurb: "Graham's central idea: only buy well below what the business is worth. The bar shows the market price against intrinsic value with 25% and 50% discount lines.") {
                    MoSStep(report: r)
                }.tag(3)
            }
            .tabViewStyle(.page(indexDisplayMode: .always))
            .indexViewStyle(.page(backgroundDisplayMode: .always))

            HStack {
                if page > 0 { Button("Back") { withAnimation { page -= 1 } } }
                Spacer()
                if page < 3 {
                    Button("Next") { withAnimation { page += 1 } }.buttonStyle(.borderedProminent)
                } else {
                    Button("Finish & add to watchlist") {
                        watchlist.add(r)
                        settings.hasCompletedOnboarding = true
                    }.buttonStyle(.borderedProminent)
                }
            }
            .padding(20)
            .background(Theme.surface)
        }
    }
}

private struct CaseStudyPage<Content: View>: View {
    let step: Int
    let title: String
    let blurb: String
    @ViewBuilder let content: Content
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                Text("STEP \(step) OF 4").font(.caption.weight(.semibold)).tracking(1.2).foregroundStyle(Theme.value)
                Text(title).font(.vlTitle).foregroundStyle(Theme.textPrimary)
                Text(blurb).font(.vlBody).foregroundStyle(Theme.textSecondary)
                content
            }
            .padding(20)
            .padding(.bottom, 56)  // clear the page-index dots
        }
    }
}

private struct RawNumbersStep: View {
    let report: ValuationReport
    private let keys: [(String, String)] = [("revenue", "Revenue"), ("net_income", "Net income"), ("eps_diluted", "Diluted EPS"), ("cfo", "Cash from operations"), ("fcf", "Free cash flow"), ("equity", "Shareholders' equity"), ("cash", "Cash & equivalents"), ("total_debt", "Total debt")]
    var body: some View {
        Card {
            VStack(spacing: 10) {
                ForEach(keys, id: \.0) { k, label in
                    if let sv = report.snapshot[k] {
                        VStack(alignment: .leading, spacing: 4) {
                            HStack {
                                Text(label).foregroundStyle(Theme.textPrimary)
                                Spacer()
                                Text(k == "eps_diluted" ? Fmt.money(sv.value) : Fmt.compact(sv.value))
                                    .font(.body.monospacedDigit()).foregroundStyle(Theme.textPrimary)
                            }
                            Text("\(sv.tag) · \(sv.form) · \(sv.periodEnd)").font(.caption2).foregroundStyle(Theme.textTertiary).lineLimit(1)
                        }
                        if k != keys.last?.0 { Divider().overlay(Theme.border) }
                    }
                }
            }
        }
    }
}

private struct ModelStep: View {
    let result: ModelResult
    let keys: [String]
    var body: some View {
        Card {
            VStack(spacing: 14) {
                ForEach(keys, id: \.self) { k in
                    if let m = result.metric(k) { MetricRow(metric: m) }
                }
            }
        }
    }
}

private struct ToggleStep: View {
    let report: ValuationReport
    @State private var model: ValuationModel = .traditional
    var body: some View {
        VStack(spacing: 14) {
            ModelToggle(model: $model)
            Card {
                let r = report.result(for: model)
                VStack(alignment: .leading, spacing: 12) {
                    Text(r.name).font(.caption).foregroundStyle(Theme.textSecondary)
                    HStack(alignment: .firstTextBaseline) {
                        Text("Intrinsic value").foregroundStyle(Theme.textPrimary)
                        Spacer()
                        Text(Fmt.money(r.intrinsicValuePerShare)).font(.vlNumber).foregroundStyle(Theme.value)
                    }
                    Divider().overlay(Theme.border)
                    ForEach((model == .traditional ? ["graham_revised", "oe_value_hurdle"] : ["wacc", "dcf_perpetuity", "roic"]), id: \.self) { k in
                        if let m = r.metric(k) { MetricRow(metric: m) }
                    }
                }
            }
            Text("Try the toggle. Notice how the two lenses disagree — that disagreement is information, not error.")
                .font(.caption).foregroundStyle(Theme.textTertiary)
        }
    }
}

private struct MoSStep: View {
    let report: ValuationReport
    var body: some View {
        VStack(spacing: 14) {
            Card { MarginOfSafetyView(mos: report.modelA.marginOfSafety) }
            Card {
                VStack(alignment: .leading, spacing: 8) {
                    Text("How to read it").font(.vlHeadline).foregroundStyle(Theme.textPrimary)
                    Label("Amber dot: what the market charges today.", systemImage: "circle.fill").foregroundStyle(Theme.price)
                    Label("Mint line: what the filings say the business is worth.", systemImage: "line.diagonal").foregroundStyle(Theme.value)
                    Text("The two ticks mark 25% and 50% discounts. Value investors wait for the amber dot to fall left of a tick — the deeper, the safer.")
                        .font(.caption).foregroundStyle(Theme.textSecondary)
                    Text(report.modelA.marginOfSafety.formula).font(.vlMono).foregroundStyle(Theme.info)
                }
            }
        }
    }
}

struct ModelToggle: View {
    @Binding var model: ValuationModel
    var expert = true
    /// Sector-specific blurb from the core's explain summary (basic mode).
    var blurbOverride: String? = nil
    var body: some View {
        VStack(spacing: 6) {
            Picker("Model", selection: $model) {
                ForEach(ValuationModel.allCases) { m in
                    Text(expert ? m.rawValue : m.friendlyName).tag(m)
                }
            }
            .pickerStyle(.segmented)
            Text(expert ? model.subtitle : (blurbOverride?.isEmpty == false ? blurbOverride! : model.friendlyBlurb)).font(.caption).foregroundStyle(Theme.textSecondary)
                .multilineTextAlignment(.center).fixedSize(horizontal: false, vertical: true)
        }
    }
}
