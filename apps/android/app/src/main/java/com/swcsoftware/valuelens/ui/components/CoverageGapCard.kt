package com.swcsoftware.valuelens.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swcsoftware.valuelens.domain.CoverageReport
import com.swcsoftware.valuelens.ui.theme.Mono
import com.swcsoftware.valuelens.ui.theme.VL

/**
 * Concept-map gaps for this filer: a line item the app couldn't match to SEC's tags.
 * Detection only — the map is never updated from the device (docs/TASKS.md Sprint 4 Track A).
 */
@Composable
fun CoverageGapCard(coverage: CoverageReport, expert: Boolean, onReport: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    val critical = coverage.gaps.count { it.critical }
    val summary = if (critical > 0)
        "$critical line item${if (critical > 1) "s" else ""} this app needs isn't in ${coverage.company}'s filings in a form we recognize."
    else "${coverage.gaps.size} line item${if (coverage.gaps.size > 1) "s" else ""} couldn't be matched to SEC's tags — some figures below may be missing."

    Card(Modifier.clickable { open = !open }) {
        Row(verticalAlignment = Alignment.Top) {
            Text("🧩", fontSize = 14.sp)
            Spacer(Modifier.padding(horizontal = 4.dp))
            Column(Modifier.weight(1f)) {
                Text("Unmatched line items", style = MaterialTheme.typography.titleMedium, color = VL.textPrimary)
                Text(summary, style = MaterialTheme.typography.bodySmall, color = VL.textSecondary)
            }
            Text(if (open) "▲" else "▼", color = VL.textTertiary, fontSize = 10.sp)
        }
        AnimatedVisibility(open) {
            Column(Modifier.padding(top = 8.dp)) {
                coverage.gaps.forEach { g ->
                    Column(Modifier.padding(vertical = 4.dp)) {
                        Row {
                            Text(g.label, color = VL.textPrimary, fontSize = 14.sp)
                            if (g.critical) { Spacer(Modifier.padding(horizontal = 3.dp)); Pill("needed", VL.danger) }
                        }
                        Text(
                            when (g.kind) {
                                "unusable" -> "Reported, but not in an annual or trailing-twelve-month form we can use."
                                "stale" -> "In the annual report but missing from the latest quarter."
                                else -> "This filer doesn't use any tag we recognize for it."
                            },
                            style = MaterialTheme.typography.bodySmall, color = VL.textSecondary,
                        )
                        if (expert) g.candidates.forEach { Text(it, fontFamily = Mono, fontSize = 11.sp, color = VL.info, maxLines = 1) }
                    }
                }
                TextButton(onReport) { Text("Report this to the ValueLens team", color = VL.value, fontSize = 13.sp) }
                Text("Opens a prefilled report in your browser. Nothing is sent until you submit it, and it contains only public SEC identifiers — no personal data.",
                    style = MaterialTheme.typography.bodySmall, color = VL.textTertiary)
            }
        }
    }
}
