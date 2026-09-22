package com.swcsoftware.valuelens.core

import com.swcsoftware.valuelens.domain.CoverageGap
import com.swcsoftware.valuelens.domain.CoverageReport

/**
 * Concept-map coverage (Sprint 4, Track A).
 *
 * The XBRL tag map is hand-curated, so a filer can use an element we don't list and the concept
 * silently goes missing. This turns that into a structured, reviewable report: which canonical
 * concept is unresolved, and which tags the filer *does* use that look related.
 *
 * Detection only — nothing here changes a valuation, and the map never updates itself
 * (see CLAUDE.md "non-negotiables" and docs/TASKS.md Sprint 4 Track A).
 */
object Coverage {
    /**
     * Concepts a given filer isn't expected to report, by sector — absence here is normal, not a gap.
     * Banks and insurers have unclassified balance sheets and no meaningful capex or inventory;
     * REITs report property sales that other filers never have.
     */
    private val NOT_EXPECTED_BY_SECTOR = mapOf(
        SectorMode.FINANCIAL to setOf("capex", "d_and_a", "cost_of_revenue", "inventory", "current_assets", "current_liabilities", "operating_income"),
        SectorMode.REIT to setOf("cost_of_revenue", "inventory"),
        SectorMode.GENERAL to emptySet(),
    )

    /** Effective requirement for this filer: the map's level, downgraded where the sector explains the absence. */
    internal fun requirementFor(c: Concept, mode: SectorMode): String =
        if (c.key in (NOT_EXPECTED_BY_SECTOR[mode] ?: emptySet())) "optional" else c.requirement

    /** Words too generic to identify a concept when matching a filer's unmapped tags. */
    private val STOPWORDS = setOf("of", "and", "the", "net", "total", "current", "noncurrent", "other", "common", "stock",
        "value", "amount", "attributable", "including", "excluding", "portion", "at", "carrying", "for", "to", "from", "in", "by", "per")

    /** "RevenueFromContractWithCustomer" → [revenue, from, contract, with, customer] */
    internal fun words(tag: String): List<String> =
        Regex("(?<=[a-z0-9])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])").split(tag.substringAfter(":")).map { it.lowercase() }.filter { it.length > 2 }

    /** Crude singular form so "revenues" and "revenue" match. */
    internal fun stem(w: String): String = if (w.length > 4 && w.endsWith("s") && !w.endsWith("ss")) w.dropLast(1) else w

    /**
     * The head noun identifies the concept: `InterestExpense` is not revenue even though our
     * `RevenuesNetOfInterestExpense` tag contains the words "interest" and "expense".
     */
    internal fun head(tag: String): String? = words(tag).firstOrNull { it !in STOPWORDS }?.let { stem(it) }

    internal fun heads(c: Concept): Set<String> = c.tags.mapNotNull { head(it) }.toSet()

    /** Distinctive words for a concept, from the tags we already map. */
    internal fun keywords(c: Concept): Set<String> = c.tags.flatMap { words(it) }.filter { it !in STOPWORDS }.map { stem(it) }.toSet()

    /**
     * Tags the filer actually reports that share a distinctive word with the concept — candidates a
     * reviewer can judge. Ranked by how many distinctive words they share.
     */
    internal fun candidates(cf: CompanyFacts, c: Concept, limit: Int = 6): List<String> {
        val kw = keywords(c)
        val hd = heads(c)
        if (kw.isEmpty() || hd.isEmpty()) return emptyList()
        val mapped = c.tags.map { "${c.taxonomy}:$it" }.toSet()
        return cf.tagKeys.asSequence()
            .filter { it !in mapped && head(it) in hd }          // same head noun, or it is a different concept
            .filter { key ->                                     // and the right kind of fact, in the right unit
                val facts = cf.factsFor(key)
                facts.any { (c.kind == Kind.FLOW) == !it.isInstant && it.unit == c.unit }
            }
            .map { it to words(it).map { w -> stem(w) }.count { w -> w in kw } }
            .filter { it.second > 0 }
            .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first.length })
            .take(limit).map { "${it.first} (${it.second} word${if (it.second > 1) "s" else ""} in common)" }
            .toList()
    }

    /**
     * Gaps for one company. `fin` supplies what resolved; `cf` supplies what the filer reports.
     * Only concepts the models actually consume are reported.
     */
    fun analyze(fin: NormalizedFinancials, cf: CompanyFacts, appVersion: String, mode: SectorMode = SectorMode.GENERAL,
                mapVersion: String = Concepts.VERSION): CoverageReport {
        val latest = fin.latestAnnual
        val ttm = fin.ttm
        val gaps = mutableListOf<CoverageGap>()

        for (c in Concepts.ALL) {
            if (c.key == "shares_outstanding") continue          // handled by the share-class path
            val requirement = requirementFor(c, mode)
            if (requirement == "optional") continue              // legitimately absent for many filers
            val inAnnual = latest?.values?.containsKey(c.key) == true
            val inTtm = ttm?.values?.containsKey(c.key) == true
            val filerHasAnyMappedTag = c.tags.any { cf.get(c.taxonomy, it).isNotEmpty() }
            when {
                !inAnnual && !inTtm && !filerHasAnyMappedTag ->
                    gaps += CoverageGap(c.key, c.label, "missing", c.statement, candidates(cf, c), requirement == "required", requirement)
                !inAnnual && !inTtm ->
                    // The filer uses a mapped tag, but not in a form the normalizer accepts (wrong period, unit or form).
                    gaps += CoverageGap(c.key, c.label, "unusable", c.statement, c.tags.filter { cf.get(c.taxonomy, it).isNotEmpty() }.map { "${c.taxonomy}:$it (reported, not in an annual/TTM context)" }, requirement == "required", requirement)
                inAnnual && !inTtm ->
                    gaps += CoverageGap(c.key, c.label, "stale", c.statement, emptyList(), false, requirement)
            }
        }
        return CoverageReport(
            ticker = fin.ticker, cik = fin.cik, company = fin.name,
            period = ttm?.periodEnd?.toString() ?: latest?.periodEnd?.toString() ?: "",
            appVersion = appVersion, mapVersion = mapVersion, gaps = gaps.sortedByDescending { it.critical },
        )
    }

    /** Markdown body for a GitHub issue — reviewed by the user before they submit it. */
    fun issueBody(r: CoverageReport): String = buildString {
        appendLine("Automatic concept-map gap report from the ValueLens app.")
        appendLine()
        appendLine("| | |")
        appendLine("|---|---|")
        appendLine("| Company | ${r.company} |")
        appendLine("| Ticker | ${r.ticker} |")
        appendLine("| CIK | ${r.cik} |")
        appendLine("| Latest period | ${r.period} |")
        appendLine("| App / map version | ${r.appVersion} / ${r.mapVersion} |")
        appendLine()
        if (r.gaps.isEmpty()) { appendLine("No unresolved concepts — filed from the \"this number looks wrong\" action."); return@buildString }
        appendLine("## Unresolved concepts")
        for (g in r.gaps) {
            appendLine()
            appendLine("### ${g.label} (`${g.concept}`) — ${g.kind}${if (g.critical) " **critical**" else ""}")
            appendLine("Statement: ${g.statement}")
            if (g.candidates.isEmpty()) appendLine("No related tags found in this filer's facts.")
            else {
                appendLine("Tags this filer reports that may match:")
                g.candidates.forEach { appendLine("- `$it`") }
            }
        }
        appendLine()
        appendLine("---")
        appendLine("Review before mapping: a similar name is not the same meaning (`RevenuesNetOfInterestExpense` ≠ `Revenues` for a bank).")
        appendLine("If mapped, update **both** `Concepts.kt` and `tags.py`, regenerate the oracle, and add a test asserting CIK ${r.cik} resolves.")
    }

    fun issueTitle(r: CoverageReport): String {
        val critical = r.gaps.filter { it.critical }
        val subject = when {
            critical.isNotEmpty() -> critical.joinToString(", ") { it.concept }
            r.gaps.isNotEmpty() -> r.gaps.joinToString(", ") { it.concept }
            else -> "reported by user"
        }
        return "coverage: ${r.ticker} (CIK ${r.cik}) — $subject"
    }

    /** Prefilled GitHub issue URL: no token, no server, and the user sees the payload before submitting. */
    fun issueUrl(r: CoverageReport, repo: String = "swcSoftware/Mobile-Valuation"): String =
        "https://github.com/$repo/issues/new?labels=coverage&title=${encode(issueTitle(r))}&body=${encode(issueBody(r))}"

    private fun encode(s: String): String = buildString {
        for (b in s.encodeToByteArray()) {
            val c = b.toInt().toChar()
            if (c.isLetterOrDigit() && b >= 0 || c in "-_.~") append(c)
            else append('%').append(((b.toInt() and 0xFF) / 16).toString(16).uppercase()).append(((b.toInt() and 0xFF) % 16).toString(16).uppercase())
        }
    }
}
