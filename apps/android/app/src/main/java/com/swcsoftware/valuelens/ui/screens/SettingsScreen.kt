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
import com.swcsoftware.valuelens.data.AppPrefs
import com.swcsoftware.valuelens.domain.RateOverrides
import com.swcsoftware.valuelens.domain.SecIdentity
import com.swcsoftware.valuelens.ui.components.Card
import com.swcsoftware.valuelens.ui.components.PrimaryButton
import com.swcsoftware.valuelens.ui.components.SectionHeader
import com.swcsoftware.valuelens.ui.theme.VL
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(state: AppState, onReplayOnboarding: () -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(state.identity?.fullName ?: "") }
    var email by remember { mutableStateOf(state.identity?.email ?: "") }
    var url by remember { mutableStateOf(state.engineUrl) }
    Column(Modifier.fillMaxSize().background(VL.background).verticalScroll(rememberScrollState()).padding(20.dp, 28.dp, 20.dp, 40.dp)) {
        Text("Settings", style = MaterialTheme.typography.displaySmall, color = VL.textPrimary)

        SectionHeader("SEC EDGAR identity", "Stored encrypted. Sent as User-Agent on every EDGAR request.")
        Card(Modifier.padding(top = 8.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("Full name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(email, { email = it }, label = { Text("Email") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
            Spacer(Modifier.height(10.dp))
            PrimaryButton("Save identity", enabled = SecIdentity(name, email).isValid) { state.saveIdentity(SecIdentity(name.trim(), email.trim())) }
        }

        SectionHeader("Valuation engine", "Emulator: 10.0.2.2. Physical phone on the same Wi-Fi: run scripts/serve-lan.sh on your Mac and pick the LAN address below.")
        Card(Modifier.padding(top = 8.dp)) {
            OutlinedTextField(url, { url = it }, label = { Text("Engine URL") }, singleLine = true, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (state.engineReachable) "Connected" else "Offline (sample data)", color = if (state.engineReachable) VL.value else VL.warning, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                TextButton({ state.updateEngineUrl(url); scope.launch { state.checkEngine() } }) { Text("Save & test", color = VL.value) }
            }
            state.engineLanAddresses.forEach { ip ->
                TextButton({ url = "http://$ip:8000"; state.updateEngineUrl(url); scope.launch { state.checkEngine() } }) { Text("Use LAN address http://$ip:8000", color = VL.value) }
            }
            TextButton({ url = AppPrefs.DEFAULT_ENGINE_URL; state.updateEngineUrl(url); scope.launch { state.checkEngine() } }) { Text("Reset to default", color = VL.textSecondary) }
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
            TextButton({ state.updateOnboarded(false); onReplayOnboarding() }) { Text("Replay guided onboarding", color = VL.value) }
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
