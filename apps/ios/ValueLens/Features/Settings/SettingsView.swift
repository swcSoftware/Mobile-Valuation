import SwiftUI

struct SettingsView: View {
    @Environment(AppSettings.self) private var settings
    @State private var name = ""
    @State private var email = ""
    @State private var showResetConfirm = false

    var body: some View {
        @Bindable var settings = settings
        NavigationStack {
            Form {
                Section {
                    TextField("Full name", text: $name)
                    TextField("Email", text: $email).keyboardType(.emailAddress).textInputAutocapitalization(.never).autocorrectionDisabled()
                    Button("Save identity") {
                        settings.identity = SECIdentity(fullName: name, email: email)
                    }
                    .disabled(!SECIdentity(fullName: name, email: email).isValid)
                } header: { Text("SEC EDGAR identity") } footer: {
                    Text("Stored in Keychain. Sent as `User-Agent: \(settings.identity?.userAgent ?? "Name email")` on every EDGAR request.")
                }

                Section {
                    Toggle(isOn: $settings.expertMode) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Expert Mode")
                            Text(settings.expertMode ? "Showing the full math" : "Showing the basics").font(.caption).foregroundStyle(Theme.textSecondary)
                        }
                    }
                    NavigationLink("Glossary — what these terms mean") { GlossaryView() }
                } header: { Text("Presentation") } footer: {
                    Text("Off: plain-language values and health facts. On: every formula, XBRL tag and assumption, with expand-all.")
                }

                Section {
                    if let r = settings.rates {
                        LabeledContent("FRED rates as of \(r.asOf)", value: "AAA \(Fmt.pct(r.aaaYieldPct, decimals: 2)) · 10-yr \(Fmt.pct(r.treasury10YPct, decimals: 2))")
                    } else {
                        Text("Rates not loaded yet").foregroundStyle(Theme.textSecondary)
                    }
                    Button("Refresh rates") { Task { await settings.refreshRates() } }
                } header: { Text("Data sources") } footer: {
                    Text("SEC EDGAR filings are fetched directly from this device using your identity. Interest rates come from a daily published FRED snapshot; market prices and beta from a public quote feed.")
                }

                Section {
                    RateField("AAA corporate yield %", value: $settings.overrides.aaaYieldPct, placeholder: "live / 5.0")
                    RateField("10-yr Treasury %", value: $settings.overrides.treasury10yPct, placeholder: "live / 4.2")
                    RateField("Hurdle rate %", value: $settings.overrides.hurdleRatePct, placeholder: "10")
                    RateField("Equity risk premium %", value: $settings.overrides.equityRiskPremiumPct, placeholder: "5")
                    RateField("Beta", value: $settings.overrides.beta, placeholder: "1.0")
                    RateField("Terminal growth %", value: $settings.overrides.terminalGrowthPct, placeholder: "2.5")
                    RateField("Exit multiple", value: $settings.overrides.exitMultiple, placeholder: "15")
                    Button("Reset to live / defaults") { settings.overrides = .none }
                } header: { Text("Valuation assumptions") } footer: {
                    Text("Blank = use FRED live rates (when the engine has a key) or engine defaults. Every report shows which values were used.")
                }

                Section {
                    Button("Replay guided onboarding") { settings.resetOnboarding() }
                    Button("Clear identity & restart", role: .destructive) { showResetConfirm = true }
                } header: { Text("Onboarding") }

                Section("About") {
                    LabeledContent("Version", value: "0.1.0 alpha")
                    Text("Value investing only: Graham, Buffett, Munger. No technical analysis. Data from SEC EDGAR 10-K and 10-Q XBRL filings. Not financial advice.")
                        .font(.caption).foregroundStyle(Theme.textSecondary)
                }
            }
            .scrollContentBackground(.hidden)
            .background(Theme.background)
            .navigationTitle("Settings")
            .onAppear { name = settings.identity?.fullName ?? ""; email = settings.identity?.email ?? "" }
            .confirmationDialog("This removes your SEC identity from Keychain and restarts onboarding.", isPresented: $showResetConfirm, titleVisibility: .visible) {
                Button("Clear & restart", role: .destructive) { settings.identity = nil; settings.resetOnboarding() }
            }
        }
    }
}

private struct RateField: View {
    let label: String
    @Binding var value: Double?
    let placeholder: String
    init(_ label: String, value: Binding<Double?>, placeholder: String) { self.label = label; _value = value; self.placeholder = placeholder }
    var body: some View {
        HStack {
            Text(label)
            Spacer()
            TextField(placeholder, value: $value, format: .number)
                .keyboardType(.decimalPad).multilineTextAlignment(.trailing).frame(width: 110)
        }
    }
}
