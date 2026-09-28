package com.swcsoftware.valuelens.export

/**
 * Greedy word wrap for the PDF canvas, which draws one line at a time (Sprint 9: notes are free text).
 * Keeps the writer's own line breaks, and splits a single word wider than the line rather than letting
 * it run off the page. `measure` is the paint's width function, so this stays testable on the JVM.
 */
fun wrapText(text: String, maxWidth: Float, measure: (String) -> Float): List<String> {
    val out = mutableListOf<String>()
    for (paragraph in text.split('\n')) {
        var line = ""
        for (word in paragraph.split(' ').filter { it.isNotEmpty() }) {
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (measure(candidate) <= maxWidth) { line = candidate; continue }
            if (line.isNotEmpty()) out += line
            var rest = word
            while (measure(rest) > maxWidth && rest.length > 1) {
                var cut = rest.length - 1
                while (cut > 1 && measure(rest.substring(0, cut)) > maxWidth) cut--
                out += rest.substring(0, cut); rest = rest.substring(cut)
            }
            line = rest
        }
        out += line
    }
    return out
}
