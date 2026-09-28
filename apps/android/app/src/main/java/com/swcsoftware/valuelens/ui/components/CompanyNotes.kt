package com.swcsoftware.valuelens.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swcsoftware.valuelens.AppState
import com.swcsoftware.valuelens.data.CompanyNote
import com.swcsoftware.valuelens.domain.CompanyRef
import com.swcsoftware.valuelens.ui.displayName
import com.swcsoftware.valuelens.ui.theme.VL
import java.text.DateFormat
import java.util.Date

/** Provided at the app root; absent (null) in previews and tests, where the notes section is omitted. */
val LocalNotes = compositionLocalOf<AppState?> { null }

/** Mirrors iOS `NoteDates.caption`: "Sep 28, 2026" or "Written Sep 20, 2026 · edited Sep 28, 2026". */
fun noteCaption(n: CompanyNote): String {
    val f = DateFormat.getDateInstance(DateFormat.MEDIUM)
    val written = f.format(Date(n.createdAt)); val edited = f.format(Date(n.updatedAt))
    return if (written == edited) written else "Written $written · edited $edited"
}

/**
 * "Your notes" on a company page (Sprint 9): the user's private notes about this company, newest first,
 * with add / edit / delete. Placed by both layouts just above the disclaimer; the same component either way.
 */
@Composable
fun CompanyNotesSection(company: CompanyRef) {
    val state = LocalNotes.current ?: return
    val notes = state.notesFor(company.ticker)
    var editing by remember { mutableStateOf<CompanyNote?>(null) }
    var adding by remember { mutableStateOf(false) }
    Card {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text("Your notes", color = VL.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text("Private — kept on this device only.", color = VL.textTertiary, fontSize = 12.sp)
            }
            TextButton({ adding = true }) { Text("✎ Add", color = VL.accent) }
        }
        if (notes.isEmpty()) {
            Text("Write down why you're watching ${company.ticker}, what would change your mind, or anything to check next quarter.",
                color = VL.textSecondary, fontSize = 13.sp,
                modifier = Modifier.padding(top = 8.dp).fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(VL.raised)
                    .clickable { adding = true }.padding(12.dp))
        } else Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            notes.forEach { n ->
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(VL.raised).clickable { editing = n }.padding(12.dp)) {
                    Text(n.text, color = VL.textPrimary, fontSize = 15.sp, maxLines = 4)
                    Text(noteCaption(n), color = VL.textTertiary, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
    if (adding) NoteEditorSheet(company.ticker, initial = "", existing = false,
        onSave = { state.addNote(company.ticker, company.displayName, it); adding = false }, onDelete = {}, onDismiss = { adding = false })
    editing?.let { n ->
        NoteEditorSheet(company.ticker, initial = n.text, existing = true,
            onSave = { state.updateNote(n.id, it); editing = null }, onDelete = { state.deleteNote(n.id); editing = null }, onDismiss = { editing = null })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteEditorSheet(ticker: String, initial: String, existing: Boolean, onSave: (String) -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    var confirmDelete by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = VL.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).imePadding().navigationBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onDismiss) { Text("Cancel", color = VL.accent) }
                Text(ticker, color = VL.textPrimary, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                // An emptied existing note is deleted on save, as on iOS.
                TextButton({ onSave(text) }, enabled = existing || text.isNotBlank()) { Text("Save", color = VL.accent) }
            }
            OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth().heightIn(min = 180.dp), placeholder = { Text("Your note about $ticker", color = VL.textTertiary) },
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = VL.textPrimary, unfocusedTextColor = VL.textPrimary, focusedBorderColor = VL.accent, unfocusedBorderColor = VL.border))
            if (existing) TextButton({ confirmDelete = true }, Modifier.padding(vertical = 8.dp)) { Text("Delete note", color = VL.danger) }
        }
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false },
        title = { Text("Delete this note?") }, text = { Text("It is stored only on this device, so it can't be recovered.") },
        confirmButton = { TextButton({ confirmDelete = false; onDelete() }) { Text("Delete", color = VL.danger) } },
        dismissButton = { TextButton({ confirmDelete = false }) { Text("Cancel") } })
}
