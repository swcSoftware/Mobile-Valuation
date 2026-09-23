package com.swcsoftware.valuelens.ui.layouts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swcsoftware.valuelens.core.Explain
import com.swcsoftware.valuelens.domain.ValuationModel
import com.swcsoftware.valuelens.domain.Verdict
import com.swcsoftware.valuelens.domain.verdictEnum
import com.swcsoftware.valuelens.ui.Fmt
import com.swcsoftware.valuelens.ui.components.Card
import com.swcsoftware.valuelens.ui.components.ModelToggle
import com.swcsoftware.valuelens.ui.theme.VL
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Sprint 5's second presentation: the decision first, then the business behind it, graded.
 * Mirrors apps/ios/ValueLens/Features/Company/Layouts/ReportCardLayout.swift.
 *
 * Every grade, rule, historical read and chip label comes from the core's `ReportCardSummary`
 * (Grading.kt); this composable decides only how they look (CLAUDE.md non-negotiable 7).
 */
@Composable
fun ReportCardLayout(ctx: LayoutContext) {
    val r = ctx.report
    val res = ctx.result
    val mos = res.marginOfSafety
    val card = ctx.reportCard
    val failed = r.dataChecks.any { it.status == "fail" }
    val chip = card?.let { if (ctx.model == ValuationModel.A) it.chipA else it.chipB }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp, 8.dp, 16.dp, 32.dp)) {
        // ---- heading
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(r.company.name, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold,
                     letterSpacing = (-0.4).sp, lineHeight = 29.sp, color = VL.textPrimary)
                Text(listOfNotNull(r.company.ticker, card?.modeLabel,
                                   r.quote?.let { "priced ${it.asOf.take(10)}" } ?: "no quote").joinToString(" · "),
                     style = MaterialTheme.typography.bodySmall, color = VL.textSecondary,
                     modifier = Modifier.padding(top = 3.dp))
            }
            chip?.let { VerdictChipView(it) }
        }
        Spacer(Modifier.height(12.dp))

        r.sector?.takeIf { it.mode != "general" }?.let { SectorModeBadge(it) }

        // ---- price against value: what the reader came for
        if (failed) {
            WithheldValueCard(r, res)
        } else {
            Card {
                Text(if (ctx.model == ValuationModel.A) "EARNINGS POWER" else "DISCOUNTED CASH FLOW",
                     fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = VL.textTertiary)
                Text(Explain.verdictSentence(r, res), fontSize = 18.sp, fontWeight = FontWeight.Medium,
                     lineHeight = 24.sp, color = VL.textPrimary, modifier = Modifier.padding(top = 8.dp))
                Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Column(Modifier.clickable { ctx.onEditPrice() }) {
                        Text("YOU PAY", fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, color = VL.price)
                        Text(Fmt.money(mos.marketPrice), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = VL.price)
                    }
                    Column {
                        Text("IT'S WORTH", fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, color = VL.value)
                        Text(mos.intrinsicValue?.let { Fmt.money(it) } ?: "Withheld", fontSize = 28.sp,
                             fontWeight = FontWeight.ExtraBold,
                             color = if (mos.intrinsicValue == null) VL.textTertiary else VL.value)
                    }
                }
                Spacer(Modifier.height(14.dp))
                PriceValueBar(mos.marketPrice, mos.intrinsicValue)
                Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.Bottom) {
                    val pct = mos.marginOfSafetyPct
                    Text(pct?.let { (if (it > 0) "+" else if (it < 0) "−" else "") + "${abs(it).roundToInt()}%" } ?: "No figure",
                         fontSize = 14.sp, fontWeight = FontWeight.Bold, color = toneColor(chip?.tone ?: "none"))
                    val band = mos.bands.firstOrNull { it.discountPct == 25.0 }?.buyBelow
                    Text(
                        when {
                            mos.intrinsicValue == null -> "see the notes below"
                            band != null -> "margin today · Graham's 25% discount would be ${Fmt.money(band)}"
                            else -> "margin today"
                        },
                        style = MaterialTheme.typography.bodySmall, color = VL.textSecondary,
                        modifier = Modifier.padding(start = 7.dp),
                    )
                }
            }
            if (mos.verdictEnum == Verdict.INSUFFICIENT) {
                Spacer(Modifier.height(12.dp)); WithheldValueCard(r, res)
            }
        }

        Spacer(Modifier.height(12.dp))
        ModelToggle(ctx.model, labels = ValuationModel.entries.map { Explain.modelName(it == ValuationModel.A, ctx.expert) },
                    onChange = ctx.onModel)

        // ---- business health, graded
        if (!ctx.expert && card != null) {
            Row(Modifier.fillMaxWidth().padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("BUSINESS HEALTH, GRADED", fontSize = 10.sp, fontWeight = FontWeight.Bold,
                     letterSpacing = 1.sp, color = VL.textTertiary, modifier = Modifier.weight(1f))
                // The lens is shown on the screen it affects — a grade that silently depends on a
                // setting buried elsewhere is exactly a "silent number".
                Text("⇄  ${card.lensName} lens", fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                     color = VL.textSecondary,
                     modifier = Modifier.background(VL.raised, RoundedCornerShape(50))
                         .clickable { ctx.onSwapLens() }.padding(horizontal = 10.dp, vertical = 6.dp))
            }
            Text("${card.lensBlurb} Thresholds are shown on every row; the valuation above is the same whichever lens you pick.",
                 style = MaterialTheme.typography.bodySmall, color = VL.textTertiary, modifier = Modifier.padding(top = 6.dp))
            val blank = card.blankNote
            if (blank != null) {
                Spacer(Modifier.height(8.dp))
                Card {
                    Text("These four facts don't fit this filer", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = VL.textPrimary)
                    Text(blank, style = MaterialTheme.typography.bodySmall, color = VL.textSecondary, modifier = Modifier.padding(top = 4.dp))
                }
            } else {
                card.facts.forEach { GradedFactRow(it) }
            }
        }

        CheckStripCard(r.dataChecks)
        DataNotesSection(r, includeChecks = false)

        if (!ctx.expert) {
            Spacer(Modifier.height(12.dp))
            TextButton({ ctx.onSetShowMath(true) }, Modifier.fillMaxWidth()) {
                Text("Show me the math", color = VL.accent)
            }
        } else {
            // Expert Mode is one shared presentation (Sprint 5 Track A), not restyled per layout.
            ExpertBody(ctx)
        }

        ProvenanceRow(r)
        Text(r.disclaimer, style = MaterialTheme.typography.bodySmall, color = VL.textTertiary,
             modifier = Modifier.fillMaxWidth().padding(top = 20.dp), textAlign = TextAlign.Center)
    }
}
