import SwiftUI

// MARK: - investor lens

/// Value or Growth, with the core's own description of each. Used at onboarding and in Settings,
/// so the two can never describe the lens differently.
struct LensPicker: View {
    @Binding var selection: InvestorLens
    let lenses: [LensInfo]
    /// Onboarding shows each lens as a large card; Settings uses a compact segmented control.
    var style: Style = .compact
    var onChoose: ((InvestorLens) -> Void)? = nil

    enum Style { case compact, cards }

    var body: some View {
        switch style {
        case .compact: compact
        case .cards: cards
        }
    }

    private var compact: some View {
        VStack(alignment: .leading, spacing: 8) {
            Picker("Investor lens", selection: $selection) {
                ForEach(lenses) { Text($0.name).tag($0.lens) }
            }
            .pickerStyle(.segmented)
            if let current = lenses.first(where: { $0.lens == selection }) {
                Text(current.blurb)
                    .font(.caption)
                    .foregroundStyle(Theme.textSecondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }

    private var cards: some View {
        VStack(spacing: 12) {
            ForEach(lenses) { info in
                Button {
                    selection = info.lens
                    onChoose?(info.lens)
                } label: {
                    HStack(alignment: .center, spacing: 12) {
                        VStack(alignment: .leading, spacing: 6) {
                            Text(info.name).font(.vlHeadline).foregroundStyle(Theme.textPrimary)
                            Text(info.blurb).font(.subheadline).foregroundStyle(Theme.textSecondary)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                        Spacer(minLength: 0)
                        Image(systemName: selection == info.lens ? "checkmark.circle.fill" : "chevron.right")
                            .foregroundStyle(selection == info.lens ? Theme.accent : Theme.textTertiary)
                    }
                    .padding(16)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Theme.surface, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                    .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous)
                        .stroke(selection == info.lens ? Theme.accent : Theme.border, lineWidth: selection == info.lens ? 2 : 1))
                }
                .buttonStyle(.plain)
                .accessibilityLabel("\(info.name). \(info.blurb)")
                .accessibilityAddTraits(selection == info.lens ? .isSelected : [])
            }
        }
    }
}

// MARK: - layout

/// The two layouts, each drawn by the layout itself on a bundled sample and scaled down — so what
/// you see here is what you will get, in your current theme and accent, not an illustration of it.
struct LayoutPicker: View {
    @Binding var selection: LayoutStyle
    let lens: InvestorLens

    @State private var sample: ValuationReport?
    @State private var explain: ExplainSummary?
    @State private var card: ReportCardSummary?

    var body: some View {
        HStack(alignment: .top, spacing: 14) {
            ForEach(LayoutStyle.allCases, id: \.self) { style in
                Button { selection = style } label: {
                    VStack(spacing: 8) {
                        thumbnail(style)
                            .overlay(RoundedRectangle(cornerRadius: 10, style: .continuous)
                                .stroke(selection == style ? Theme.accent : Theme.border, lineWidth: selection == style ? 2.5 : 1))
                        HStack(spacing: 4) {
                            if selection == style {
                                Image(systemName: "checkmark.circle.fill").foregroundStyle(Theme.accent)
                            }
                            Text(style.displayName).font(.subheadline.weight(.semibold)).foregroundStyle(Theme.textPrimary)
                        }
                        Text(style.blurb).font(.caption2).foregroundStyle(Theme.textSecondary)
                            .multilineTextAlignment(.center).fixedSize(horizontal: false, vertical: true)
                    }
                    .frame(maxWidth: .infinity)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("\(style.displayName) layout. \(style.blurb)")
                .accessibilityAddTraits(selection == style ? .isSelected : [])
            }
        }
        .padding(.vertical, 6)
        .task(id: lens) { await loadSample() }
    }

    /// Phone-width layout, cropped to its first screen, scaled to fit a thumbnail.
    private static let phoneWidth: CGFloat = 393
    private static let cropHeight: CGFloat = 640
    private static let scale: CGFloat = 0.34

    @ViewBuilder
    private func thumbnail(_ style: LayoutStyle) -> some View {
        let w = Self.phoneWidth * Self.scale, h = Self.cropHeight * Self.scale
        ZStack(alignment: .topLeading) {
            Theme.background
            if let sample {
                layout(style, sample)
                    .frame(width: Self.phoneWidth)
                    .frame(width: Self.phoneWidth, height: Self.cropHeight, alignment: .top)
                    .clipped()
                    .scaleEffect(Self.scale, anchor: .topLeading)
            } else {
                ProgressView().frame(width: w, height: h)
            }
        }
        .frame(width: w, height: h, alignment: .topLeading)
        .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
        .allowsHitTesting(false)
        .accessibilityHidden(true)   // the label below describes it
    }

    @ViewBuilder
    private func layout(_ style: LayoutStyle, _ report: ValuationReport) -> some View {
        let ctx = LayoutContext(
            report: report,
            result: report.result(for: .traditional),
            explain: explain,
            expert: false,
            showingMathTemporarily: false,
            model: .constant(.traditional),
            editPrice: {},
            setShowMath: { _ in },
            expandAll: .constant(nil),
            expandVersion: .constant(0),
            reportIssue: {},
            reportCard: card
        )
        switch style {
        case .classic: ClassicLayout(ctx: ctx)
        case .reportCard: ReportCardLayout(ctx: ctx)
        }
    }

    private func loadSample() async {
        // The bundled Apple sample: always present, needs no network or identity.
        let repo = SampleValuationRepository()
        guard let r = try? await repo.valuation(ticker: "AAPL", priceOverride: nil, overrides: .none) else { return }
        async let e = repo.explain(r)
        async let c = repo.reportCard(r, lens: lens)
        let (ex, rc) = await (e, c)
        sample = r; explain = ex; card = rc
    }
}
