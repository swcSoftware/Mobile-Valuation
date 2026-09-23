package com.swcsoftware.valuelens.ui.layouts

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swcsoftware.valuelens.domain.ModelResult
import com.swcsoftware.valuelens.domain.SectorInfo
import com.swcsoftware.valuelens.domain.ValuationReport
import com.swcsoftware.valuelens.domain.Verdict
import com.swcsoftware.valuelens.domain.price
import com.swcsoftware.valuelens.domain.verdictEnum
import com.swcsoftware.valuelens.ui.Fmt
import com.swcsoftware.valuelens.ui.components.Card
import com.swcsoftware.valuelens.ui.theme.VL

/**
 * The four obligations from docs/DESIGN.md, as components rather than markup repeated per layout.
 * Mirrors apps/ios/ValueLens/DesignSystem/RequiredComponents.swift.
 *
 * They are deliberately plain: a layout styles its surroundings, but the *substance* — what was
 * withheld, what was assumed, which industry rules applied — is written once here, so two layouts
 * cannot drift into telling the reader different things.
 */

/** 1. A value we refused to show, as a deliberate act rather than an error or a blank. */
@Composable
fun WithheldValueCard(r: ValuationReport, res: ModelResult) {
    val failed = r.dataChecks.any { it.status == "fail" }
    val reason = when {
        failed -> {
            val names = r.dataChecks.filter { it.status == "fail" }.joinToString(", ") { it.label }
            "Some of the numbers pulled from SEC didn't pass verification" +
                (if (names.isEmpty()) "" else " ($names)") +
                ", so ValueLens won't show a fair value it can't stand behind."
        }
        r.price == null && res.marginOfSafety.intrinsicValue != null ->
            "No market quote was available, so the margin of safety can't be computed. Tap the price to enter one manually."
        r.warnings.joinToString(" ").lowercase().let { it.contains("missing concepts") && it.contains("eps") } ->
            "The latest 10-K doesn't tag diluted EPS in a way the concept map recognizes yet, so the Graham formulas can't run. Owner-earnings totals are still shown."
        r.warnings.joinToString(" ").lowercase().let { it.contains("negative") || it.contains("no usable") } ->
            "This model produced no figure a share price could be read from — a discounted cash flow can come out negative, and a negative per-share value is not a price. Nothing is shown rather than a number we can't stand behind."
        else ->
            "The filings don't carry enough of the line items this model needs. The data notes say which ones."
    }
    Card {
        Text(
            if (failed) "Value withheld" else "Why is the fair value missing?",
            style = MaterialTheme.typography.titleMedium,
            color = if (failed) VL.danger else VL.textPrimary,
        )
        Text(reason, style = MaterialTheme.typography.bodySmall, color = VL.textSecondary,
             modifier = Modifier.padding(top = 4.dp))
    }
}

/**
 * 2. `warnings` plus, optionally, every data check that did not pass.
 *
 * `includeChecks` is false where a layout already shows the check list separately, so the reader
 * never sees the same finding twice.
 */
@Composable
fun DataNotesSection(r: ValuationReport, includeChecks: Boolean = true) {
    val flagged = if (includeChecks) r.dataChecks.filter { it.status != "pass" } else emptyList()
    val count = flagged.size + r.warnings.size
    if (count == 0) return   // nothing to say, so say nothing — and claim nothing

    var expanded by remember { mutableStateOf(false) }
    val summary = if (includeChecks) {
        "${r.dataChecks.count { it.status == "pass" }} of ${r.dataChecks.size} checks cleared · $count note${if (count == 1) "" else "s"}"
    } else {
        "$count note${if (count == 1) "" else "s"}"
    }

    Spacer(Modifier.height(12.dp))
    Card {
        Row(
            Modifier.fillMaxWidth().clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("ⓘ  Data notes", style = MaterialTheme.typography.titleMedium,
                 color = VL.textPrimary, modifier = Modifier.weight(1f))
            Text(summary, style = MaterialTheme.typography.bodySmall, color = VL.textSecondary)
        }
        AnimatedVisibility(expanded) {
            Column(Modifier.padding(top = 8.dp)) {
                flagged.forEach { check ->
                    NoteRow(check.label, check.message,
                            if (check.status == "fail") VL.danger else VL.warning)
                }
                r.warnings.forEach { NoteRow(null, it, VL.border) }
            }
        }
    }
}

@Composable
private fun NoteRow(title: String?, body: String, tint: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Spacer(Modifier.width(2.dp).height(if (title == null) 18.dp else 34.dp))
        Column(Modifier.padding(start = 9.dp)) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.bodySmall, color = VL.textPrimary)
            }
            Text(body, style = MaterialTheme.typography.bodySmall, color = VL.textSecondary)
        }
    }
}

/**
 * 3. Beta is measured from five years of prices; cost of debt very often is not. An unlabeled input
 * is a bug (CLAUDE.md non-negotiable 2), so every layout carries this.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProvenanceRow(r: ValuationReport) {
    data class Item(val text: String, val measured: Boolean)
    val tracked = listOf(
        Triple("beta", "Beta", setOf("measured")),
        Triple("tax_rate", "Tax rate", setOf("sec")),
        Triple("cost_of_debt", "Cost of debt", setOf("sec")),
        Triple("rates", "Treasury rates", setOf("fred")),
    )
    val items = tracked.mapNotNull { (key, label, measuredValues) ->
        val source = r.provenance[key] ?: return@mapNotNull null
        val measured = source in measuredValues
        val detail = when {
            key == "beta" && measured -> "measured over 5 years"
            key == "rates" && measured -> "from FRED"
            measured -> "from the filings"
            else -> "assumed"
        }
        Item("$label $detail", measured)
    }
    if (items.isEmpty()) return
    FlowRow(Modifier.fillMaxWidth().padding(top = 14.dp)) {
        items.forEach { item ->
            Text(
                (if (item.measured) "✓ " else "○ ") + item.text,
                fontSize = 11.sp,
                color = if (item.measured) VL.textTertiary else VL.warning,
                modifier = Modifier.padding(end = 12.dp, bottom = 4.dp),
            )
        }
    }
}

/**
 * 4. An operating company, a bank and a REIT are not valued the same way. When a company is valued
 * by anything other than the general rules, the screen says so.
 */
@Composable
fun SectorModeBadge(sector: SectorInfo) {
    Text(
        "Industry mode: ${sector.note}",
        style = MaterialTheme.typography.bodySmall,
        color = VL.info,
        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
    )
}
