package com.swcsoftware.valuelens.ui.layouts

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
 * What a layout is allowed to see.
 *
 * Deliberately narrow: the report, the selected model's result, and a few callbacks for the things
 * only the screen can do. A layout cannot reach the repository, the network or the core, which is
 * the mechanical reason it cannot compute a number of its own (CLAUDE.md non-negotiable 7).
 *
 * Mirrors apps/ios/ValueLens/Features/Company/Layouts/ClassicLayout.swift.
 */
data class LayoutContext(
    val report: ValuationReport,
    val result: ModelResult,
    /** Expert Mode, or "show me the math" tapped for this company. */
    val expert: Boolean,
    /** True when Expert Mode is on globally, so "hide the math" should not be offered. */
    val expertModeAlwaysOn: Boolean,
    val model: ValuationModel,
    val onModel: (ValuationModel) -> Unit,
    val onEditPrice: () -> Unit,
    val onSetShowMath: (Boolean) -> Unit,
    val onReportIssue: (CoverageReport) -> Unit,
    /** The core's grades, rules and chips for the active lens. Only the report card reads it. */
    val reportCard: ReportCardSummary? = null,
    /** Flips Value ⇄ Growth; the lens is shown on the screen it affects, so it is switched there. */
    val onSwapLens: () -> Unit = {},
) {
    val demandedComponents: Set<RequiredComponent>
        get() = RequiredComponent.demandedBy(report, result)
}

/** The layout that shipped in Sprints 0–4, unchanged in substance and in order. */
@Composable
fun ClassicLayout(ctx: LayoutContext) {
    val r = ctx.report
    val res = ctx.result
    val expert = ctx.expert
    val model = ctx.model

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp, 8.dp, 16.dp, 32.dp)) {
val failed = r.dataChecks.any { it.status == "fail" }
Text(r.company.name, style = MaterialTheme.typography.headlineSmall, color = VL.textPrimary)
Row(verticalAlignment = Alignment.Bottom) {
    Text(Fmt.money(r.price), fontSize = 38.sp, fontWeight = FontWeight.ExtraBold, color = VL.price, modifier = Modifier.clickable { ctx.onEditPrice() })
    Column(Modifier.padding(start = 12.dp, bottom = 8.dp)) {
        Text(r.quote?.let { "${it.source} · ${it.asOf.take(10)}" } ?: "no quote — tap to enter", fontSize = 11.sp, color = VL.textTertiary)
        if (expert) Text("CIK ${r.company.cik}", fontSize = 11.sp, color = VL.textTertiary)
    }
}
Spacer(Modifier.height(10.dp))
ModelToggle(model, labels = ValuationModel.entries.map { Explain.modelName(it == ValuationModel.A, expert) }, onChange = ctx.onModel)
if (!expert) Text(Explain.modelBlurb(model == ValuationModel.A, r.sector?.mode ?: "general"), style = MaterialTheme.typography.bodySmall, color = VL.textSecondary, modifier = Modifier.padding(vertical = 6.dp))
r.sector?.takeIf { it.mode != "general" }?.let { SectorModeBadge(it) }
Spacer(Modifier.height(8.dp))

if (failed) {
            WithheldValueCard(r, res)
        } else {
    Card { if (!expert) Text(Explain.verdictSentence(r, res), color = VL.textPrimary, modifier = Modifier.padding(bottom = 12.dp)); MarginOfSafetyView(res.marginOfSafety, compact = !expert) }
    if (res.marginOfSafety.verdictEnum == Verdict.INSUFFICIENT) { Spacer(Modifier.height(12.dp)); WithheldValueCard(r, res) }
}
Spacer(Modifier.height(12.dp))
DataChecksCard(r.dataChecks, Explain.checksSummary(r), expert)
r.coverage?.takeIf { it.gaps.isNotEmpty() }?.let { cov ->
    Spacer(Modifier.height(12.dp))
    CoverageGapCard(cov, expert) { ctx.onReportIssue(cov) }
}

if (!expert) {
    SectionHeader("How healthy is the business?", "Tap a tile for a plain-English explanation")
    val facts = Explain.healthFacts(r)
    facts.chunked(2).forEach { row -> Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { row.forEach { f -> FactTile(f.label, f.value, f.tone, f.plain, Modifier.weight(1f)) }; if (row.size == 1) Spacer(Modifier.weight(1f)) } }
    Spacer(Modifier.height(16.dp))
    TextButton({ ctx.onSetShowMath(true) }, Modifier.fillMaxWidth()) { Text("Show me the math", color = VL.accent) }
    Text("Turn on Expert Mode in Settings to always see formulas and SEC line items.", style = MaterialTheme.typography.bodySmall, color = VL.textTertiary, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
} else {
    ExpertBody(ctx)
}
Text(r.disclaimer, style = MaterialTheme.typography.bodySmall, color = VL.textTertiary, modifier = Modifier.fillMaxWidth().padding(top = 20.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}
