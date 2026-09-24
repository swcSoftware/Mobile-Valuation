package com.swcsoftware.valuelens.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swcsoftware.valuelens.core.Grading
import com.swcsoftware.valuelens.core.Lens
import com.swcsoftware.valuelens.core.LensInfo
import com.swcsoftware.valuelens.data.SampleValuationRepository
import com.swcsoftware.valuelens.domain.ValuationModel
import com.swcsoftware.valuelens.domain.ValuationReport
import com.swcsoftware.valuelens.ui.LayoutStyle
import com.swcsoftware.valuelens.ui.layouts.ClassicLayout
import com.swcsoftware.valuelens.ui.layouts.LayoutContext
import com.swcsoftware.valuelens.ui.layouts.ReportCardLayout
import com.swcsoftware.valuelens.ui.theme.VL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Value or Growth, described in the core's own words (Grading.lenses()), so onboarding and Settings
 * cannot describe the lens differently. Mirrors apps/ios/.../Settings/PresentationPickers.swift.
 */
@Composable
fun LensPicker(selected: Lens, onSelect: (Lens) -> Unit, cards: Boolean = false) {
    val lenses = remember { Grading.lenses() }
    if (cards) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            lenses.forEach { info -> LensCard(info, info.key == selected.name) { onSelect(Lens.from(info.key)) } }
        }
    } else {
        Column {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                lenses.forEachIndexed { i, info ->
                    SegmentedButton(
                        selected = info.key == selected.name,
                        onClick = { onSelect(Lens.from(info.key)) },
                        shape = SegmentedButtonDefaults.itemShape(i, lenses.size),
                    ) { Text(info.name) }
                }
            }
            lenses.firstOrNull { it.key == selected.name }?.let {
                Text(it.blurb, style = MaterialTheme.typography.bodySmall, color = VL.textSecondary,
                     modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

@Composable
private fun LensCard(info: LensInfo, chosen: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .background(VL.surface, RoundedCornerShape(16.dp))
            .border(if (chosen) 2.dp else 1.dp, if (chosen) VL.accent else VL.border, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(16.dp)
            .semantics { selected = chosen; contentDescription = "${info.name}. ${info.blurb}" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(info.name, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = VL.textPrimary)
            Text(info.blurb, fontSize = 14.sp, color = VL.textSecondary, modifier = Modifier.padding(top = 6.dp))
        }
        Text(if (chosen) "✓" else "›", fontSize = 20.sp, color = if (chosen) VL.accent else VL.textTertiary,
             modifier = Modifier.padding(start = 12.dp))
    }
}

/**
 * The two layouts, each drawn by the layout itself on the bundled Apple sample and scaled down — so
 * what you see here is what you will get, in the current theme and accent.
 */
@Composable
fun LayoutPicker(selected: LayoutStyle, lens: Lens, onSelect: (LayoutStyle) -> Unit) {
    val context = LocalContext.current
    val sample by produceState<ValuationReport?>(null) {
        value = withContext(Dispatchers.IO) { runCatching { SampleValuationRepository(context).valuation("AAPL") }.getOrNull() }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        LayoutStyle.entries.forEach { style ->
            val chosen = style == selected
            Column(
                Modifier.weight(1f).clickable { onSelect(style) }
                    .semantics(mergeDescendants = true) { this.selected = chosen; contentDescription = "${style.displayName} layout. ${style.blurb}" },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Thumbnail(style, sample, lens, chosen)
                Text((if (chosen) "✓ " else "") + style.displayName, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                     color = if (chosen) VL.accent else VL.textPrimary, modifier = Modifier.padding(top = 8.dp))
                Text(style.blurb, fontSize = 11.sp, color = VL.textSecondary, textAlign = TextAlign.Center)
            }
        }
    }
}

private const val PHONE_WIDTH = 393
private const val CROP_HEIGHT = 640
private const val SCALE = 0.34f

@Composable
private fun Thumbnail(style: LayoutStyle, sample: ValuationReport?, lens: Lens, chosen: Boolean) {
    Box(
        Modifier.size((PHONE_WIDTH * SCALE).dp, (CROP_HEIGHT * SCALE).dp)
            .clip(RoundedCornerShape(10.dp))
            .background(VL.background)
            .border(if (chosen) 2.5.dp else 1.dp, if (chosen) VL.accent else VL.border, RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (sample == null) {
            CircularProgressIndicator(Modifier.size(20.dp))
        } else {
            val ctx = remember(sample, lens) {
                LayoutContext(
                    report = sample, result = sample.modelA, expert = false, expertModeAlwaysOn = false,
                    model = ValuationModel.A, onModel = {}, onEditPrice = {}, onSetShowMath = {},
                    onReportIssue = {}, reportCard = Grading.reportCard(sample, lens),
                )
            }
            // Laid out at phone width, then scaled — the layout never knows it is a thumbnail.
            Box(
                Modifier.wrapContentSize(Alignment.TopStart, unbounded = true)
                    .requiredSize(PHONE_WIDTH.dp, CROP_HEIGHT.dp)
                    .graphicsLayer { scaleX = SCALE; scaleY = SCALE; transformOrigin = TransformOrigin(0f, 0f) },
            ) {
                when (style) {
                    LayoutStyle.CLASSIC -> ClassicLayout(ctx)
                    LayoutStyle.REPORT_CARD -> ReportCardLayout(ctx)
                }
            }
            // Catches every touch so the miniature cannot be scrolled or tapped into.
            Box(Modifier.matchParentSize().clickable(enabled = false) {})
        }
    }
}
