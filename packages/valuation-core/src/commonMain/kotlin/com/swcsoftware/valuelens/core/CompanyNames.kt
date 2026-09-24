package com.swcsoftware.valuelens.core

/**
 * How a company's name is *displayed*: first letter capital, the rest lower — "Merck & Co., Inc.",
 * not "MERCK & CO., INC." (owner decision, 2026-09-24; ISSUES #85).
 *
 * SEC's conformed names are mostly shouted (42 of the 77 in scripts/universe.txt), and many carry a
 * state-of-incorporation marker nobody wants to read ("AMERICAN TOWER CORP /MA/", "QUALCOMM INC/DE").
 *
 * **Display only.** The filed name is evidence and is never replaced: the report keeps it, the
 * dossier and the expert header show it, the concept-map issue report sends it. This formats a
 * string for the eye.
 *
 * The rules:
 *  - A name with no lowercase at all is title-cased word by word, keeping digits (3M), ampersand
 *    words (AT&T), dotted initialisms (U.S.), roman numerals, structural acronyms (LLC, PLC, REIT)
 *    and the company's own ticker when it appears as a word (AGNC). "and", "of", "the"… go lower
 *    after the first word. "MC" + a letter becomes "Mc" + capital (McDonalds, McKesson).
 *  - A name already in mixed case is the company's own styling (AbbVie, CoreWeave, D.R. Horton) and
 *    is left alone, except for a stray shouted word of four or more letters ("NIKE, Inc.").
 *  - Brand casings that no rule can infer (JPMorgan, PepsiCo) come from a curated list — reviewed,
 *    like the concept map; `CompanyNamesTest` pins every universe name so a change is visible.
 */
object CompanyNames {
    const val VERSION = "2026-09-24"

    private val ACRONYMS = setOf(
        "LLC", "LP", "LLP", "PLC", "NV", "SA", "AG", "SE", "REIT", "USA", "US", "ETF", "ADR", "NA",
        "II", "III", "IV", "VI", "VII", "VIII", "IX", "XI", "XII",
    )
    private val BRANDS = mapOf(
        "JPMORGAN" to "JPMorgan", "PEPSICO" to "PepsiCo", "ABBVIE" to "AbbVie", "NETSUITE" to "NetSuite",
        "PAYPAL" to "PayPal", "BLACKROCK" to "BlackRock", "EBAY" to "eBay",
    )
    private val SMALL_WORDS = setOf("of", "and", "the", "for", "in", "on", "at", "a", "an", "to", "by")

    /** "/MA/", "/NEW", "/DE" at the end of a token. Token-level, no lookahead (Kotlin/Native safe). */
    private val STATE_SUFFIX = Regex("/[A-Z]{2,3}/?$")
    private val TOKEN = Regex("^([^A-Za-z0-9&]*)(.*?)([^A-Za-z0-9&]*)$")
    private val DOTTED_INITIALISM = Regex("^(?:[A-Z]\\.)+[A-Z]?$")

    fun display(filed: String, ticker: String? = null): String {
        val tokens = filed.replace(' ', ' ').split(' ')
            .filter { it.isNotEmpty() }
            .mapNotNull { t -> t.replace(STATE_SUFFIX, "").trimEnd('/').ifEmpty { null } }
        if (tokens.isEmpty()) return filed.trim()
        val shouted = tokens.none { t -> t.any { it.isLowerCase() } }
        val tickerWord = ticker?.replace("-", "")?.uppercase()?.takeIf { it.length >= 3 }

        return tokens.mapIndexed { i, token ->
            val m = TOKEN.matchEntire(token) ?: return@mapIndexed token
            val (lead, core, trail) = m.destructured
            if (core.isEmpty()) return@mapIndexed token
            val word = if (shouted) {
                when {
                    keep(core, tickerWord) -> core
                    i > 0 && core.lowercase() in SMALL_WORDS -> core.lowercase()
                    else -> titleWord(core)
                }
            } else {
                val shoutedWord = core.none { it.isLowerCase() } && core.count { it.isUpperCase() } >= 4
                if (shoutedWord && !keep(core, tickerWord)) titleWord(core) else core
            }
            lead + word + trail
        }.joinToString(" ")
    }

    private fun keep(core: String, tickerWord: String?): Boolean =
        core.any { it.isDigit() } || '&' in core || DOTTED_INITIALISM.matches(core) ||
            core in ACRONYMS || (tickerWord != null && core == tickerWord)

    private fun titleWord(core: String): String =
        BRANDS[core] ?: core.split('-').joinToString("-") { part ->
            part.split('\'').mapIndexed { i, piece -> if (i == 0) titlePart(piece) else piece.lowercase() }
                .joinToString("'")
        }

    private fun titlePart(p: String): String = when {
        p.isEmpty() -> p
        p.length > 3 && p.startsWith("MC") && p[2].isLetter() -> "Mc" + p[2].uppercaseChar() + p.substring(3).lowercase()
        else -> p[0].uppercaseChar() + p.substring(1).lowercase()
    }
}
