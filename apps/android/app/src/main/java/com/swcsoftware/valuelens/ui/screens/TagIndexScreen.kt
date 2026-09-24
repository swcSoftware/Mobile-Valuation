package com.swcsoftware.valuelens.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swcsoftware.valuelens.core.DisplayLabels
import com.swcsoftware.valuelens.ui.components.Card
import com.swcsoftware.valuelens.ui.theme.Mono
import com.swcsoftware.valuelens.ui.theme.VL

/**
 * The Index: every term the app shows, with the SEC tag(s) behind it (Sprint 6, owner decision).
 * Value screens show plain language only; this page is the path back to the filing.
 * Mirrors apps/ios/ValueLens/Features/Settings/TagIndexView.swift.
 */
@Composable
fun TagIndexScreen(onBack: () -> Unit) {
    val entries = remember { DisplayLabels.index() }
    var query by remember { mutableStateOf("") }
    val q = query.trim().lowercase()
    val shown = if (q.isEmpty()) entries else entries.filter { e ->
        e.term.lowercase().contains(q) || e.tags.any { it.tag.lowercase().contains(q) || it.label.lowercase().contains(q) }
    }
    Column(Modifier.fillMaxSize().background(VL.background)) {
        Row(Modifier.fillMaxWidth().background(VL.surface).padding(8.dp, 12.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onBack) { Text("‹ Back", color = VL.accent) }
            Text("Index", fontWeight = FontWeight.Bold, color = VL.textPrimary, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            Spacer(Modifier.padding(horizontal = 32.dp))
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text("Every figure Alpha shows is read from a company's SEC filing, where it is recorded under a standard tag. These are the tags behind the terms you see, if you want to find a number in the filing yourself.",
                 style = MaterialTheme.typography.bodySmall, color = VL.textSecondary)
            Text("On sec.gov, open the company's 10-K or 10-Q in the Inline XBRL viewer (the iXBRL link beside the document) and search for the tag. Where a term lists several tags, Alpha uses the first one the company actually filed.",
                 style = MaterialTheme.typography.bodySmall, color = VL.textSecondary, modifier = Modifier.padding(top = 8.dp, bottom = 12.dp))
            OutlinedTextField(query, { query = it }, label = { Text("Search terms or tags") }, singleLine = true,
                              modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp))
            shown.forEach { e ->
                Card(Modifier.padding(bottom = 10.dp)) {
                    Text(e.term, style = MaterialTheme.typography.titleMedium, color = VL.textPrimary)
                    e.tags.forEachIndexed { i, t ->
                        // Selectable, so a tag can be copied straight into a search.
                        SelectionContainer {
                            Text(t.tag, fontFamily = Mono, fontSize = 12.sp,
                                 color = if (i == 0) VL.textPrimary else VL.textSecondary, modifier = Modifier.padding(top = 8.dp))
                        }
                        Text(t.label, fontSize = 11.sp, color = VL.textTertiary)
                    }
                }
            }
            if (shown.isEmpty()) Text("Nothing matches “$query”.", fontSize = 12.sp, color = VL.textTertiary)
        }
    }
}
