package com.swcsoftware.valuelens.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swcsoftware.valuelens.core.Glossary
import com.swcsoftware.valuelens.ui.theme.VL

/** Whether terms link (Expert Mode) and what a tap does. Provided once at the app root (Sprint 8). */
class GlossaryLinks(val enabled: Boolean, val onTerm: (String) -> Unit)
val LocalGlossary = compositionLocalOf { GlossaryLinks(false) {} }

/**
 * Expert Mode text with its glossary terms dotted-underlined; tapping one opens the definition in a
 * bottom sheet, without leaving the screen. Which words link is decided by the core (`Glossary.link`),
 * so Android underlines the same words as iOS. Outside Expert Mode this is plain `Text`.
 */
@Composable
fun GlossaryText(
    text: String, color: Color, modifier: Modifier = Modifier,
    fontSize: TextUnit = TextUnit.Unspecified, fontFamily: FontFamily? = null, style: TextStyle = LocalTextStyle.current,
) {
    val links = LocalGlossary.current
    if (!links.enabled) { Text(text, modifier, color = color, fontSize = fontSize, fontFamily = fontFamily, style = style); return }
    val spans = remember(text) { Glossary.link(text) }
    if (spans.none { it.key != null }) { Text(text, modifier, color = color, fontSize = fontSize, fontFamily = fontFamily, style = style); return }
    val ranges = remember(spans) {
        var at = 0
        spans.mapNotNull { s -> val r = s.key?.let { at until at + s.text.length }; at += s.text.length; r }
    }
    val annotated = remember(spans) {
        buildAnnotatedString {
            spans.forEach { s ->
                val key = s.key
                if (key == null) append(s.text)
                else withLink(LinkAnnotation.Clickable(key) { links.onTerm(key) }) { append(s.text) }
            }
        }
    }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    // Compose has no dotted underline; draw one under each linked range, line by line, to match iOS.
    val dots = Modifier.drawBehind {
        val l = layout ?: return@drawBehind
        val effect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 2.dp.toPx()))
        ranges.forEach { r ->
            for (line in l.getLineForOffset(r.first)..l.getLineForOffset(r.last)) {
                val s = maxOf(r.first, l.getLineStart(line)); val e = minOf(r.last + 1, l.getLineEnd(line, visibleEnd = true))
                if (e <= s) continue
                val y = l.getLineBaseline(line) + 2.dp.toPx()
                drawLine(color.copy(alpha = 0.8f), Offset(l.getHorizontalPosition(s, true), y), Offset(l.getHorizontalPosition(e, true), y),
                    strokeWidth = 1.dp.toPx(), pathEffect = effect)
            }
        }
    }
    Text(annotated, modifier.then(dots), color = color, fontSize = fontSize, fontFamily = fontFamily, style = style, onTextLayout = { layout = it })
}

/** The definition over the current screen; "See it in the Glossary" opens the full list at this entry. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlossaryDefinitionSheet(key: String, expert: Boolean, onDismiss: () -> Unit, onOpenGlossary: (String) -> Unit) {
    val e = remember(key) { Glossary.entry(key) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = VL.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding()) {
            if (e == null) { Text("No definition for this term yet.", color = VL.textSecondary); return@Column }
            Text(e.term, color = VL.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(e.plain, color = VL.textSecondary, fontSize = 16.sp, modifier = Modifier.padding(top = 8.dp))
            if (expert) Text(e.expert, color = VL.textTertiary, fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp))
            TextButton({ onOpenGlossary(key) }, Modifier.padding(top = 6.dp)) { Text("See it in the Glossary", color = VL.accent) }
        }
    }
}
