package com.swcsoftware.valuelens.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A run of text; `key` names the glossary entry it links to, or null for plain text. */
@Serializable
data class TermSpan(val text: String, val key: String? = null)

/**
 * Every term the app can define (Sprint 8), loaded from `labels/glossary.json` — owner-editable, compiled
 * in like the display labels — and the linker that turns a sentence or formula into tappable terms.
 *
 * Linking lives in the core so iOS and Android underline the same words (CLAUDE.md non-negotiable 7).
 * Hand-tokenised: no `\b`, no lookarounds (Kotlin/Native).
 */
object Glossary {
    @Serializable private class File(val version: String, val entries: List<GlossaryEntry>)

    private val file: File = Json { ignoreUnknownKeys = true }.decodeFromString(File.serializer(), GLOSSARY_JSON)
    val version: String get() = file.version
    val entries: List<GlossaryEntry> get() = file.entries

    fun entry(key: String): GlossaryEntry? = entries.firstOrNull { it.key == key }

    private class Matcher(val alias: String, val key: String, val caseSensitive: Boolean)

    /** Longest alias first, so "NNWC" wins over "NWC" and "free cash flow to the firm" over "free cash flow". */
    private val matchers: List<Matcher> by lazy {
        entries.flatMap { e -> e.aliases.map { Matcher(it, e.key, it.any { c -> c.isUpperCase() }) } }
            .sortedByDescending { it.alias.length }
    }

    /** `_` is a boundary: "FCFF" links in the subscripted `FCFF_t`. */
    private fun wordChar(c: Char) = c.isLetterOrDigit()

    /**
     * Split `text` into plain and linked spans. Only the first mention of each term is linked, so a
     * paragraph isn't a wall of underlines. Joining the spans' text always gives back `text` exactly.
     */
    fun link(text: String): List<TermSpan> {
        val out = mutableListOf<TermSpan>()
        val plain = StringBuilder()
        val linked = HashSet<String>()
        var i = 0
        while (i < text.length) {
            val m = if (i > 0 && wordChar(text[i - 1]) && wordChar(text[i])) null else matchers.firstOrNull { m ->
                val end = i + m.alias.length
                end <= text.length &&
                    text.regionMatches(i, m.alias, 0, m.alias.length, ignoreCase = !m.caseSensitive) &&
                    !(end < text.length && wordChar(text[end]) && wordChar(m.alias.last()))
            }
            if (m == null) { plain.append(text[i]); i++; continue }
            val word = text.substring(i, i + m.alias.length)
            if (m.key in linked) plain.append(word)
            else {
                if (plain.isNotEmpty()) { out += TermSpan(plain.toString()); plain.clear() }
                out += TermSpan(word, m.key)
                linked += m.key
            }
            i += m.alias.length
        }
        if (plain.isNotEmpty()) out += TermSpan(plain.toString())
        return out
    }
}
