import SwiftUI

struct OnboardingFlow: View {
    @Environment(AppSettings.self) private var settings
    @State private var path: [Step] = []

    /// Identity → investor lens → guided valuation. The lens question sits after the SEC identity
    /// (which the app cannot work without) and before the first valuation, so the first report card
    /// a user sees is already graded against the bar they chose (Sprint 5 Track D).
    enum Step: Hashable { case lens, caseStudy }

    var body: some View {
        @Bindable var settings = settings
        NavigationStack(path: $path) {
            IdentityView { identity in
                settings.identity = identity
                path.append(.lens)
            }
            .navigationDestination(for: Step.self) { step in
                switch step {
                case .lens:
                    LensChoiceView(selection: $settings.investorLens,
                                   lenses: settings.repository.lenses()) { path.append(.caseStudy) }
                case .caseStudy: CaseStudyView()
                }
            }
        }
    }
}

/// Step 2: "what kind of investor are you?" — asked once, changeable any time in Settings.
struct LensChoiceView: View {
    @Binding var selection: InvestorLens
    let lenses: [LensInfo]
    let onContinue: () -> Void

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                VStack(alignment: .leading, spacing: 10) {
                    Text("What kind of investor are you?")
                        .font(.vlTitle).foregroundStyle(Theme.textPrimary)
                        .fixedSize(horizontal: false, vertical: true)
                    // It must say plainly that this changes no valuation (Track D).
                    Text("This sets the bar we grade a business against — nothing else. Fair value, margin of safety and the verdict are worked out the same way whichever you pick, and you can change this any time in Settings.")
                        .font(.vlBody).foregroundStyle(Theme.textSecondary)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .padding(.top, 12)

                LensPicker(selection: $selection, lenses: lenses, style: .cards) { _ in onContinue() }

                if let example {
                    Text(example)
                        .font(.caption).foregroundStyle(Theme.textTertiary)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
            .padding(20)
        }
        .background(Theme.background)
        .navigationTitle("Your lens")
        .navigationBarTitleDisplayMode(.inline)
    }

    /// Built from the live rules the core reports, so the example cannot drift from the grading.
    private var example: String? {
        // Quoted as-is: the rule opens with the grade letter, so any case change would corrupt it
        // ("A at 10%" is a grade; "a at 10%" is nonsense).
        let rules = lenses.map { "\($0.name): \($0.growthRule)" }
        guard rules.count == 2 else { return nil }
        return "Example — revenue growth for an operating company. \(rules[0]). \(rules[1]). The company's growth rate is the same number either way."
    }
}

/// Step 1: capture Full Name + Email — SEC requires this identity on every EDGAR request.
struct IdentityView: View {
    @Environment(AppSettings.self) private var settings
    let onContinue: (SECIdentity) -> Void
    @State private var name = ""
    @State private var email = ""

    private var identity: SECIdentity { SECIdentity(fullName: name, email: email) }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 28) {
                VStack(alignment: .leading, spacing: 8) {
                    Text("ValueLens").font(.vlDisplay).foregroundStyle(Theme.textPrimary)
                    Text("Intrinsic value from primary SEC disclosures.\nNo charts. No momentum. No noise.")
                        .font(.vlBody).foregroundStyle(Theme.textSecondary)
                }
                .padding(.top, 40)

                Card {
                    VStack(alignment: .leading, spacing: 14) {
                        Label("Identify yourself to SEC EDGAR", systemImage: "person.text.rectangle")
                            .font(.vlHeadline).foregroundStyle(Theme.textPrimary)
                        Text("SEC's fair-access policy requires every request to declare who is asking. Your name and email are stored in the device Keychain and sent only as the EDGAR User-Agent header.")
                            .font(.caption).foregroundStyle(Theme.textSecondary)
                        TextField("Full name", text: $name)
                            .textContentType(.name)
                            .textFieldStyle(.roundedBorder)
                        TextField("Email address", text: $email)
                            .textContentType(.emailAddress)
                            .keyboardType(.emailAddress)
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                            .textFieldStyle(.roundedBorder)
                        Text("User-Agent: \(identity.isValid ? identity.userAgent : "Jane Doe jane@example.com")")
                            .font(.vlMono).foregroundStyle(Theme.textTertiary)
                    }
                }

                PrimaryButton(title: "Continue to guided valuation", enabled: identity.isValid) {
                    onContinue(identity)
                }
                Text("Generated by ValueLens • Built on Pure Fundamental SEC EDGAR Disclosures • Not Financial Advice")
                    .font(.caption2).foregroundStyle(Theme.textTertiary).multilineTextAlignment(.center)
                    .frame(maxWidth: .infinity)
            }
            .padding(20)
        }
        .background(Theme.background)
        .onAppear {
            if let id = settings.identity { name = id.fullName; email = id.email }
        }
    }
}
