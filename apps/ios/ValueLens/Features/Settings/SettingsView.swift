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
                    TextField("http://127.0.0.1:8000", text: $settings.engineURL).keyboardType(.URL).textInputAutocapitalization(.never).autocorrectionDisabled()
                    HStack {
                        Text("Status")
                        Spacer()
                        Label(settings.engineReachable ? "Connected" : "Offline (sample data)", systemImage: settings.engineReachable ? "checkmark.circle.fill" : "wifi.slash")
                            .foregroundStyle(settings.engineReachable ? Theme.value : Theme.warning).font(.caption)
                    }
                    Button("Test connection") { Task { settings.engineReachable = await settings.repository.health() } }
                } header: { Text("Valuation engine") } footer: {
                    Text("The Python service that ingests EDGAR and runs the models. Run it locally with uvicorn or point this at a hosted instance.")
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
