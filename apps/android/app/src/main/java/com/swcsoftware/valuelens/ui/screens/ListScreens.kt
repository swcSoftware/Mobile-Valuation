package com.swcsoftware.valuelens.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swcsoftware.valuelens.AppState
import com.swcsoftware.valuelens.data.WatchlistEntry
import com.swcsoftware.valuelens.domain.CompanyRef
import com.swcsoftware.valuelens.domain.verdictEnum
import com.swcsoftware.valuelens.ui.Fmt
import com.swcsoftware.valuelens.ui.components.Card
import com.swcsoftware.valuelens.ui.theme.VL
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun WatchlistScreen(state: AppState, onOpen: (CompanyRef) -> Unit, onSearch: () -> Unit) {
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(VL.background)) {
        Row(Modifier.fillMaxWidth().padding(20.dp, 28.dp, 20.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Watchlist", style = MaterialTheme.typography.displaySmall, color = VL.textPrimary, modifier = Modifier.weight(1f))
            if (refreshing) CircularProgressIndicator(Modifier.width(20.dp).height(20.dp), strokeWidth = 2.dp)
            else if (!state.engineReachable) Text("offline", color = VL.warning, fontSize = 12.sp)
            else if (state.watchlist.isNotEmpty()) TextButton({ scope.launch { refreshing = true; state.refreshWatchlist(); refreshing = false } }) { Text("Refresh", color = VL.value) }
        }
        if (state.watchlist.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text("No companies yet", style = MaterialTheme.typography.titleMedium, color = VL.textPrimary)
                Text("Search for a ticker to value it from its SEC filings.", color = VL.textSecondary, modifier = Modifier.padding(top = 6.dp))
                TextButton(onSearch) { Text("Search", color = VL.value) }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 4.dp, 16.dp, 24.dp)) {
                items(state.watchlist, key = { it.company.ticker }) { e -> WatchlistRow(e, onOpen = { onOpen(e.company) }, onRemove = { state.removeFromWatchlist(e.company.ticker) }) }
                item { Text(Fmt.relative(state.watchlistStore.lastUpdated), style = MaterialTheme.typography.bodySmall, color = VL.textTertiary, modifier = Modifier.fillMaxWidth().padding(12.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
            }
        }
    }
}

@Composable
private fun WatchlistRow(e: WatchlistEntry, onOpen: () -> Unit, onRemove: () -> Unit) {
    val mos = e.lastReport?.modelA?.marginOfSafety
    Card(Modifier.padding(bottom = 10.dp).clickable(onClick = onOpen), padding = 14) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(e.company.ticker, style = MaterialTheme.typography.titleMedium, color = VL.textPrimary)
                Text(e.company.name, style = MaterialTheme.typography.bodySmall, color = VL.textSecondary, maxLines = 1)
            }
            Column(horizontalAlignment = Alignment.End) {
                Row { Text(Fmt.money(mos?.marketPrice), color = VL.price); Text(" vs ", color = VL.textTertiary, fontSize = 11.sp, modifier = Modifier.align(Alignment.CenterVertically)); Text(Fmt.money(mos?.intrinsicValue), color = VL.value) }
                mos?.verdictEnum?.let { Text(it.title, fontSize = 11.sp, color = VL.verdictColor(it)) }
            }
            TextButton(onRemove) { Text("✕", color = VL.textTertiary) }
        }
    }
}

@Composable
fun SearchScreen(state: AppState, onOpen: (CompanyRef) -> Unit) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<CompanyRef>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    fun run(q: String) {
        query = q; job?.cancel()
        if (q.isBlank()) { results = emptyList(); return }
        job = scope.launch {
            delay(300); searching = true
            runCatching { state.repository.search(q) }.onSuccess { results = it; error = null }.onFailure { results = emptyList(); error = it.message }
            searching = false
        }
    }
    Column(Modifier.fillMaxSize().background(VL.background).padding(20.dp, 28.dp, 20.dp, 0.dp)) {
        Text("Search", style = MaterialTheme.typography.displaySmall, color = VL.textPrimary)
        OutlinedTextField(query, ::run, placeholder = { Text("Ticker or company name") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp))
        if (query.isBlank()) {
            Text("TRY", style = MaterialTheme.typography.labelSmall, color = VL.textSecondary, modifier = Modifier.padding(top = 8.dp, bottom = 6.dp))
            Card(padding = 0) { listOf("AAPL", "KO", "MSFT", "JNJ", "PG", "BRK-B").forEach { t -> Text(t, color = VL.value, modifier = Modifier.fillMaxWidth().clickable { run(t) }.padding(14.dp)) } }
        } else if (searching) {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (results.isEmpty()) {
            Text(error ?: "No matches in SEC's company list.", color = VL.textSecondary, modifier = Modifier.padding(8.dp))
        } else {
            LazyColumn { items(results, key = { it.cik }) { c ->
                Card(Modifier.padding(bottom = 10.dp).clickable { onOpen(c) }, padding = 14) {
                    Text(c.ticker, style = MaterialTheme.typography.titleMedium, color = VL.textPrimary)
                    Text(c.name, style = MaterialTheme.typography.bodySmall, color = VL.textSecondary)
                }
            } }
        }
    }
}
