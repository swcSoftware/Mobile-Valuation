package com.swcsoftware.valuelens.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swcsoftware.valuelens.domain.MarginOfSafety
import com.swcsoftware.valuelens.domain.Metric
import com.swcsoftware.valuelens.domain.SourcedValue
import com.swcsoftware.valuelens.domain.ValuationModel
import com.swcsoftware.valuelens.domain.verdictEnum
import com.swcsoftware.valuelens.ui.Fmt
import com.swcsoftware.valuelens.ui.theme.Mono
import com.swcsoftware.valuelens.ui.theme.VL

@Composable
fun Card(modifier: Modifier = Modifier, padding: Int = 16, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(VL.surface)
            .border(1.dp, VL.border, RoundedCornerShape(16.dp))
            .padding(padding.dp),
        content = content,
    )
}

@Composable
fun SectionHeader(title: String, subtitle: String? = null) {
    Column(Modifier.padding(top = 8.dp)) {
        Text(title.uppercase(), style = MaterialTheme.typography.labelSmall, color = VL.textSecondary)
        subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = VL.textTertiary) }
    }
}

@Composable
fun Pill(text: String, color: Color) {
    Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clip(CircleShape).background(color.copy(alpha = 0.16f)).padding(horizontal = 10.dp, vertical = 5.dp))
}

@Composable
fun PrimaryButton(title: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = VL.value, contentColor = VL.background, disabledContainerColor = VL.raised, disabledContentColor = VL.textTertiary)) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

@Composable
fun ModelToggle(model: ValuationModel, onChange: (ValuationModel) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            ValuationModel.entries.forEachIndexed { i, m ->
                SegmentedButton(selected = model == m, onClick = { onChange(m) },
                    shape = SegmentedButtonDefaults.itemShape(i, ValuationModel.entries.size),
                    colors = SegmentedButtonDefaults.colors(activeContainerColor = Color(0xFF3A3F48), activeContentColor = VL.textPrimary, inactiveContainerColor = VL.raised, inactiveContentColor = VL.textSecondary)) {
                    Text(m.label)
                }
            }
        }
        Text(model.subtitle, style = MaterialTheme.typography.bodySmall, color = VL.textSecondary, modifier = Modifier.padding(top = 6.dp))
    }
}

/** Tap-to-expand metric: value + exact formula + inputs + SEC sources (blueprint §7.2). */
@Composable
fun MetricRow(metric: Metric, emphasize: Boolean = false) {
    var expanded by remember { mutableStateOf(false) }
    val valueColor = when { metric.value == null -> VL.textTertiary; metric.unit == "USD/share" -> VL.value; else -> VL.textPrimary }
    Column(Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(metric.label, color = VL.textPrimary, modifier = Modifier.weight(1f),
                style = if (emphasize) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium)
            Text(Fmt.metric(metric), color = valueColor, fontSize = if (emphasize) 20.sp else 15.sp,
                fontWeight = if (emphasize) FontWeight.Bold else FontWeight.Normal)
            Text(if (expanded) " ▲" else " ▼", color = VL.textTertiary, fontSize = 10.sp)
        }
        AnimatedVisibility(expanded) {
            Column(Modifier.padding(top = 10.dp).clip(RoundedCornerShape(10.dp)).background(VL.raised).padding(12.dp)) {
                Text(metric.formula, fontFamily = Mono, fontSize = 12.sp, color = VL.info)
                if (metric.inputs.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    metric.inputs.toSortedMap().forEach { (k, v) ->
                        Row { Text(k, fontFamily = Mono, fontSize = 12.sp, color = VL.textSecondary, modifier = Modifier.weight(1f)); Text(Fmt.input(k, v), fontFamily = Mono, fontSize = 12.sp, color = VL.textPrimary) }
                    }
                }
                if (metric.sources.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("SEC SOURCES", style = MaterialTheme.typography.labelSmall, color = VL.textTertiary)
                    metric.sources.forEach { SourceLine(it) }
                }
                metric.notes.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = VL.textSecondary, modifier = Modifier.padding(top = 6.dp)) }
            }
        }
    }
}

@Composable
fun SourceLine(s: SourcedValue) {
    Column(Modifier.padding(top = 6.dp)) {
        Row { Text(s.tag, fontFamily = Mono, fontSize = 12.sp, color = VL.textPrimary, modifier = Modifier.weight(1f), maxLines = 1); Text(Fmt.compact(s.value), fontFamily = Mono, fontSize = 12.sp, color = VL.textSecondary) }
        Text("${s.form} · period ending ${s.periodEnd} · filed ${s.filed} · ${s.accession}", fontSize = 11.sp, color = VL.textTertiary)
        if (s.note.isNotEmpty()) Text(s.note, fontSize = 11.sp, color = VL.textTertiary)
    }
}

/** Price vs intrinsic value on one scale with 25% / 50% buy-below ticks. */
@Composable
fun MarginOfSafetyView(mos: MarginOfSafety, compact: Boolean = false) {
    val verdict = mos.verdictEnum
    val vc = VL.verdictColor(verdict)
    val description = buildString {
        mos.intrinsicValue?.let { append("Intrinsic value ${Fmt.money(it)}. ") } ?: append("Intrinsic value unavailable. ")
        mos.marketPrice?.let { append("Market price ${Fmt.money(it)}. ") }
        append(verdict.title)
    }
    Column(Modifier.fillMaxWidth().semantics { contentDescription = description }) {
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) { Text("MARKET PRICE", style = MaterialTheme.typography.labelSmall, color = VL.price); Text(Fmt.money(mos.marketPrice), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = VL.price) }
            Column(horizontalAlignment = Alignment.End) { Text("INTRINSIC VALUE", style = MaterialTheme.typography.labelSmall, color = VL.value); Text(Fmt.money(mos.intrinsicValue), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = VL.value) }
        }
        Spacer(Modifier.height(12.dp))
        val maxV = maxOf(mos.intrinsicValue ?: 0.0, mos.marketPrice ?: 0.0) * 1.15
        var widthPx by remember { mutableStateOf(0) }
        val density = LocalDensity.current
        fun x(v: Double?): Float = if (v == null || maxV <= 0) 0f else with(density) { (widthPx * (v / maxV)).toFloat().toDp().value }
        Box(Modifier.fillMaxWidth().height(22.dp).onSizeChanged { widthPx = it.width }) {
            Box(Modifier.fillMaxWidth().height(10.dp).align(Alignment.CenterStart).clip(CircleShape).background(VL.raised))
            mos.intrinsicValue?.let { iv ->
                Box(Modifier.width(x(iv).dp).height(10.dp).align(Alignment.CenterStart).clip(CircleShape).background(VL.value.copy(alpha = 0.35f)))
                mos.bands.forEach { b -> Box(Modifier.offset(x = x(b.buyBelow).dp).width(2.dp).height(16.dp).align(Alignment.CenterStart).background(VL.value)) }
                Box(Modifier.offset(x = (x(iv) - 1.5f).dp).width(3.dp).height(22.dp).align(Alignment.CenterStart).background(VL.value))
            }
            mos.marketPrice?.let { p -> Box(Modifier.offset(x = (x(p) - 7f).dp).size(14.dp).align(Alignment.CenterStart).clip(CircleShape).background(VL.price).border(2.dp, VL.background, CircleShape)) }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Pill(verdict.title, vc)
            Spacer(Modifier.weight(1f))
            mos.marginOfSafetyPct?.let { m -> Text("MoS ${if (m < 0) "−" else ""}${Fmt.pct(abs(m), 0)}", color = vc, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
        }
        if (!compact) {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                mos.bands.forEach { b -> Column { Text("Buy below (${b.discountPct.toInt()}% MoS)", fontSize = 11.sp, color = VL.textTertiary); Text(Fmt.money(b.buyBelow), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = VL.textSecondary) } }
            }
        }
    }
}

private fun abs(v: Double) = kotlin.math.abs(v)

@Composable
fun ThinDivider() = HorizontalDivider(color = VL.border, thickness = 1.dp)
