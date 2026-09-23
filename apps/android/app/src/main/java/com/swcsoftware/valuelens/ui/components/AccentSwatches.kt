package com.swcsoftware.valuelens.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.swcsoftware.valuelens.ui.theme.AccentChoice
import com.swcsoftware.valuelens.ui.theme.Rgb
import com.swcsoftware.valuelens.ui.theme.VL

/**
 * The chrome-colour picker. Mirrors apps/ios/ValueLens/Features/Settings/AccentPicker.swift.
 *
 * Enforces two rules rather than trusting them:
 *  1. An accent that cannot reach 3:1 against the current ground is shown as unavailable, with the
 *     reason, instead of producing a button nobody can read.
 *  2. Price and value are never offered here — they are the one pair the app depends on telling
 *     apart (docs/DESIGN.md, "Semantic colors").
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccentSwatches(selected: Long?, isDark: Boolean, onPick: (Long?) -> Unit) {
    val ground = Rgb(if (isDark) 0xFF0B0D10 else 0xFFF1F4F5)
    FlowRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        AccentChoice.presets.forEach { (name, argb) ->
            val legible = Rgb(argb).isLegibleOn(ground)
            val chosen = selected == argb
            Box(
                Modifier
                    .padding(end = 10.dp, bottom = 10.dp)
                    .size(48.dp)
                    .alpha(if (legible) 1f else 0.45f)
                    .background(Color(argb), RoundedCornerShape(10.dp))
                    .border(
                        width = if (chosen) 2.dp else 1.dp,
                        color = if (chosen) VL.textPrimary else VL.border,
                        shape = RoundedCornerShape(10.dp),
                    )
                    .then(
                        if (legible) Modifier.clickable { onPick(if (chosen) null else argb) }
                        else Modifier
                    )
                    .semantics {
                        contentDescription = if (legible) {
                            "$name accent" + if (chosen) ", selected" else ""
                        } else {
                            "$name accent, unavailable — too little contrast with this background"
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (chosen) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = Rgb(argb).readableForeground())
                }
            }
        }
    }
}
