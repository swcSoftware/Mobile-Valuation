package com.swcsoftware.valuelens.ui.layouts

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swcsoftware.valuelens.core.GradedFact
import com.swcsoftware.valuelens.core.TrendPoint
import com.swcsoftware.valuelens.core.VerdictChip
import com.swcsoftware.valuelens.domain.DataCheck
import com.swcsoftware.valuelens.ui.components.Card
import com.swcsoftware.valuelens.ui.theme.VL

/**
 * Pieces of the report-card layout. Mirrors
 * apps/ios/ValueLens/Features/Company/Layouts/ReportCardComponents.swift.
 *
 * Every value they show — grades, rules, phrases, chip labels — arrives from the core in
 * `ReportCardSummary`; these composables decide only how it looks.
 */

/** Tone → colour: the only mapping a layout owns. */
@Composable
internal fun toneColor(tone: String): Color = when (tone) {
    "good" -> VL.value
    "mid" -> VL.warning
    "bad" -> VL.danger
    else -> VL.textTertiary
}

@Composable
internal fun gradeColor(grade: String?): Color = when (grade) {
    "A", "B" -> VL.value
    "C" -> VL.warning
    "D", "F" -> VL.danger
    else -> VL.textTertiary
}

@Composable
fun VerdictChipView(chip: VerdictChip) {
    val tint = toneColor(chip.tone)
    Text(
        chip.label.uppercase(),
        fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp, color = tint,
        modifier = Modifier.background(tint.copy(alpha = 0.16f), RoundedCornerShape(50)).padding(horizontal = 9.dp, vertical = 5.dp),
    )
}

/** What you pay against what it's worth, on one scale. The figures printed beside it are the core's. */
@Composable
fun PriceValueBar(price: Double?, value: Double?) {
    val track = VL.raised
    val fill = VL.value.copy(alpha = 0.35f)
    val marker = VL.price
    Canvas(Modifier.fillMaxWidth().height(14.dp)) {
        val span = maxOf(price ?: 0.0, value ?: 0.0, 1.0) * 1.1
        val barTop = size.height / 2 - 4.dp.toPx()
        val barH = 8.dp.toPx()
        drawRoundRect(track, topLeft = Offset(0f, barTop), size = androidx.compose.ui.geometry.Size(size.width, barH),
                      cornerRadius = androidx.compose.ui.geometry.CornerRadius(barH / 2))
        value?.let {
            drawRoundRect(fill, topLeft = Offset(0f, barTop),
                          size = androidx.compose.ui.geometry.Size((it / span * size.width).toFloat(), barH),
                          cornerRadius = androidx.compose.ui.geometry.CornerRadius(barH / 2))
        }
        price?.let {
            val x = (it / span * size.width).toFloat().coerceIn(0f, size.width - 3.dp.toPx())
            drawRect(marker, topLeft = Offset(x, 0f), size = androidx.compose.ui.geometry.Size(3.dp.toPx(), size.height))
        }
    }
}

@Composable
fun GradeBadge(grade: String?) {
    val tint = gradeColor(grade)
    Box(
        Modifier.size(30.dp).background(tint.copy(alpha = if (grade == null) 0.10f else 0.16f), RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(grade ?: "—", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = tint)
    }
}

/** The trend behind a fact, from the filed years. Fundamentals only — never price (blueprint §1). */
@Composable
fun Sparkline(points: List<TrendPoint>, tone: String) {
    val tint = toneColor(tone)
    Canvas(Modifier.width(62.dp).height(22.dp)) {
        if (points.size < 2) return@Canvas
        val lo = points.minOf { it.value }
        val hi = points.maxOf { it.value }
        val range = (hi - lo).takeIf { it > 0 } ?: 1.0
        val step = size.width / (points.size - 1)
        fun y(v: Double) = (size.height - 2.dp.toPx() - ((v - lo) / range * (size.height - 4.dp.toPx()))).toFloat()
        val path = Path()
        points.forEachIndexed { i, p ->
            if (i == 0) path.moveTo(0f, y(p.value)) else path.lineTo(i * step, y(p.value))
        }
        drawPath(path, tint.copy(alpha = 0.75f), style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(tint, radius = 2.2.dp.toPx(), center = Offset((points.size - 1) * step, y(points.last().value)))
    }
}

/**
 * One health fact: the grade against a printed rule, the value, and the company against its own
 * filed history. Tap for the plain-English reasoning.
 */
@Composable
fun GradedFactRow(fact: GradedFact) {
    var open by remember { mutableStateOf(false) }
    val border = VL.border
    Column(
        Modifier.fillMaxWidth().padding(top = 8.dp)
            .background(VL.surface, RoundedCornerShape(12.dp))
            .border(1.dp, border, RoundedCornerShape(12.dp))
            .clickable { open = !open }
            .padding(12.dp)
            .semantics {
                contentDescription = listOfNotNull(
                    fact.label,
                    fact.grade?.let { "grade $it, ${fact.rule}" } ?: fact.rule,
                    fact.value,
                    fact.history?.phrase,
                ).joinToString(". ")
            },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GradeBadge(fact.grade)
            Column(Modifier.padding(start = 10.dp).weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(fact.label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = VL.textPrimary,
                         maxLines = 1, modifier = Modifier.weight(1f))
                    fact.history?.let { Sparkline(it.points, it.tone); Spacer(Modifier.width(8.dp)) }
                    Text(
                        fact.value ?: if (fact.state == "missing") "—" else "Not graded",
                        fontSize = 17.sp, fontWeight = FontWeight.Bold,
                        color = if (fact.value == null) VL.textSecondary else VL.textPrimary,
                    )
                }
                Row(Modifier.padding(top = 3.dp)) {
                    Text(fact.rule, fontSize = 11.sp, color = VL.textTertiary, modifier = Modifier.weight(1f))
                    Text(
                        fact.history?.phrase ?: "No trend yet", fontSize = 11.sp, textAlign = TextAlign.End,
                        color = fact.history?.let { toneColor(it.tone) } ?: VL.textTertiary,
                    )
                }
            }
        }
        AnimatedVisibility(open) {
            Text(fact.why, style = MaterialTheme.typography.bodySmall, color = VL.textSecondary,
                 modifier = Modifier.padding(top = 10.dp))
        }
    }
}

/**
 * "14 of 16 data checks cleared" over one segment per check. Always shown — a clean gate is worth
 * seeing too — and it names every check that did not pass when opened.
 */
@Composable
fun CheckStripCard(checks: List<DataCheck>) {
    var open by remember { mutableStateOf(false) }
    val flagged = checks.filter { it.status != "pass" }
    val passColor = VL.value.copy(alpha = 0.7f)
    val warnColor = VL.warning
    val failColor = VL.danger
    fun color(status: String) = when (status) { "pass" -> passColor; "warn" -> warnColor; else -> failColor }

    Spacer(Modifier.height(12.dp))
    Card {
        Column(Modifier.fillMaxWidth().clickable { open = !open }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${checks.size - flagged.size} of ${checks.size} data checks cleared",
                     fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = VL.textPrimary, modifier = Modifier.weight(1f))
                Text(if (open) "Hide" else if (flagged.isEmpty()) "See all" else "What needs a look?",
                     fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = VL.accent)
            }
            Row(Modifier.fillMaxWidth().padding(top = 9.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                checks.forEach { c ->
                    Box(Modifier.weight(1f).height(5.dp).background(color(c.status), RoundedCornerShape(2.dp)))
                }
            }
        }
        AnimatedVisibility(open) {
            Column(Modifier.padding(top = 10.dp)) {
                (flagged.ifEmpty { checks }).forEach { c ->
                    Row(Modifier.padding(vertical = 4.dp)) {
                        Box(Modifier.width(2.dp).height(30.dp).background(color(c.status)))
                        Column(Modifier.padding(start = 9.dp)) {
                            Text(c.label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = VL.textPrimary)
                            Text(com.swcsoftware.valuelens.core.DisplayLabels.sentence(c.message), fontSize = 12.sp, color = VL.textSecondary)
                        }
                    }
                }
            }
        }
    }
}
