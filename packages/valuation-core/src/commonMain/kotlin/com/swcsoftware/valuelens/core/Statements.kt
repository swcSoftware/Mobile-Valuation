package com.swcsoftware.valuelens.core

import kotlin.math.abs
import kotlin.math.pow

/**
 * Normalized annual (10-K) and TTM (10-K + 10-Q) statements. Faithful port of
 * services/valuation-engine/valuation_engine/normalize/statements.py — keep the two in sync;
 * the oracle test diffs their outputs.
 */
class SourcedValueCore(
    var value: Double, val tag: String, val taxonomy: String, val accession: String, val form: String,
    val periodEnd: Day, val periodStart: Day?, val filed: Day, var derived: Boolean = false, var note: String = "",
) {
    val qualifiedTag: String get() = "$taxonomy:$tag"
}

class PeriodCore(val label: String, var periodEnd: Day, val fiscalYear: Int?, var form: String) {
    val values: LinkedHashMap<String, SourcedValueCore> = LinkedHashMap()
    fun get(key: String): Double? = values[key]?.value
}

class NormalizedFinancials(
    val ticker: String, val cik: Long, val name: String,
    val annual: List<PeriodCore>, val ttm: PeriodCore?, val currentShares: SourcedValueCore?, val warnings: List<String>,
) {
    val latestAnnual: PeriodCore? get() = annual.lastOrNull()
}

object Statements {
    private val ANNUAL_FORMS = setOf("10-K", "10-K/A", "10-KT", "10-K405")
    private val QUARTERLY_FORMS = setOf("10-Q", "10-Q/A", "10-QT")
    private const val MIN_ANNUAL_DAYS = 340L
    private const val MAX_ANNUAL_DAYS = 380L

    private fun latestFiled(facts: List<Fact>): Fact = facts.maxWith(compareBy<Fact> { it.filed }.thenBy { it.end })

    private fun toSv(f: Fact, derived: Boolean = false, note: String = "") =
        SourcedValueCore(f.value, f.tag, f.taxonomy, f.accession, f.form, f.end, f.start, f.filed, derived, note)

    private fun conceptFacts(cf: CompanyFacts, c: Concept): List<Pair<String, List<Fact>>> =
        c.tags.mapNotNull { tag -> cf.get(c.taxonomy, tag).filter { it.unit == c.unit }.takeIf { it.isNotEmpty() }?.let { tag to it } }

    private fun annualFlow(cf: CompanyFacts, c: Concept): LinkedHashMap<Day, SourcedValueCore> {
        val result = LinkedHashMap<Day, SourcedValueCore>()
        for ((_, facts) in conceptFacts(cf, c)) {
            val byEnd = LinkedHashMap<Day, MutableList<Fact>>()
            for (f in facts) {
                if (f.form !in ANNUAL_FORMS || f.start == null) continue
                val d = f.durationDays ?: 0
                if (d in MIN_ANNUAL_DAYS..MAX_ANNUAL_DAYS) byEnd.getOrPut(f.end) { mutableListOf() } += f
            }
            for ((end, group) in byEnd) if (end !in result) result[end] = toSv(latestFiled(group))
        }
        return result
    }

    private fun annualInstant(cf: CompanyFacts, c: Concept, ends: List<Day>): LinkedHashMap<Day, SourcedValueCore> {
        val result = LinkedHashMap<Day, SourcedValueCore>()
        for ((_, facts) in conceptFacts(cf, c)) {
            val byEnd = LinkedHashMap<Day, MutableList<Fact>>()
            for (f in facts) if (f.form in ANNUAL_FORMS && f.start == null) byEnd.getOrPut(f.end) { mutableListOf() } += f
            for (end in ends) {
                if (end in result) continue
                byEnd[end]?.let { result[end] = toSv(latestFiled(it)) }
            }
        }
        return result
    }

    /** Every candidate tag is evaluated; the freshest result wins (companies switch tags over time). */
    private fun ttmFlow(cf: CompanyFacts, c: Concept, fyValue: SourcedValueCore?): SourcedValueCore? {
        val candidates = conceptFacts(cf, c).mapNotNull { (tag, facts) -> ttmFlowForTag(tag, facts, c, fyValue) }
        return candidates.maxByOrNull { it.periodEnd } ?: fyValue
    }

    private fun ttmFlowForTag(tag: String, facts: List<Fact>, c: Concept, fyValueIn: SourcedValueCore?): SourcedValueCore? {
        var fyValue = fyValueIn
        run {
            val q = facts.filter { it.form in QUARTERLY_FORMS && it.start != null }
            if (q.isEmpty()) return null
            val latestEnd = q.maxOf { it.end }
            if (fyValue != null && latestEnd <= fyValue.periodEnd) return fyValue
            val cur = q.filter { it.end == latestEnd }.maxWith(compareBy<Fact> { it.durationDays ?: 0 }.thenBy { it.filed })
            if (c.unit == "shares") return toSv(cur, note = "Weighted-average shares from latest 10-Q YTD period")
            val curStart = cur.start!!
            val priorStart = curStart - 365; val priorEnd = cur.end - 365
            val prior = facts.filter { it.start != null && it.start.near(priorStart, 10) && it.end.near(priorEnd, 10) }
            if (fyValue == null || !fyValue.periodEnd.near(curStart - 1, 8)) {
                val annual = facts.filter { it.form in ANNUAL_FORMS && it.start != null && (it.durationDays ?: 0) in MIN_ANNUAL_DAYS..MAX_ANNUAL_DAYS && it.end.near(curStart - 1, 8) }
                if (annual.isEmpty()) return null
                fyValue = toSv(latestFiled(annual))
            }
            if (prior.isEmpty()) return null
            val p = latestFiled(prior)
            val ttm = fyValue.value + cur.value - p.value
            val dec = if (c.unit == "USD/shares") 2 else 0
            var note = "TTM = FY(${fyValue.periodEnd}) ${PyFmt.commas(fyValue.value, dec)} + YTD(${curStart}→${cur.end}) ${PyFmt.commas(cur.value, dec)} − YTD(${p.start}→${p.end}) ${PyFmt.commas(p.value, dec)}"
            if (c.unit == "USD/shares") note += " (EPS TTM is an additive approximation)"
            return SourcedValueCore(ttm, tag, c.taxonomy, cur.accession, cur.form, cur.end, cur.end - 365, cur.filed, derived = true, note = note)
        }
    }

    /** Latest balance-sheet value across all candidate tags (freshest period end wins; earlier tag on ties). */
    private fun ttmInstant(cf: CompanyFacts, c: Concept): SourcedValueCore? {
        var best: SourcedValueCore? = null
        for ((_, facts) in conceptFacts(cf, c)) {
            val inst = facts.filter { it.start == null && (it.form in ANNUAL_FORMS || it.form in QUARTERLY_FORMS) }
            if (inst.isEmpty()) continue
            val latestEnd = inst.maxOf { it.end }
            if (best == null || latestEnd > best.periodEnd) best = toSv(latestFiled(inst.filter { it.end == latestEnd }))
        }
        return best
    }

    private const val NWC_RATIO_WINDOW = 5
    private const val NWC_DELTA_WINDOW = 3

    /**
     * Normalized ΔNWC (Damodaran): avg(NWC ÷ revenue over 5 yrs) × Δrevenue; fallback 3-yr mean of ΔNWC; then raw.
     * Mirror of statements.py `_normalize_delta_nwc` — keep identical (oracle-tested).
     */
    private fun normalizeDeltaNwc(period: PeriodCore, history: List<PeriodCore>, mk: (Double, String, SourcedValueCore) -> SourcedValueCore) {
        val v = period.values
        val nwc = v["nwc"] ?: return
        val prev = history.lastOrNull()
        val pool = (history + period).filter { "nwc" in it.values && (it.get("revenue") ?: 0.0) != 0.0 }
        val ratios = pool.takeLast(NWC_RATIO_WINDOW).map { (it.fiscalYear?.toString() ?: it.label) to it.values["nwc"]!!.value / it.values["revenue"]!!.value }
        val rev = period.get("revenue"); val prevRev = prev?.get("revenue")
        if (ratios.size >= 3 && rev != null && prevRev != null) {
            val avg = ratios.sumOf { it.second } / ratios.size
            val dRev = rev - prevRev
            var scale = 1.0
            if (period.fiscalYear == null && prev != null) { val days = prev.periodEnd.daysUntil(period.periodEnd); if (days in 1..364) scale = 365.0 / days }
            val detail = ratios.joinToString(", ") { "${it.first}: ${PyFmt.fixed(it.second * 100, 1)}%" }
            val note = "avg(NWC/revenue over ${ratios.size} yrs = ${PyFmt.fixed(avg * 100, 1)}% [$detail]) × Δrevenue ${PyFmt.commas(dRev, 0)}" + (if (scale != 1.0) " × ${PyFmt.fixed(scale, 2)} (annualized)" else "")
            v["delta_nwc_normalized"] = mk(avg * dRev * scale, note, nwc)
            return
        }
        val deltas = (history + period).mapNotNull { it.values["delta_nwc"]?.value }.takeLast(NWC_DELTA_WINDOW)
        if (deltas.size >= 3) v["delta_nwc_normalized"] = mk(deltas.sum() / deltas.size, "mean of last ${deltas.size} yearly ΔNWC (revenue history too short for the ratio method)", nwc)
        else v["delta_nwc"]?.let { v["delta_nwc_normalized"] = mk(it.value, "raw one-year ΔNWC (insufficient history to normalize)", nwc) }
    }

    private fun derive(period: PeriodCore, history: List<PeriodCore>) {
        val v = period.values
        val prev = history.lastOrNull()
        fun mk(value: Double, note: String, base: SourcedValueCore) =
            SourcedValueCore(value, "derived", "valuelens", base.accession, base.form, base.periodEnd, base.periodStart, base.filed, derived = true, note = note)

        if ("operating_income" !in v && "pretax_income" in v) {
            val interest = v["interest_expense"]?.let { abs(it.value) } ?: 0.0
            v["operating_income"] = mk(v["pretax_income"]!!.value + interest, "pretax_income + interest_expense (EBIT proxy; OperatingIncomeLoss not tagged)", v["pretax_income"]!!)
        }
        if ("total_liabilities" !in v && "liabilities_and_equity" in v && "equity" in v)
            v["total_liabilities"] = mk(v["liabilities_and_equity"]!!.value - v["equity"]!!.value, "LiabilitiesAndStockholdersEquity − StockholdersEquity", v["equity"]!!)
        var debt = 0.0; val parts = mutableListOf<String>()
        for (k in listOf("short_term_debt", "long_term_debt")) v[k]?.let { debt += it.value; parts += k }
        if (parts.isNotEmpty()) v["total_debt"] = mk(debt, parts.joinToString(" + "), v[parts[0]]!!)
        if ("cfo" in v && "capex" in v) v["fcf"] = mk(v["cfo"]!!.value - abs(v["capex"]!!.value), "cfo − capex", v["cfo"]!!)
        if ("current_assets" in v && "current_liabilities" in v) {
            var ca = v["current_assets"]!!.value - (v["cash"]?.value ?: 0.0)
            ca -= v["short_term_investments"]?.value ?: 0.0
            var cl = v["current_liabilities"]!!.value
            cl -= v["short_term_debt"]?.value ?: 0.0
            v["nwc"] = mk(ca - cl, "(current_assets − cash − short_term_investments) − (current_liabilities − short_term_debt)", v["current_assets"]!!)
            prev?.values?.get("nwc")?.let { pn -> v["delta_nwc"] = mk(v["nwc"]!!.value - pn.value, "nwc − nwc(${prev.periodEnd})", v["nwc"]!!) }
            normalizeDeltaNwc(period, history, ::mk)
        }
        if ("income_tax" in v && "pretax_income" in v && v["pretax_income"]!!.value > 0) {
            val rate = v["income_tax"]!!.value / v["pretax_income"]!!.value
            v["effective_tax_rate"] = mk(maxOf(0.0, minOf(rate, 0.5)), "income_tax / pretax_income (clamped 0–50%)", v["income_tax"]!!)
        }
        if ("capex" in v && "d_and_a" in v)
            v["maintenance_capex"] = mk(minOf(abs(v["capex"]!!.value), abs(v["d_and_a"]!!.value)), "min(capex, d_and_a) — conservative proxy; Buffett's 'average capex to maintain competitive position'", v["capex"]!!)
        if (listOf("net_income", "d_and_a", "maintenance_capex").all { it in v }) {
            val dwc = v["delta_nwc_normalized"]?.value ?: v["delta_nwc"]?.value ?: 0.0
            v["owner_earnings"] = mk(v["net_income"]!!.value + v["d_and_a"]!!.value - v["maintenance_capex"]!!.value - dwc, "net_income + d_and_a − maintenance_capex − delta_nwc_normalized", v["net_income"]!!)
        }
        if ("operating_income" in v) {
            val t = v["effective_tax_rate"]?.value ?: 0.21
            v["nopat"] = mk(v["operating_income"]!!.value * (1 - t), "operating_income × (1 − ${PyFmt.fixed(t, 3)})", v["operating_income"]!!)
            if ("d_and_a" in v && "capex" in v) {
                val dwc = v["delta_nwc_normalized"]?.value ?: v["delta_nwc"]?.value ?: 0.0
                v["fcff"] = mk(v["nopat"]!!.value + v["d_and_a"]!!.value - abs(v["capex"]!!.value) - dwc, "nopat + d_and_a − capex − delta_nwc_normalized", v["nopat"]!!)
            }
        }
        if ("equity" in v) {
            val ic = v["equity"]!!.value + (v["total_debt"]?.value ?: 0.0) - (v["cash"]?.value ?: 0.0)
            v["invested_capital"] = mk(ic, "equity + total_debt − cash", v["equity"]!!)
            if ("nopat" in v && ic > 0) v["roic"] = mk(v["nopat"]!!.value / ic, "nopat / invested_capital", v["nopat"]!!)
        }
        if ("equity" in v && "shares_diluted" in v && v["shares_diluted"]!!.value > 0) v["book_value_per_share"] = mk(v["equity"]!!.value / v["shares_diluted"]!!.value, "equity / shares_diluted", v["equity"]!!)
        if ("net_income" in v && "equity" in v && v["equity"]!!.value > 0) v["roe"] = mk(v["net_income"]!!.value / v["equity"]!!.value, "net_income / equity", v["net_income"]!!)
        if ("current_assets" in v && "current_liabilities" in v && v["current_liabilities"]!!.value > 0) v["current_ratio"] = mk(v["current_assets"]!!.value / v["current_liabilities"]!!.value, "current_assets / current_liabilities", v["current_assets"]!!)
        if ("total_debt" in v && "equity" in v && v["equity"]!!.value > 0) v["debt_to_equity"] = mk(v["total_debt"]!!.value / v["equity"]!!.value, "total_debt / equity", v["total_debt"]!!)
        if ("operating_income" in v && "revenue" in v && v["revenue"]!!.value > 0) v["operating_margin"] = mk(v["operating_income"]!!.value / v["revenue"]!!.value, "operating_income / revenue", v["operating_income"]!!)
        if ("net_income" in v && "revenue" in v && v["revenue"]!!.value > 0) v["net_margin"] = mk(v["net_income"]!!.value / v["revenue"]!!.value, "net_income / revenue", v["net_income"]!!)
    }

    private fun adjustForSplits(periods: List<PeriodCore>, warnings: MutableList<String>) {
        for (i in 1 until periods.size) {
            val cur = periods[i].get("shares_diluted") ?: continue
            val prev = periods[i - 1].get("shares_diluted") ?: continue
            if (cur == 0.0 || prev <= 0) continue
            val ratio = cur / prev
            if (ratio >= 1.8 || ratio <= 0.55) {
                val factor = kotlin.math.round(ratio * 2) / 2
                if (factor <= 0) continue
                warnings += "Share-count discontinuity (${PyFmt.g(factor)}×) between FY${periods[i - 1].fiscalYear} and FY${periods[i].fiscalYear} (stock split, as restated in later filings); earlier per-share data adjusted."
                for (p in periods.subList(0, i)) {
                    p.values["eps_diluted"]?.let { sv -> sv.value = sv.value / factor; sv.derived = true; sv.note = (if (sv.note.isNotEmpty()) sv.note + " " else "") + "split-adjusted ÷${PyFmt.g(factor)}" }
                    p.values["shares_diluted"]?.let { sv -> sv.value = sv.value * factor; sv.derived = true; sv.note = (if (sv.note.isNotEmpty()) sv.note + " " else "") + "split-adjusted ×${PyFmt.g(factor)}" }
                }
            }
        }
    }

    /** 52/53-week fiscal years can end on Jan 1–7 of the following calendar year (JNJ, etc.). */
    fun fiscalYearFor(end: Day): Int = if (end.month == 1 && end.day <= 7) end.year - 1 else end.year

    fun normalize(cf: CompanyFacts, maxYears: Int = 10): NormalizedFinancials {
        val warnings = mutableListOf<String>()
        val flows = Concepts.ALL.filter { it.kind == Kind.FLOW }
        val instants = Concepts.ALL.filter { it.kind == Kind.INSTANT && it.taxonomy == "us-gaap" }
        val annualFlowValues = LinkedHashMap<String, LinkedHashMap<Day, SourcedValueCore>>()
        for (c in flows) annualFlowValues[c.key] = annualFlow(cf, c)
        var ends: List<Day> = (annualFlowValues["revenue"]!!.keys + annualFlowValues["net_income"]!!.keys).toSet().sorted()
        ends = ends.takeLast(maxYears)
        if (ends.isEmpty()) warnings += "No annual 10-K income statement data found."
        val annualInstantValues = LinkedHashMap<String, LinkedHashMap<Day, SourcedValueCore>>()
        for (c in instants) annualInstantValues[c.key] = annualInstant(cf, c, ends)

        val periods = ends.map { end ->
            val fy = fiscalYearFor(end)
            PeriodCore("FY$fy", end, fy, "10-K").also { p ->
                for ((key, series) in annualFlowValues) series[end]?.let { p.values[key] = it }
                for ((key, series) in annualInstantValues) series[end]?.let { p.values[key] = it }
            }
        }
        adjustForSplits(periods, warnings)
        periods.forEachIndexed { i, p -> derive(p, periods.subList(0, i)) }

        var ttm: PeriodCore? = null
        val latest = periods.lastOrNull()
        if (latest != null) {
            val t = PeriodCore("TTM", latest.periodEnd, null, "10-K+10-Q")
            for (c in flows) ttmFlow(cf, c, latest.values[c.key])?.let { sv -> t.values[c.key] = sv; if (sv.periodEnd > t.periodEnd) t.periodEnd = sv.periodEnd }
            for (c in instants) ttmInstant(cf, c)?.let { t.values[c.key] = it }
            val stale = t.values.filter { (_, sv) -> sv.periodEnd.daysUntil(t.periodEnd) > 540 }.keys.toList()
            stale.forEach { t.values.remove(it) }
            if (stale.isNotEmpty()) warnings += "Dropped stale TTM values (tag no longer reported): " + stale.joinToString(", ")
            derive(t, periods)
            if (t.periodEnd == latest.periodEnd) t.form = "10-K"
            ttm = t
        }

        var currentShares: SourcedValueCore? = null
        for ((_, facts) in conceptFacts(cf, Concepts.BY_KEY["shares_outstanding"]!!)) {
            val latestEnd = facts.maxOf { it.end }
            val candidates = facts.filter { it.end == latestEnd }
            val total = candidates.sumOf { it.value }
            currentShares = toSv(latestFiled(candidates))
            if (candidates.size > 1) { currentShares.value = total; currentShares.derived = true; currentShares.note = "Sum of ${candidates.size} share classes reported on $latestEnd" }
            break
        }
        val anchor = ttm?.periodEnd ?: latest?.periodEnd
        if (currentShares != null && anchor != null && currentShares.periodEnd.daysUntil(anchor) > 540) {
            warnings += "dei:EntityCommonStockSharesOutstanding is stale (${currentShares.periodEnd}); likely a multi-class filer whose share data is dimensioned by class. Ignoring it."
            currentShares = null
        }
        if (currentShares == null && ttm != null && "shares_diluted" in ttm.values) {
            currentShares = ttm.values["shares_diluted"]
            warnings += "Using diluted weighted-average shares as current share count."
        }
        if (currentShares == null) warnings += "No usable share count found; per-share values unavailable."

        if (latest != null) {
            val missing = Concepts.ALL.filter { it.key !in latest.values && it.key != "shares_outstanding" }.map { it.key }
            if (missing.isNotEmpty()) warnings += "Latest 10-K missing concepts: " + missing.joinToString(", ")
        }
        return NormalizedFinancials(cf.ref.ticker, cf.ref.cik, cf.entityName, periods, ttm, currentShares, warnings)
    }

    fun cagr(first: Double?, last: Double?, years: Int): Double? {
        if (first == null || last == null || years <= 0 || first <= 0 || last <= 0) return null
        return (last / first).pow(1.0 / years) - 1.0
    }

    class GrowthEntryCore(val fullPeriodYears: Int, val fullPeriodCagr: Double?, val fiveYearCagr: Double?, val hasFive: Boolean)

    fun growthSummary(fin: NormalizedFinancials): LinkedHashMap<String, GrowthEntryCore> {
        val out = LinkedHashMap<String, GrowthEntryCore>()
        if (fin.annual.size < 2) return out
        for (key in listOf("revenue", "net_income", "eps_diluted", "equity", "fcf", "owner_earnings", "book_value_per_share")) {
            val series = fin.annual.mapNotNull { p -> p.get(key)?.let { (p.fiscalYear ?: 0) to it } }
            if (series.size < 2) continue
            val (fy0, v0) = series.first(); val (fy1, v1) = series.last()
            val five = series.filter { it.first >= fy1 - 5 }
            val fiveCagr = if (five.size >= 2) cagr(five.first().second, five.last().second, five.last().first - five.first().first) else null
            out[key] = GrowthEntryCore(fy1 - fy0, cagr(v0, v1, fy1 - fy0), fiveCagr, five.size >= 2)
        }
        return out
    }
}
