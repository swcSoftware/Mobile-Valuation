package com.swcsoftware.valuelens.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The words ValueLens shows for SEC tags and model keys — "Earnings per share (diluted)", never
 * `us-gaap:EarningsPerShareDiluted` (Sprint 6).
 *
 * Every label comes from `packages/valuation-core/labels/display-labels.json`, compiled in at build
 * time. That file is deliberately separate from the concept map: `Concepts.kt` decides which tags are
 * *read*, and changing it changes valuations; this decides only what they are *called*, so it can be
 * edited freely. Display only — search, the concept-map issue report and anything used as a lookup
 * key keep the raw form. The raw tags are listed on the Index page (`index()`), for anyone checking
 * a number against the filing.
 *
 * Curated first, automatic second: `DisplayLabelsTest` fails if a tag the concept map can read or a key
 * a model emits has no entry, so the automatic fallback only ever handles tags a company files that we
 * do not read (the "unmatched line items" card).
 */
object DisplayLabels {

    @Serializable
    internal data class LabelFile(
        val version: String,
        val howToEdit: List<String> = emptyList(),
        val properNouns: List<String> = emptyList(),
        val tags: Map<String, String>,
        val concepts: Map<String, String>,
        val inputs: Map<String, String>,
        val inputsByMetric: Map<String, Map<String, String>> = emptyMap(),
        val formulaTerms: Map<String, String> = emptyMap(),
    )

    internal val file: LabelFile by lazy { Json { ignoreUnknownKeys = false }.decodeFromString(LabelFile.serializer(), DISPLAY_LABELS_JSON) }

    val version: String get() = file.version

    private val TAXONOMIES = listOf("us-gaap", "dei", "srt", "ifrs-full", "valuelens")

    // ---- single values -------------------------------------------------------------------

    /** `us-gaap:EarningsPerShareDiluted` → "Earnings per share (diluted)". Unlisted tags are split into words. */
    fun tag(raw: String): String = file.tags[raw] ?: humanizeTag(raw)

    /** A concept-map key (`long_term_debt`) → "Long-term debt". */
    fun concept(key: String): String = file.concepts[key] ?: humanizeKey(key)

    /**
     * A model input key → its label. `metric` picks a per-metric meaning where one exists: inside
     * Stage-1 growth, `revenue` is a five-year growth rate, not dollars of revenue.
     */
    fun input(key: String, metric: String? = null): String {
        metric?.let { m -> file.inputsByMetric[m]?.get(key)?.let { return it } }
        file.inputs[key]?.let { return it }
        FISCAL_YEAR.matchEntire(key)?.let { return "Fiscal ${it.groupValues[1]}" }
        return humanizeKey(key)
    }

    fun hasTag(raw: String) = raw in file.tags
    fun hasInput(key: String) = key in file.inputs || FISCAL_YEAR.matches(key)

    private val FISCAL_YEAR = Regex("^FY(\\d{4})$")

    // ---- text ----------------------------------------------------------------------------

    /**
     * Rewrites machine text inside a sentence — warnings, data-check messages, source notes:
     * `us-gaap:Foo` tags become their labels, and `snake_case` keys become words
     * ("no longer reported: long-term debt").
     *
     * Only keys containing an underscore are touched. Plain words are never replaced, because they
     * collide with English: "shares" and "terminal" are both keys and ordinary words in a warning.
     */
    fun sentence(text: String): String = replaceTokens(replaceTags(text)) { token ->
        if ('_' !in token) null
        else (file.concepts[token] ?: file.inputs[token])?.let(::inSentence)
    }

    /**
     * Rewrites a formula: everything `sentence` does, plus the formula-only abbreviations listed in
     * the file (`fcf`, `ffo`). Uses generic input labels — a formula states the rule, not one metric's
     * reading of it.
     */
    fun formula(text: String): String = replaceTokens(replaceTags(text)) { token ->
        file.formulaTerms[token]
            ?: if ('_' in token) (file.concepts[token] ?: file.inputs[token])?.let(::inSentence) else null
    }

    /** A label as it reads mid-sentence: "Net income" → "net income", but "Graham value…" and "EPS" keep capitals. */
    internal fun inSentence(label: String): String {
        val first = label.substringBefore(' ')
        val keep = first in file.properNouns || first.drop(1).any { it.isUpperCase() } || first.length <= 1
        return if (keep) label else label[0].lowercaseChar() + label.substring(1)
    }

    // ---- the Index page ------------------------------------------------------------------

    /**
     * Every term the app shows, with the SEC tags behind it in the order the concept map tries them.
     * The first tag a company actually filed is the one used; the rest are alternatives.
     */
    fun index(): List<IndexEntry> = Concepts.ALL.map { c ->
        IndexEntry(
            term = concept(c.key),
            key = c.key,
            tags = c.tags.map { t -> "${c.taxonomy}:$t" }.map { IndexTag(it, tag(it)) },
        )
    } + IndexEntry(
        term = tag("valuelens:derived"),
        key = "derived",
        tags = listOf(IndexTag("valuelens:derived", "Not an SEC tag: ValueLens computed this from other filed figures, and the calculation is shown with the number.")),
    )

    // ---- mechanics -----------------------------------------------------------------------

    private fun replaceTags(text: String): String {
        if (TAXONOMIES.none { "$it:" in text }) return text
        val out = StringBuilder()
        var i = 0
        while (i < text.length) {
            val prefix = TAXONOMIES.firstOrNull { text.startsWith("$it:", i) && (i == 0 || !text[i - 1].isLetterOrDigit()) }
            if (prefix != null) {
                var j = i + prefix.length + 1
                while (j < text.length && text[j].isLetterOrDigit()) j++
                if (j > i + prefix.length + 1) { out.append(tag(text.substring(i, j))); i = j; continue }
            }
            out.append(text[i]); i++
        }
        return out.toString()
    }

    /** Scans runs of [a-z0-9_]; `lookup` returns the replacement for a whole run, or null to keep it. */
    private fun replaceTokens(text: String, lookup: (String) -> String?): String {
        val out = StringBuilder()
        var i = 0
        fun isTokenChar(c: Char) = c.isLowerCase() || c.isDigit() || c == '_'
        while (i < text.length) {
            val c = text[i]
            val boundaryBefore = i == 0 || !(text[i - 1].isLetterOrDigit() || text[i - 1] == '_')
            if (boundaryBefore && isTokenChar(c)) {
                var j = i
                while (j < text.length && isTokenChar(text[j])) j++
                val boundaryAfter = j == text.length || !(text[j].isLetterOrDigit() || text[j] == '_')
                val token = text.substring(i, j)
                val replacement = if (boundaryAfter) lookup(token) else null
                out.append(replacement ?: token)
                i = j
                continue
            }
            out.append(c); i++
        }
        return out.toString()
    }

    /** `us-gaap:SomeNewTag` → "Some new tag" — the fallback for tags a company files that nobody listed. */
    internal fun humanizeTag(raw: String): String {
        val name = raw.substringAfter(':')
        val words = mutableListOf<String>()
        var current = StringBuilder()
        for ((i, ch) in name.withIndex()) {
            val startsWord = ch.isUpperCase() && current.isNotEmpty() &&
                (!name[i - 1].isUpperCase() || (i + 1 < name.length && name[i + 1].isLowerCase()))
            if (startsWord) { words += current.toString(); current = StringBuilder() }
            current.append(ch)
        }
        if (current.isNotEmpty()) words += current.toString()
        return words.mapIndexed { i, w ->
            when {
                w.length > 1 && w.all { it.isUpperCase() || it.isDigit() } -> w   // an acronym stays one
                i == 0 -> w
                else -> w.lowercase()
            }
        }.joinToString(" ").ifEmpty { raw }
    }

    /** `some_key` → "Some key". */
    internal fun humanizeKey(key: String): String =
        key.split('_').filter { it.isNotEmpty() }.joinToString(" ").replaceFirstChar { it.uppercaseChar() }.ifEmpty { key }
}

@Serializable
data class IndexEntry(
    /** The term as the app shows it. */
    val term: String,
    /** The concept-map key, stable for identity in lists. */
    val key: String,
    /** The tags behind it, in the order they are tried. */
    val tags: List<IndexTag>,
)

@Serializable
data class IndexTag(val tag: String, val label: String)
