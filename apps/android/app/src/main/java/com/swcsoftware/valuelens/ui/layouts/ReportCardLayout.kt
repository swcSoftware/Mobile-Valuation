package com.swcsoftware.valuelens.ui.layouts

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.swcsoftware.valuelens.core.Explain
import com.swcsoftware.valuelens.domain.ValuationModel
import com.swcsoftware.valuelens.domain.Verdict
import com.swcsoftware.valuelens.domain.verdictEnum
import com.swcsoftware.valuelens.ui.Fmt
import com.swcsoftware.valuelens.ui.components.Card
import com.swcsoftware.valuelens.ui.components.DataChecksCard
import com.swcsoftware.valuelens.ui.components.MarginOfSafetyView
import com.swcsoftware.valuelens.ui.components.ModelToggle
import com.swcsoftware.valuelens.ui.theme.VL

/**
 * Sprint 5's second presentation: the decision first, then the business behind it.
 *
 * **Track A ships the structure; Track C ships the design** — and Track C is where this gets its
 * graded health facts, once `Explain.Fact` carries `grade` and `rule` from the core. Nothing here
 * computes a grade locally; that would break non-negotiable 7.
 *
 * Mirrors apps/ios/ValueLens/Features/Company/Layouts/ReportCardLayout.swift.
 */
@Composable
fun ReportCardLayout(ctx: LayoutContext) {
    val r = ctx.report
    val res = ctx.result
    val failed = r.dataChecks.any { it.status == "fail" }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp, 8.dp, 16.dp, 32.dp)) {
        Text(r.company.name, style = MaterialTheme.typography.headlineSmall, color = VL.textPrimary)
        Text(
            r.company.ticker + (r.quote?.let { " · priced ${it.asOf.take(10)}" } ?: " · no quote"),
            style = MaterialTheme.typography.bodySmall, color = VL.textSecondary,
        )
        Spacer(Modifier.height(10.dp))

        r.sector?.takeIf { it.mode != "general" }?.let { SectorModeBadge(it) }

        if (failed || res.marginOfSafety.verdictEnum == Verdict.INSUFFICIENT) {
            WithheldValueCard(r, res)
        } else {
            Card {
                if (!ctx.expert) {
                    Text(Explain.verdictSentence(r, res), color = VL.textPrimary,
                         modifier = Modifier.padding(bottom = 12.dp))
                }
                Row(Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text("YOU PAY", style = MaterialTheme.typography.labelSmall, color = VL.price)
                        Text(Fmt.money(res.marginOfSafety.marketPrice),
                             style = MaterialTheme.typography.headlineSmall, color = VL.price)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("IT'S WORTH", style = MaterialTheme.typography.labelSmall, color = VL.value)
                        Text(Fmt.money(res.marginOfSafety.intrinsicValue),
                             style = MaterialTheme.typography.headlineSmall, color = VL.value)
                    }
                }
                Spacer(Modifier.height(12.dp))
                MarginOfSafetyView(res.marginOfSafety, compact = !ctx.expert)
            }
        }

        Spacer(Modifier.height(12.dp))
        ModelToggle(
            ctx.model,
            labels = ValuationModel.entries.map { Explain.modelName(it == ValuationModel.A, ctx.expert) },
            onChange = ctx.onModel,
        )

        Spacer(Modifier.height(12.dp))
        DataChecksCard(r.dataChecks, Explain.checksSummary(r), ctx.expert)
        DataNotesSection(r, includeChecks = false)

        if (!ctx.expert) {
            Spacer(Modifier.height(12.dp))
            TextButton({ ctx.onSetShowMath(true) }, Modifier.fillMaxWidth()) {
                Text("Show me the math", color = VL.accent)
            }
        }

        ProvenanceRow(r)
        Text(
            r.disclaimer, style = MaterialTheme.typography.bodySmall, color = VL.textTertiary,
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp), textAlign = TextAlign.Center,
        )
    }
}
