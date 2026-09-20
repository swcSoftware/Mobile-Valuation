package com.swcsoftware.valuelens.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.swcsoftware.valuelens.AppState
import com.swcsoftware.valuelens.core.Explain
import com.swcsoftware.valuelens.ui.components.Card
import com.swcsoftware.valuelens.ui.theme.VL

@Composable
fun GlossaryScreen(state: AppState, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(VL.background)) {
        Row(Modifier.fillMaxWidth().background(VL.surface).padding(8.dp, 12.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onBack) { Text("‹ Back", color = VL.value) }
            Text("Glossary", fontWeight = FontWeight.Bold, color = VL.textPrimary, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            androidx.compose.foundation.layout.Spacer(Modifier.padding(horizontal = 32.dp))
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text("Plain English first. ${if (state.expertMode) "Expert detail below each term." else "Turn on Expert Mode in Settings for the full definitions."}", style = MaterialTheme.typography.bodySmall, color = VL.textSecondary, modifier = Modifier.padding(bottom = 12.dp))
            Explain.glossary.forEach { g ->
                Card(Modifier.padding(bottom = 10.dp)) {
                    Text(g.term, style = MaterialTheme.typography.titleMedium, color = VL.textPrimary)
                    Text(g.plain, color = VL.textSecondary, modifier = Modifier.padding(top = 4.dp))
                    if (state.expertMode) Text(g.expert, style = MaterialTheme.typography.bodySmall, color = VL.textTertiary, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
    }
}
