package com.swcsoftware.valuelens.ui.layouts

import com.swcsoftware.valuelens.ui.displayName
import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swcsoftware.valuelens.AppState
import com.swcsoftware.valuelens.domain.CompanyRef
import com.swcsoftware.valuelens.domain.EngineException
import com.swcsoftware.valuelens.domain.HistoryPoint
import com.swcsoftware.valuelens.domain.ModelResult
import com.swcsoftware.valuelens.domain.ValuationModel
import com.swcsoftware.valuelens.domain.ValuationReport
import com.swcsoftware.valuelens.domain.Verdict
import com.swcsoftware.valuelens.domain.price
import com.swcsoftware.valuelens.domain.result
import com.swcsoftware.valuelens.domain.verdictEnum
import com.swcsoftware.valuelens.core.Coverage
import com.swcsoftware.valuelens.core.Explain
import com.swcsoftware.valuelens.export.Exporter
import com.swcsoftware.valuelens.ui.components.CoverageGapCard
import com.swcsoftware.valuelens.ui.components.DataChecksCard
import com.swcsoftware.valuelens.ui.components.FactTile
import com.swcsoftware.valuelens.ui.Fmt
import com.swcsoftware.valuelens.ui.components.Card
import com.swcsoftware.valuelens.ui.components.MarginOfSafetyView
import com.swcsoftware.valuelens.ui.components.MetricRow
import com.swcsoftware.valuelens.ui.components.ModelToggle
import com.swcsoftware.valuelens.ui.components.SectionHeader
import com.swcsoftware.valuelens.ui.components.ThinDivider
import com.swcsoftware.valuelens.ui.theme.VL
import kotlinx.coroutines.launch
import com.swcsoftware.valuelens.domain.CoverageReport
import com.swcsoftware.valuelens.ui.screens.SnapshotGrid
import com.swcsoftware.valuelens.ui.screens.HistoryChart
import com.swcsoftware.valuelens.ui.screens.HistoryTable
import com.swcsoftware.valuelens.core.ReportCardSummary
import com.swcsoftware.valuelens.ui.LayoutStyle
import com.swcsoftware.valuelens.ui.RequiredComponent

/**
 * "Show me the math" — the formulas, the SEC line items and the filed history.
 *
 * **One shared presentation for every layout** (Sprint 5 Track A): a layout chooses where this sits,
 * never what it says. It has no scroll of its own, so it can sit inside either layout's column.
 * Mirrors apps/ios/ValueLens/Features/Company/Layouts/ExpertBody.swift.
 */
@Composable
fun ExpertBody(ctx: LayoutContext) {
    val r = ctx.report
    val res = ctx.result
    var series by remember { mutableStateOf("earnings") }
    var expandAll by remember { mutableStateOf<Boolean?>(null) }
    var expandVersion by remember { mutableStateOf(0) }

    Column(Modifier.fillMaxWidth()) {
        if (r.company.displayName != r.company.name) {
            // The display form is for reading; the filed name is the evidence.
            Text("Filed with the SEC as ${r.company.name} · CIK ${r.company.cik}", fontSize = 11.sp,
                 fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, color = VL.textTertiary,
                 modifier = Modifier.padding(top = 8.dp))
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            SectionHeader(res.name, "Tap any metric for the formula and SEC line items")
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton({ expandAll = true; expandVersion++ }) { Text("Expand all", color = VL.accent, fontSize = 12.sp) }
            TextButton({ expandAll = false; expandVersion++ }) { Text("Collapse all", color = VL.textSecondary, fontSize = 12.sp) }
        }
        Card {
            MetricRow(res.composite, emphasize = true, expandAll = expandAll, expandVersion = expandVersion); ThinDivider()
            res.metrics.filter { it.key != "fcff_projection" }.forEach { MetricRow(it, expandAll = expandAll, expandVersion = expandVersion) }
        }
        r.provenance["beta_detail"]?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = VL.textTertiary, modifier = Modifier.padding(top = 6.dp)) }

        if (r.shareClasses.isNotEmpty()) {
            SectionHeader("Share classes", "From the filing cover page; ratios from per-class EPS, in ${r.company.ticker} share terms")
            Card(Modifier.padding(top = 8.dp)) {
                r.shareClasses.forEach { c ->
                    Row(Modifier.padding(vertical = 4.dp)) {
                        Text("Class ${c.cls}${c.ticker?.let { " · $it" } ?: " · not traded"}", color = VL.textPrimary, modifier = Modifier.weight(1f))
                        Text(Fmt.number(c.shares, 0), color = VL.textSecondary)
                        Text("  × ${Fmt.number(c.ratioToSearched, if (c.ratioToSearched >= 10) 0 else 3)}", color = VL.textTertiary, fontSize = 12.sp)
                    }
                }
            }
        }

        SectionHeader("Balance sheet & quality", "Trailing twelve months")
        Card(Modifier.padding(top = 8.dp)) { SnapshotGrid(r) }

        SectionHeader("10-K history", "${r.history.size} fiscal years from annual filings")
        Card(Modifier.padding(top = 8.dp)) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf("earnings" to "Earnings", "cash" to "Cash flow", "roic" to "ROIC", "book" to "Book value").forEachIndexed { i, (k, l) ->
                    SegmentedButton(series == k, { series = k }, SegmentedButtonDefaults.itemShape(i, 4),
                        colors = SegmentedButtonDefaults.colors(activeContainerColor = Color(0xFF3A3F48), activeContentColor = VL.textPrimary, inactiveContainerColor = VL.raised, inactiveContentColor = VL.textSecondary)) { Text(l, fontSize = 12.sp) }
                }
            }
            HistoryChart(r.history, series)
        }
        Card(Modifier.padding(top = 12.dp), padding = 0) { HistoryTable(r.history) }

        SectionHeader("Growth (CAGR)")
        Card(Modifier.padding(top = 8.dp)) {
            Row { Spacer(Modifier.weight(1f)); Text("5-yr", color = VL.textSecondary, fontSize = 12.sp, modifier = Modifier.width(70.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End); Text("Full", color = VL.textSecondary, fontSize = 12.sp, modifier = Modifier.width(70.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End) }
            listOf("revenue" to "Revenue", "net_income" to "Net income", "eps_diluted" to "EPS", "equity" to "Book value", "fcf" to "Free cash flow", "owner_earnings" to "Owner earnings").forEach { (k, l) ->
                val g = r.growth[k]
                // Read before the local fun: a nested `fun` is not a composable scope.
                val naColor = VL.textTertiary
                val negColor = VL.danger
                val posColor = VL.textPrimary
                fun c(v: Double?) = if (v == null) naColor else if (v < 0) negColor else posColor
                Row(Modifier.padding(vertical = 3.dp)) { Text(l, color = VL.textPrimary, modifier = Modifier.weight(1f)); Text(Fmt.pct(g?.fiveYearCagr, 1, true), color = c(g?.fiveYearCagr), modifier = Modifier.width(70.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End); Text(Fmt.pct(g?.fullPeriodCagr, 1, true), color = c(g?.fullPeriodCagr), modifier = Modifier.width(70.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End) }
            }
        }

        SectionHeader("Assumptions", "Rates: ${r.assumptions.rateSource} · beta: ${r.provenance["beta"] ?: "?"}")
        Card(Modifier.padding(top = 8.dp)) {
            val a = r.assumptions
            listOf("AAA corporate yield (Y)" to Fmt.pct(a.aaaYieldPct, 2), "10-yr Treasury (rf)" to Fmt.pct(a.treasury10yPct, 2), "Hurdle rate" to Fmt.pct(a.hurdleRatePct), "Equity risk premium" to Fmt.pct(a.equityRiskPremiumPct), "Beta (${r.provenance["beta"] ?: "?"})" to Fmt.number(a.beta), "Terminal growth" to Fmt.pct(a.terminalGrowthPct), "Exit multiple" to "${Fmt.number(a.exitMultiple, 0)}×", "Projection years" to "${a.projectionYears}", "Growth cap" to Fmt.pct(a.maxGrowthPct, 0))
                .forEach { (k, v) -> Row(Modifier.padding(vertical = 3.dp)) { Text(k, color = VL.textSecondary, modifier = Modifier.weight(1f)); Text(v, color = VL.textPrimary) } }
        }
            if (!ctx.expertModeAlwaysOn) TextButton({ ctx.onSetShowMath(false) }, Modifier.fillMaxWidth()) { Text("Hide the math", color = VL.textSecondary) }
    }
}
