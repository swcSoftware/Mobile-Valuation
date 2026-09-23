package com.swcsoftware.valuelens.ui.screens

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
import com.swcsoftware.valuelens.core.Grading
import com.swcsoftware.valuelens.core.Lens
import com.swcsoftware.valuelens.ui.LayoutStyle
import com.swcsoftware.valuelens.ui.layouts.ClassicLayout
import com.swcsoftware.valuelens.ui.layouts.LayoutContext
import com.swcsoftware.valuelens.ui.layouts.ReportCardLayout
import com.swcsoftware.valuelens.ui.Fmt
import com.swcsoftware.valuelens.ui.components.Card
import com.swcsoftware.valuelens.ui.components.MarginOfSafetyView
import com.swcsoftware.valuelens.ui.components.MetricRow
import com.swcsoftware.valuelens.ui.components.ModelToggle
import com.swcsoftware.valuelens.ui.components.SectionHeader
import com.swcsoftware.valuelens.ui.components.ThinDivider
import com.swcsoftware.valuelens.ui.theme.VL
import kotlinx.coroutines.launch

@Composable
fun CompanyDetailScreen(state: AppState, company: CompanyRef, onBack: () -> Unit, onOpenSettings: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    var report by remember { mutableStateOf<ValuationReport?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<EngineException?>(null) }
    var model by remember { mutableStateOf(ValuationModel.A) }
    var priceOverride by remember { mutableStateOf<Double?>(null) }
    var showPrice by remember { mutableStateOf(false) }
    var showExport by remember { mutableStateOf(false) }
    var series by remember { mutableStateOf("earnings") }
    var showMath by remember { mutableStateOf(false) }
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    var expandAll by remember { mutableStateOf<Boolean?>(null) }
    var expandVersion by remember { mutableStateOf(0) }

    fun load() {
        loading = true; error = null
        scope.launch {
            runCatching { state.repository.valuation(company.ticker, priceOverride, state.overrides) }
                .onSuccess { report = it; if (state.inWatchlist(it.company.ticker)) state.updateWatchlist(it) }
                .onFailure { error = it as? EngineException ?: EngineException.Server(0, it.message ?: "Unknown error") }
            loading = false
        }
    }
    LaunchedEffect(company.ticker) { load() }

    Column(Modifier.fillMaxSize().background(VL.background)) {
        Row(Modifier.fillMaxWidth().background(VL.surface).padding(8.dp, 12.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onBack) { Text("‹ Back", color = VL.value) }
            Text(company.ticker, fontWeight = FontWeight.Bold, color = VL.textPrimary, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            report?.let { r ->
                val starred = state.inWatchlist(r.company.ticker)
                TextButton({ if (starred) state.removeFromWatchlist(r.company.ticker) else state.addToWatchlist(r) }) { Text(if (starred) "★" else "☆", color = VL.value, fontSize = 20.sp) }
                Box {
                    TextButton({ showExport = true }) { Text("⇪", color = VL.value, fontSize = 18.sp) }
                    DropdownMenu(showExport, { showExport = false }) {
                        DropdownMenuItem({ Text("PDF valuation dossier") }, { showExport = false; Exporter.sharePdf(context, r, model) })
                        DropdownMenuItem({ Text("Share card (1:1)") }, { showExport = false; Exporter.shareCard(context, r, model, square = true) })
                        DropdownMenuItem({ Text("Share card (16:9)") }, { showExport = false; Exporter.shareCard(context, r, model, square = false) })
                    }
                }
            }
        }
        val r = report
        when {
            r != null -> {
                // The screen owns loading, errors, the toolbar and the dialogs; a *layout* owns
                // the reading of the report, and which one runs is the user's choice
                // (Sprint 5 Track A). Nothing else here needs to know which it is.
                val ctx = LayoutContext(
                    report = r,
                    result = r.result(model),
                    expert = state.expertMode || showMath,
                    expertModeAlwaysOn = state.expertMode,
                    model = model,
                    onModel = { new ->
                        val before = r.result(model).marginOfSafety.verdictEnum
                        val after = r.result(new).marginOfSafety.verdictEnum
                        haptics.performHapticFeedback(
                            if (before != after) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove
                        )
                        model = new
                    },
                    onEditPrice = { showPrice = true },
                    onSetShowMath = { showMath = it },
                    onReportIssue = { cov -> runCatching { uriHandler.openUri(Coverage.issueUrl(cov)) } },
                    // Grading is pure and cheap; re-run it when the report or the lens changes, and
                    // never re-run the valuation for a lens change.
                    reportCard = remember(r, state.investorLens) { Grading.reportCard(r, state.investorLens) },
                    onSwapLens = {
                        state.updateInvestorLens(if (state.investorLens == Lens.VALUE) Lens.GROWTH else Lens.VALUE)
                    },
                )
                when (state.layoutStyle) {
                    LayoutStyle.CLASSIC -> ClassicLayout(ctx)
                    LayoutStyle.REPORT_CARD -> ReportCardLayout(ctx)
                }
            }
            loading -> Column(Modifier.fillMaxWidth().padding(top = 120.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(); Text("Pulling 10-K / 10-Q filings from SEC EDGAR…", style = MaterialTheme.typography.bodySmall, color = VL.textSecondary, modifier = Modifier.padding(top = 12.dp))
            }
            else -> Column(Modifier.fillMaxWidth().padding(32.dp, 80.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(error?.title ?: "Couldn't value ${company.ticker}", style = MaterialTheme.typography.titleMedium, color = VL.textPrimary)
                Text(error?.message ?: "", color = VL.textSecondary, modifier = Modifier.padding(top = 8.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                TextButton({ load() }) { Text("Retry", color = VL.value) }
                if (error is EngineException.InvalidIdentity) TextButton(onOpenSettings) { Text("Open Settings", color = VL.value) }
            }
        }
    }

    if (showPrice) {
        var text by remember { mutableStateOf(priceOverride?.toString() ?: "") }
        AlertDialog(onDismissRequest = { showPrice = false }, title = { Text("Market price") },
            text = { Column { Text("SEC filings carry no prices. Override the quote to test a hypothetical entry price.", style = MaterialTheme.typography.bodySmall); OutlinedTextField(text, { text = it }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.padding(top = 8.dp)) } },
            confirmButton = { TextButton({ priceOverride = text.trim().toDoubleOrNull(); showPrice = false; load() }) { Text("Apply") } },
            dismissButton = { TextButton({ priceOverride = null; showPrice = false; load() }) { Text("Clear") } })
    }
}

@Composable
fun InsufficientDataCard(r: ValuationReport, res: ModelResult) {
    val w = r.warnings.joinToString(" ").lowercase()
    val reason = when {
        r.price == null && res.intrinsicValuePerShare != null -> "No market quote was available, so the margin of safety can't be computed. Tap the price to enter one manually."
        "multi-class" in w || "no usable share count" in w -> "This company reports per-share data by share class (e.g. Class A / Class B), which SEC's company-facts feed doesn't expose. Total-company figures below are still valid; per-share values will arrive with the Sprint 2 XBRL upgrade."
        "missing concepts" in w && "eps" in w -> "The latest 10-K doesn't tag diluted EPS in a way the engine recognizes yet, so the Graham formulas can't run. Owner-earnings totals are still shown."
        else -> "The filings don't carry enough of the line items this model needs. See the data notes at the bottom for the specifics."
    }
    Card { Text("Why is the intrinsic value missing?", style = MaterialTheme.typography.titleMedium, color = VL.textPrimary); Text(reason, style = MaterialTheme.typography.bodySmall, color = VL.textSecondary, modifier = Modifier.padding(top = 6.dp)) }
}

@Composable
fun SnapshotGrid(r: ValuationReport) {
    val items = listOf<Triple<String, String, (Double) -> String>>(
        Triple("equity", "Book value", Fmt::compact), Triple("book_value_per_share", "Book / share", { Fmt.money(it) }),
        Triple("cash", "Cash", Fmt::compact), Triple("total_debt", "Total debt", Fmt::compact),
        Triple("current_ratio", "Current ratio", { Fmt.number(it) + "×" }), Triple("debt_to_equity", "Debt / equity", { Fmt.number(it) + "×" }),
        Triple("roe", "ROE", { Fmt.pct(it, 1, true) }), Triple("roic", "ROIC", { Fmt.pct(it, 1, true) }),
        Triple("operating_margin", "Op. margin", { Fmt.pct(it, 1, true) }), Triple("net_margin", "Net margin", { Fmt.pct(it, 1, true) }),
    )
    items.chunked(2).forEach { row ->
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            row.forEach { (k, label, f) ->
                val s = r.snapshot[k]
                Column(Modifier.weight(1f)) {
                    Text(label, style = MaterialTheme.typography.bodySmall, color = VL.textSecondary)
                    Text(s?.let { f(it.value) } ?: "—", color = VL.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    s?.let { Text(it.tag.removePrefix("us-gaap:"), fontSize = 11.sp, color = VL.textTertiary, maxLines = 1) }
                }
            }
        }
    }
}

/** Fundamental history only (revenue/earnings/cash flow/ROIC/book). Nothing price-derived. */
@Composable
fun HistoryChart(history: List<HistoryPoint>, series: String) {
    val cfg: List<Triple<String, (HistoryPoint) -> Double?, Color>> = when (series) {
        "cash" -> listOf(Triple("Free cash flow", { it.fcf }, VL.info), Triple("Owner earnings", { it.ownerEarnings }, VL.value))
        "roic" -> listOf(Triple("ROIC", { it.roic?.times(100) }, VL.value))
        "book" -> listOf(Triple("Book value", { it.equity }, VL.value))
        else -> listOf(Triple("Revenue", { it.revenue }, VL.textTertiary), Triple("Net income", { it.netIncome }, VL.value))
    }
    val pts = history.filter { it.fiscalYear != null }
    val maxV = pts.flatMap { h -> cfg.map { c -> kotlin.math.abs(c.second(h) ?: 0.0) } }.maxOrNull()?.takeIf { it > 0 } ?: 1.0
    Column(Modifier.padding(top = 12.dp)) {
        Row { Text(if (series == "roic") "${maxV.toInt()}%" else Fmt.compact(maxV), fontSize = 10.sp, color = VL.textTertiary) }
        // Read before the Canvas: its draw scope is not a composable scope.
        val axisColor = VL.border
        Canvas(Modifier.fillMaxWidth().height(150.dp)) {
            val n = pts.size.coerceAtLeast(1)
            val groupW = size.width / n
            val barW = (groupW - 6.dp.toPx()) / cfg.size
            drawLine(axisColor, Offset(0f, size.height), Offset(size.width, size.height))
            drawLine(axisColor, Offset(0f, size.height / 2), Offset(size.width, size.height / 2))
            pts.forEachIndexed { i, h ->
                cfg.forEachIndexed { j, c ->
                    val v = c.second(h) ?: 0.0
                    val hPx = (kotlin.math.abs(v) / maxV * size.height).toFloat()
                    drawRect(c.third, Offset(i * groupW + 3.dp.toPx() + j * barW, size.height - hPx), Size(barW - 2.dp.toPx(), hPx))
                }
            }
        }
        Row(Modifier.fillMaxWidth()) { pts.forEach { h -> Text(h.fiscalYear.toString().takeLast(2), fontSize = 10.sp, color = VL.textTertiary, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center) } }
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) { cfg.forEach { c -> Text("● ${c.first}", fontSize = 11.sp, color = c.third) } }
    }
}

@Composable
fun HistoryTable(history: List<HistoryPoint>) {
    val rows = listOf<Pair<String, (HistoryPoint) -> String>>(
        "Revenue" to { Fmt.compact(it.revenue) }, "Net income" to { Fmt.compact(it.netIncome) }, "EPS (diluted)" to { Fmt.money(it.epsDiluted) },
        "Cash from ops" to { Fmt.compact(it.cfo) }, "CapEx" to { Fmt.compact(it.capex) }, "Free cash flow" to { Fmt.compact(it.fcf) },
        "Owner earnings" to { Fmt.compact(it.ownerEarnings) }, "Book value" to { Fmt.compact(it.equity) }, "Book / share" to { Fmt.money(it.bookValuePerShare) }, "ROIC" to { Fmt.pct(it.roic, 1, true) },
    )
    Column(Modifier.horizontalScroll(rememberScrollState()).padding(14.dp)) {
        Row { Text("FY", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = VL.textSecondary, modifier = Modifier.width(110.dp)); history.forEach { Text("${it.fiscalYear}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = VL.textSecondary, modifier = Modifier.width(76.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End) } }
        ThinDivider()
        rows.forEach { (l, f) -> Row(Modifier.padding(vertical = 4.dp)) { Text(l, fontSize = 12.sp, color = VL.textPrimary, modifier = Modifier.width(110.dp)); history.forEach { Text(f(it), fontSize = 12.sp, color = VL.textSecondary, modifier = Modifier.width(76.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End) } } }
    }
}
