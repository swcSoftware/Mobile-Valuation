package com.swcsoftware.valuelens.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.swcsoftware.valuelens.AppState
import com.swcsoftware.valuelens.domain.RateOverrides
import com.swcsoftware.valuelens.domain.SecIdentity
import com.swcsoftware.valuelens.ui.components.Card
import com.swcsoftware.valuelens.ui.components.PrimaryButton
import com.swcsoftware.valuelens.ui.components.SectionHeader
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import com.swcsoftware.valuelens.ui.components.AccentSwatches
import com.swcsoftware.valuelens.ui.theme.LocalPalette
import com.swcsoftware.valuelens.ui.theme.ThemePreference
import com.swcsoftware.valuelens.ui.theme.VL
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(state: AppState, onReplayOnboarding: () -> Unit, onGlossary: () -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(state.identity?.fullName ?: "") }
    var email by remember { mutableStateOf(state.identity?.email ?: "") }
    Column(Modifier.fillMaxSize().background(VL.background).verticalScroll(rememberScrollState()).padding(20.dp, 28.dp, 20.dp, 40.dp)) {
        Text("Settings", style = MaterialTheme.typography.displaySmall, color = VL.textPrimary)

        SectionHeader("SEC EDGAR identity", "Stored encrypted. Sent as User-Agent on every EDGAR request.")
        Card(Modifier.padding(top = 8.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("Full name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(email, { email = it }, label = { Text("Email") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
            Spacer(Modifier.height(10.dp))
            PrimaryButton("Save identity", enabled = SecIdentity(name, email).isValid) { state.saveIdentity(SecIdentity(name.trim(), email.trim())) }
        }

        SectionHeader("Presentation", "Off: plain-language values and health facts. On: every formula, XBRL tag and assumption, with expand-all.")
        Card(Modifier.padding(top = 8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("Expert Mode", color = VL.textPrimary); Text(if (state.expertMode) "Showing the full math" else "Showing the basics", style = MaterialTheme.typography.bodySmall, color = VL.textSecondary) }
                androidx.compose.material3.Switch(checked = state.expertMode, onCheckedChange = { state.updateExpertMode(it) })
            }
            TextButton(onGlossary) { Text("Glossary — what these terms mean", color = VL.accent) }
        }

        SectionHeader("Layout", "Two presentations of the same numbers. Each preview is the real layout, drawn on a bundled Apple sample.")
        Card { LayoutPicker(state.layoutStyle, state.investorLens) { state.updateLayoutStyle(it) } }

        SectionHeader("Investor lens", "Sets the bar the report card grades a business against. It never changes a valuation — fair value, margin of safety and the verdict are the same numbers whichever you pick.")
        Card { LensPicker(state.investorLens, { state.updateInvestorLens(it) }) }

        SectionHeader(
            "Appearance",
            "Market price stays amber and fair value stays mint in every theme — that pair is how you read a valuation, so it is never restyled. An accent that would be unreadable on the current background is not offered.",
        )
        Card {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                ThemePreference.entries.forEachIndexed { i, p ->
                    SegmentedButton(
                        selected = state.themePreference == p,
                        onClick = { state.updateThemePreference(p) },
                        shape = SegmentedButtonDefaults.itemShape(i, ThemePreference.entries.size),
                    ) { Text(p.displayName) }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Accent color", color = VL.textPrimary, modifier = Modifier.weight(1f))
                if (state.accentArgb != null) {
                    TextButton({ state.updateAccent(null) }) { Text("Reset", color = VL.accent) }
                }
            }
            AccentSwatches(
                selected = state.accentArgb,
                isDark = LocalPalette.current.isDark,
                onPick = { state.updateAccent(it) },
            )
        }

        SectionHeader("Data sources", "SEC EDGAR filings are fetched directly from this device using your identity. Interest rates come from a daily published FRED snapshot.")
        Card(Modifier.padding(top = 8.dp)) {
            val r = state.rates
            Text(if (r != null) "FRED rates as of ${r.as_of}: AAA ${r.aaa_yield_pct}% · 10-yr ${r.treasury_10y_pct}%" else "Rates not loaded yet", color = VL.textPrimary, style = MaterialTheme.typography.bodySmall)
            TextButton({ scope.launch { state.refreshRates() } }) { Text("Refresh rates", color = VL.accent) }
        }

        SectionHeader("Valuation assumptions", "Blank = engine live/default value. Every report shows which values were used.")
        Card(Modifier.padding(top = 8.dp)) {
            val o = state.overrides
            RateField("AAA corporate yield %", o.aaaYieldPct) { state.updateOverrides(o.copy(aaaYieldPct = it)) }
            RateField("10-yr Treasury %", o.treasury10yPct) { state.updateOverrides(o.copy(treasury10yPct = it)) }
            RateField("Hurdle rate %", o.hurdleRatePct) { state.updateOverrides(o.copy(hurdleRatePct = it)) }
            RateField("Equity risk premium %", o.equityRiskPremiumPct) { state.updateOverrides(o.copy(equityRiskPremiumPct = it)) }
            RateField("Beta", o.beta) { state.updateOverrides(o.copy(beta = it)) }
            RateField("Terminal growth %", o.terminalGrowthPct) { state.updateOverrides(o.copy(terminalGrowthPct = it)) }
            RateField("Exit multiple", o.exitMultiple) { state.updateOverrides(o.copy(exitMultiple = it)) }
            TextButton({ state.updateOverrides(RateOverrides.NONE) }) { Text("Reset to live / defaults", color = VL.textSecondary) }
        }

        SectionHeader("Onboarding")
        Card(Modifier.padding(top = 8.dp)) {
            TextButton({ state.updateOnboarded(false); onReplayOnboarding() }) { Text("Replay guided onboarding", color = VL.accent) }
            TextButton({ state.clearIdentity(); onReplayOnboarding() }) { Text("Clear identity & restart", color = VL.danger) }
        }

        SectionHeader("About")
        Card(Modifier.padding(top = 8.dp)) {
            Text("Version 0.1.0 alpha", color = VL.textPrimary)
            Text("Value investing only: Graham, Buffett, Munger. No technical analysis. Data from SEC EDGAR 10-K and 10-Q XBRL filings. Not financial advice.", style = MaterialTheme.typography.bodySmall, color = VL.textSecondary, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun RateField(label: String, value: Double?, onChange: (Double?) -> Unit) {
    var text by remember(value) { mutableStateOf(value?.toString() ?: "") }
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = VL.textPrimary, modifier = Modifier.weight(1f))
        OutlinedTextField(text, { t -> text = t; onChange(t.trim().toDoubleOrNull()) }, singleLine = true, modifier = Modifier.width(120.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), placeholder = { Text("live", color = VL.textTertiary) })
    }
}
