package com.swcsoftware.valuelens.ui.components

import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.swcsoftware.valuelens.core.DisplayLabels
import com.swcsoftware.valuelens.data.FilingsDownloader
import com.swcsoftware.valuelens.domain.FilingDocument
import com.swcsoftware.valuelens.ui.theme.VL

/** Provided by the company screen; null elsewhere (previews, tests), where the card is omitted. */
val LocalFilings = compositionLocalOf<FilingsDownloader?> { null }

private fun mb(bytes: Long) = if (bytes >= 1_048_576) "%.1f MB".format(bytes / 1_048_576.0) else "${bytes / 1024} KB"

/** "SEC filings behind these numbers" (Sprint 9; mirror of iOS FilingsUsedCard). Placed by both layouts. */
@Composable
fun FilingsUsedCard() {
    val d = LocalFilings.current ?: return
    if (d.documents.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    Card(Modifier.clickable { open = true }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("SEC filings behind these numbers", color = VL.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text(if (d.isFinished) "${d.documents.size} filings · downloaded for this visit (${mb(d.downloadedBytes)}) · deleted when you leave"
                     else "${d.documents.size} filings · downloading ${d.downloadedCount} of ${d.downloadableCount}…", color = VL.textSecondary, fontSize = 12.sp)
            }
            Text("›", color = VL.textTertiary, fontSize = 22.sp, modifier = Modifier.padding(start = 12.dp))
        }
    }
    if (open) FilingsListSheet(d) { open = false }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilingsListSheet(d: FilingsDownloader, onDismiss: () -> Unit) {
    var reading by remember { mutableStateOf<FilingDocument?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = VL.surface) {
        Text("Filings used", color = VL.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, modifier = Modifier.padding(horizontal = 16.dp))
        LazyColumn(Modifier.navigationBarsPadding()) {
            items(d.documents, key = { it.filing.accession }) { doc ->
                val status = d.status[doc.filing.accession]
                Column(Modifier.fillMaxWidth().clickable(enabled = d.localFile(doc) != null || doc.documentUrl == null) { reading = doc }.padding(16.dp, 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(doc.title, color = VL.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        when (status) {
                            is FilingsDownloader.Status.Done -> Text(mb(status.bytes), color = VL.textTertiary, fontSize = 11.sp)
                            FilingsDownloader.Status.Downloading -> CircularProgressIndicator(Modifier.padding(start = 8.dp).size(18.dp), strokeWidth = 2.dp)
                            FilingsDownloader.Status.Failed -> Text("Couldn't download", color = VL.warning, fontSize = 11.sp)
                            FilingsDownloader.Status.NoDocument -> Text("On SEC.gov", color = VL.textTertiary, fontSize = 11.sp)
                            else -> Text("Waiting", color = VL.textTertiary, fontSize = 11.sp)
                        }
                    }
                    Text("Filed ${doc.filing.filed} · supplied ${doc.supplied} figures", color = VL.textSecondary, fontSize = 12.sp)
                    Text(doc.filing.accession, color = VL.textTertiary, fontSize = 11.sp)
                    Text(doc.filing.concepts.joinToString(", ") { DisplayLabels.concept(it) }, color = VL.textTertiary, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                HorizontalDivider(color = VL.border)
            }
            item {
                Text("Every figure Alpha read from SEC EDGAR came from one of these filings. \"Supplied\" is the years Alpha took from each: when a later filing restates a year, Alpha uses the newer figure, so a 10-K often supplies only earlier years. Downloaded when you opened this company; deleted when you leave it.",
                    color = VL.textTertiary, fontSize = 12.sp, modifier = Modifier.padding(16.dp))
            }
        }
    }
    reading?.let { doc -> FilingReader(doc, d.localFile(doc)) { reading = null } }
}

/** The downloaded filing in a WebView; images and styles load from the filing's folder on sec.gov. */
@Composable
private fun FilingReader(doc: FilingDocument, file: java.io.File?, onClose: () -> Unit) {
    val uri = LocalUriHandler.current
    val original = doc.documentUrl ?: doc.indexUrl
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(VL.background)) {
            Row(Modifier.fillMaxWidth().background(VL.surface).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClose) { Text("‹ Back", color = VL.accent) }
                Text("${doc.filing.form} · ${doc.reportDate ?: doc.filing.filed}", color = VL.textPrimary, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                TextButton({ uri.openUri(original) }) { Text("Open on SEC.gov", color = VL.accent) }
            }
            if (file == null) Text("EDGAR's filing list doesn't name this filing's main document. Open it on SEC.gov instead.", color = VL.textSecondary, modifier = Modifier.padding(24.dp))
            else AndroidView(factory = { ctx ->
                WebView(ctx).apply {
                    settings.builtInZoomControls = true; settings.displayZoomControls = false; settings.loadWithOverviewMode = true; settings.useWideViewPort = true
                    loadDataWithBaseURL(original.substringBeforeLast('/') + "/", file.readText(), "text/html", "utf-8", null)
                }
            }, modifier = Modifier.fillMaxSize())
        }
    }
}
