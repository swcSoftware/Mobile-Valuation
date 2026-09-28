package com.swcsoftware.valuelens

import com.swcsoftware.valuelens.data.NotesStore
import com.swcsoftware.valuelens.export.wrapText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Sprint 9: private notes per company. Mirrors iOS NotesTests for the store; the PDF wrap is Android-only. */
class NotesTest {
    private fun store() = NotesStore(File.createTempFile("notes", ".json").apply { delete(); deleteOnExit() })

    @Test fun notesBelongToTheirCompanyNewestFirst() {
        val s = store()
        s.add("mcd", "McDonald's", "First thought", now = 1_000)
        s.add("MCD", "McDonald's", "Second thought", now = 2_000)
        s.add("KO", "Coca-Cola", "Different company", now = 1_000)
        assertEquals(listOf("Second thought", "First thought"), s.notesFor("MCD").map { it.text })
        assertEquals(1, s.notesFor("ko").size)
        assertTrue(s.notesFor("AAPL").isEmpty())
    }

    @Test fun blankTextIsNotANote() {
        val s = store()
        assertNull(s.add("MCD", "McDonald's", "   \n "))
        assertTrue(s.notes.isEmpty())
    }

    @Test fun editingMovesANoteToTheTopAndClearingItDeletesIt() {
        val s = store()
        val a = s.add("MCD", "McDonald's", "A", now = 1_000)!!
        s.add("MCD", "McDonald's", "B", now = 2_000)
        s.update(a.id, "A, revised", now = 3_000)
        assertEquals(listOf("A, revised", "B"), s.notesFor("MCD").map { it.text })
        assertEquals(1_000L, s.notesFor("MCD").first().createdAt)
        s.update(a.id, "  ")
        assertEquals(listOf("B"), s.notesFor("MCD").map { it.text })
    }

    @Test fun notesSurviveARestartAndDeleteIsPermanent() {
        val f = File.createTempFile("notes", ".json").apply { delete(); deleteOnExit() }
        val s = NotesStore(f)
        val keep = s.add("MCD", "McDonald's", "Keep me")!!
        val gone = s.add("MCD", "McDonald's", "Delete me")!!
        s.delete(gone.id)
        assertEquals(listOf(keep.id), NotesStore(f).notesFor("MCD").map { it.id })
    }

    @Test fun pdfWrapKeepsLineBreaksAndNeverOverflows() {
        val measure = { t: String -> t.length * 5f }   // 5 pt per character; a 50 pt line holds 10
        val lines = wrapText("one two three four\nfive supercalifragilistic", 50f, measure)
        assertEquals(listOf("one two", "three four", "five", "supercalif", "ragilistic"), lines)
        assertTrue(lines.all { measure(it) <= 50f })
        assertEquals(listOf(""), wrapText("", 50f, measure))
    }
}
