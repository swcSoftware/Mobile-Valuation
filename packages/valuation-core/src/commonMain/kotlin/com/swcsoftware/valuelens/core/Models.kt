package com.swcsoftware.valuelens.core

import com.swcsoftware.valuelens.domain.Metric
import com.swcsoftware.valuelens.domain.MoSBand
import com.swcsoftware.valuelens.domain.MarginOfSafety
import com.swcsoftware.valuelens.domain.ModelResult
import com.swcsoftware.valuelens.domain.SourcedValue
import kotlin.math.abs
import kotlin.math.pow

internal fun SourcedValueCore.toDomain() = SourcedValue(value, qualifiedTag, accession, form, periodEnd.toString(), periodStart?.toString(), filed.toString(), derived, note)

private fun metric(key: String, label: String, value: Double?, unit: String, formula: String,
                   inputs: Map<String, Double?> = emptyMap(), sources: List<SourcedValueCore> = emptyList(), notes: List<String> = emptyList()) =
    Metric(key, label, value, unit, formula, LinkedHashMap(inputs), sources.map { it.toDomain() }, notes)

/** Model A — Graham / Buffett / Munger. Port of valuation/model_a.py. */
object ModelA {
    fun grahamGrowthRate(fin: NormalizedFinancials, a: AssumptionsCore): Metric {
        val gs = Statements.growthSummary(fin)
        var raw = gs["eps_diluted"]?.fullPeriodCagr
        var years: Int? = gs["eps_diluted"]?.fullPeriodYears
        val notes = mutableListOf<String>()
        if (raw == null) {
            raw = gs["net_income"]?.fullPeriodCagr; years = gs["net_income"]?.fullPeriodYears
            notes += "EPS CAGR unavailable; using net income CAGR."
        }
        if (raw == null) return metric("graham_g", "Expected growth (g)", null, "%", "CAGR(EPS diluted, first→last 10-K)", notes = notes + "Insufficient history.")
        val clamped = maxOf(0.0, minOf(raw * 100.0, a.maxGrowthPct))
        if (clamped != raw * 100.0) notes += "Raw CAGR ${PyFmt.fixed(raw * 100, 1)}% clamped to [0, ${PyFmt.fixed(a.maxGrowthPct, 0)}%] per Graham's caution on extrapolation."
        val eps = fin.annual.mapNotNull { it.values["eps_diluted"] }
        return metric("graham_g", "Expected growth (g)", clamped, "%",
            "CAGR(EPS) over $years yrs = (EPS_last / EPS_first)^(1/$years) − 1, clamped to [0, ${PyFmt.fixed(a.maxGrowthPct, 0)}%]",
            inputs = linkedMapOf("raw_cagr_pct" to raw * 100.0, "years" to years?.toDouble()),
            sources = if (eps.isNotEmpty()) listOf(eps.first(), eps.last()) else emptyList(), notes = notes)
    }

    fun grahamValues(fin: NormalizedFinancials, a: AssumptionsCore, g: Metric): List<Metric> {
        val eps = fin.ttm?.values?.get("eps_diluted")
        val gv = g.value
        if (eps == null || gv == null) return listOf(metric("graham_classic", "Graham intrinsic value (classic)", null, "USD/share", "EPS × (8.5 + 2g)", notes = listOf("Missing EPS or growth.")))
        val classic = eps.value * (8.5 + 2 * gv)
        val revised = if (a.aaaYieldPct > 0) classic * 4.4 / a.aaaYieldPct else null
        return listOf(
            metric("graham_classic", "Graham intrinsic value (classic)", classic, "USD/share", "V* = EPS × (8.5 + 2g)",
                linkedMapOf("eps_ttm" to eps.value, "g_pct" to gv), listOf(eps), listOf("8.5 = P/E of a no-growth company per Graham (1962).")),
            metric("graham_revised", "Graham intrinsic value (revised 1974)", revised, "USD/share", "V* = EPS × (8.5 + 2g) × 4.4 / Y",
                linkedMapOf("eps_ttm" to eps.value, "g_pct" to gv, "aaa_yield_pct" to a.aaaYieldPct), listOf(eps), listOf("4.4 = average AAA yield when Graham published; Y = current AAA yield.")),
        )
    }

    fun nnwc(fin: NormalizedFinancials): Metric {
        val formula = "NNWC = cash + short_term_investments + 0.75×receivables + 0.50×inventory − total_liabilities"
        val v = fin.ttm?.values
        if (v == null || "total_liabilities" !in v) return metric("nnwc", "Net-net working capital", null, "USD", formula, notes = listOf("Missing balance sheet."))
        val cash = v["cash"]?.value ?: 0.0; val sti = v["short_term_investments"]?.value ?: 0.0
        val ar = v["receivables"]?.value ?: 0.0; val inv = v["inventory"]?.value ?: 0.0; val tl = v["total_liabilities"]!!.value
        val total = cash + sti + 0.75 * ar + 0.50 * inv - tl
        val shares = fin.currentShares?.value
        val perShare = if (shares != null && shares != 0.0) total / shares else null
        val srcs = listOf("cash", "short_term_investments", "receivables", "inventory", "total_liabilities").mapNotNull { v[it] }
        val notes = mutableListOf("Negative NNWC is normal for most operating businesses; a positive NNWC per share above price is Graham's classic 'net-net' bargain.")
        notes += if (perShare != null) "Per share: ${PyFmt.commas(perShare, 2)}" else "Per-share value unavailable."
        return metric("nnwc", "Net-net working capital", total, "USD", formula,
            linkedMapOf("cash" to cash, "short_term_investments" to sti, "receivables" to ar, "inventory" to inv, "total_liabilities" to tl, "shares_outstanding" to shares, "nnwc_per_share" to perShare), srcs, notes)
    }

    fun ownerEarnings(fin: NormalizedFinancials, a: AssumptionsCore): List<Metric> {
        val formula = "OE = net_income + d_and_a − maintenance_capex − delta_nwc"
        val v = fin.ttm?.values
        val oe = v?.get("owner_earnings") ?: return listOf(metric("owner_earnings", "Owner earnings (TTM)", null, "USD", formula, notes = listOf("Missing inputs.")))
        val inputs = linkedMapOf<String, Double?>("net_income" to v["net_income"]!!.value, "d_and_a" to v["d_and_a"]!!.value,
            "maintenance_capex" to v["maintenance_capex"]!!.value, "delta_nwc" to (v["delta_nwc"]?.value ?: 0.0))
        val out = mutableListOf(metric("owner_earnings", "Owner earnings (TTM)", oe.value, "USD", formula, inputs,
            listOf(v["net_income"]!!, v["d_and_a"]!!, v["capex"]!!), listOf(v["maintenance_capex"]!!.note)))
        val shares = fin.currentShares?.value
        if (shares != null && shares != 0.0) {
            val oePs = oe.value / shares
            out += metric("owner_earnings_per_share", "Owner earnings / share", oePs, "USD/share", "owner_earnings / shares_outstanding",
                linkedMapOf("owner_earnings" to oe.value, "shares" to shares), listOfNotNull(fin.currentShares))
            for ((key, label, r) in listOf(
                Triple("oe_value_hurdle", "OE value @ ${PyFmt.fixed(a.hurdleRatePct, 1)}% hurdle", a.hurdleRatePct),
                Triple("oe_value_treasury", "OE value @ ${PyFmt.fixed(a.treasury10yPct, 2)}% 10-yr Treasury", a.treasury10yPct))) {
                val value = if (r > 0) oePs / (r / 100.0) else null
                out += metric(key, label, value, "USD/share", "V = OE_per_share / r   (zero terminal growth)",
                    linkedMapOf("owner_earnings_per_share" to oePs, "r_pct" to r), notes = listOf("Buffett: discount owner earnings at the long bond rate with no growth inflation; the conservatism is the point."))
            }
        }
        return out
    }

    fun marginOfSafety(intrinsic: Double?, price: Double?, a: AssumptionsCore): MarginOfSafety {
        val bands = a.mosBandsPct.map { b -> MoSBand(b, intrinsic?.let { it * (1 - b / 100.0) }) }
        val mos = if (intrinsic != null && intrinsic > 0 && price != null && price != 0.0) (1 - price / intrinsic) * 100.0 else null
        return MarginOfSafety(intrinsic, price, mos, bands, "MoS = 1 − price / intrinsic_value", verdict(mos, a))
    }

    private fun verdict(mos: Double?, a: AssumptionsCore): String {
        if (mos == null) return "insufficient_data"
        val hi = a.mosBandsPct.max(); val lo = a.mosBandsPct.min()
        return when { mos >= hi -> "deep_value"; mos >= lo -> "within_margin"; mos >= 0 -> "below_intrinsic_thin_margin"; else -> "above_intrinsic" }
    }

    fun run(fin: NormalizedFinancials, a: AssumptionsCore, price: Double?): ModelResult {
        val g = grahamGrowthRate(fin, a)
        val graham = grahamValues(fin, a, g)
        val oe = ownerEarnings(fin, a)
        val nn = nnwc(fin)
        val candidates = LinkedHashMap<String, Double?>()
        for (m in graham + oe) if (m.key in setOf("graham_revised", "oe_value_hurdle") && m.value != null && m.value != 0.0) candidates[m.key] = m.value
        val intrinsic = candidates.values.filterNotNull().minOrNull()
        val composite = metric("model_a_intrinsic", "Model A intrinsic value", intrinsic, "USD/share", "min(graham_revised, oe_value_hurdle)", candidates, notes = listOf("Conservative blend: the lower of the two classic estimates."))
        return ModelResult("Traditional Value (Graham / Buffett / Munger)", intrinsic, composite, listOf(g) + graham + nn + oe, marginOfSafety(intrinsic, price, a))
    }
}

/** Model B — DCF / WACC / ROIC. Port of valuation/model_b.py. */
object ModelB {
    fun costOfCapital(fin: NormalizedFinancials, a: AssumptionsCore, price: Double?): List<Metric> {
        val v = fin.ttm?.values ?: LinkedHashMap()
        val rf = a.treasury10yPct / 100.0
        val keCapm = rf + a.beta * a.equityRiskPremiumPct / 100.0
        val keFloor = rf + 0.04
        val ke = maxOf(keCapm, keFloor)
        val debt = v["total_debt"]?.value ?: 0.0
        val interest = v["interest_expense"]?.let { abs(it.value) }
        val kdRaw = if (interest != null && debt > 0) interest / debt else null
        val kd = if (kdRaw != null) minOf(maxOf(kdRaw, 0.02), 0.12) else rf + 0.015
        val t = v["effective_tax_rate"]?.value ?: (a.taxRatePct / 100.0)
        val shares = fin.currentShares?.value
        var mcap: Double? = if (price != null && price != 0.0 && shares != null && shares != 0.0) price * shares else null
        if (mcap == null) mcap = v["equity"]?.value
        val notes = mutableListOf<String>()
        val keNotes = if (keCapm < keFloor) listOf("CAPM gave ${PyFmt.fixed(keCapm * 100, 2)}% with β ${PyFmt.fixed(a.beta, 2)}; floored at rf + 4% — no value investor discounts equity below that.") else emptyList()
        val we: Double; val wd: Double
        if (mcap == null || mcap <= 0) { we = 1.0; wd = 0.0; notes += "No market cap or book equity available; assuming 100% equity." }
        else { we = mcap / (mcap + debt); wd = debt / (mcap + debt) }
        val wacc = we * ke + wd * kd * (1 - t)
        return listOf(
            metric("cost_of_equity", "Cost of equity (CAPM)", ke * 100, "%", "Ke = max(rf + β × ERP, rf + 4%)", linkedMapOf("rf_pct" to a.treasury10yPct, "beta" to a.beta, "erp_pct" to a.equityRiskPremiumPct, "capm_pct" to keCapm * 100), notes = keNotes),
            metric("cost_of_debt", "Cost of debt (pre-tax)", kd * 100, "%", "Kd = interest_expense / total_debt (clamped 2–12%)",
                linkedMapOf("interest_expense" to interest, "total_debt" to debt, "raw_pct" to kdRaw?.let { it * 100 }), listOfNotNull(v["interest_expense"], v["total_debt"])),
            metric("wacc", "WACC", wacc * 100, "%", "WACC = E/(D+E)·Ke + D/(D+E)·Kd·(1−t)",
                linkedMapOf("equity_weight" to we, "debt_weight" to wd, "ke_pct" to ke * 100, "kd_pct" to kd * 100, "tax_rate" to t, "market_cap" to mcap, "total_debt" to debt), notes = notes),
        )
    }

    fun fcffGrowth(fin: NormalizedFinancials, a: AssumptionsCore): Metric {
        val g = Statements.growthSummary(fin)
        val picks = listOf("fcf", "revenue", "net_income").mapNotNull { k -> g[k]?.fiveYearCagr?.let { k to it } }
        if (picks.isEmpty()) return metric("fcff_growth", "Stage-1 growth", 5.0, "%", "default", notes = listOf("No growth history; using 5% default."))
        val vals = picks.map { it.second }.sorted()
        val median = vals[vals.size / 2]
        val clamped = maxOf(0.0, minOf(median * 100.0, a.maxGrowthPct))
        return metric("fcff_growth", "Stage-1 growth (${a.projectionYears} yrs)", clamped, "%",
            "median(5-yr CAGR of ${picks.joinToString(", ") { it.first }}), clamped to [0, ${PyFmt.fixed(a.maxGrowthPct, 0)}%]",
            LinkedHashMap<String, Double?>(picks.associate { it.first to it.second * 100 }))
    }

    fun dcf(fin: NormalizedFinancials, a: AssumptionsCore, waccPct: Double, g1Pct: Double): List<Metric> {
        val v = fin.ttm?.values
        val fcff = v?.get("fcff") ?: return listOf(metric("dcf_perpetuity", "DCF fair value (perpetuity)", null, "USD/share", "see module docstring", notes = listOf("FCFF unavailable.")))
        val fcff0 = fcff.value
        val w = waccPct / 100.0; val g1 = g1Pct / 100.0; val gt = a.terminalGrowthPct / 100.0; val n = a.projectionYears
        val projected = (1..n).map { t -> fcff0 * (1 + g1).pow(t) }
        val pvStage1 = projected.withIndex().sumOf { (i, f) -> f / (1 + w).pow(i + 1) }
        val notes = mutableListOf<String>()
        val tvPerp: Double? = if (w <= gt) { notes += "WACC ≤ terminal growth; perpetuity model undefined. Using exit multiple only."; null } else projected.last() * (1 + gt) / (w - gt)
        val tvExit = projected.last() * a.exitMultiple
        val debt = v["total_debt"]?.value ?: 0.0
        val cash = (v["cash"]?.value ?: 0.0) + (v["short_term_investments"]?.value ?: 0.0)
        val shares = fin.currentShares?.value
        fun equityPerShare(tv: Double?): Double? {
            if (tv == null || shares == null || shares == 0.0) return null
            val ev = pvStage1 + tv / (1 + w).pow(n)
            val v = (ev - debt + cash) / shares
            return if (v > 0) v else null  // negative projected cash flow: a DCF is not meaningful, not a negative price
        }
        if (fcff0 <= 0) notes += "Free cash flow to the firm is negative (${PyFmt.commas(fcff0, 0)}); a DCF cannot value a business that consumes cash — see owner earnings and the growth history instead."
        val base = linkedMapOf<String, Double?>("fcff_ttm" to fcff0, "wacc_pct" to waccPct, "stage1_growth_pct" to g1Pct, "years" to n.toDouble(), "pv_stage1" to pvStage1, "total_debt" to debt, "cash_and_sti" to cash, "shares" to shares)
        return listOf(
            metric("dcf_perpetuity", "DCF fair value (perpetuity growth)", equityPerShare(tvPerp), "USD/share", "Σ FCFF_t/(1+WACC)^t + [FCFF_N(1+g)/(WACC−g)]/(1+WACC)^N − debt + cash, ÷ shares",
                LinkedHashMap(base).apply { put("terminal_growth_pct", a.terminalGrowthPct); put("terminal_value", tvPerp) }, listOf(fcff), notes),
            metric("dcf_exit_multiple", "DCF fair value (exit multiple)", equityPerShare(tvExit), "USD/share", "Σ FCFF_t/(1+WACC)^t + [FCFF_N × multiple]/(1+WACC)^N − debt + cash, ÷ shares",
                LinkedHashMap(base).apply { put("exit_multiple", a.exitMultiple); put("terminal_value", tvExit) }, listOf(fcff)),
            metric("fcff_projection", "Projected FCFF", null, "USD", "FCFF_t = FCFF_0 × (1+g)^t", LinkedHashMap<String, Double?>(projected.withIndex().associate { (i, f) -> "year_${i + 1}" to f })),
        )
    }

    fun roicEva(fin: NormalizedFinancials, waccPct: Double): List<Metric> {
        val v = fin.ttm?.values ?: LinkedHashMap()
        if ("roic" !in v || "invested_capital" !in v) return listOf(metric("roic", "ROIC (TTM)", null, "%", "nopat / invested_capital", notes = listOf("Missing inputs.")))
        val roic = v["roic"]!!.value; val ic = v["invested_capital"]!!.value; val w = waccPct / 100.0
        val history = fin.annual.mapNotNull { p -> p.get("roic")?.let { (p.fiscalYear ?: 0) to it } }
        val yearsAbove = history.count { it.second > w }
        return listOf(
            metric("roic", "ROIC (TTM)", roic * 100, "%", "ROIC = NOPAT / (equity + total_debt − cash)", linkedMapOf("nopat" to v["nopat"]!!.value, "invested_capital" to ic), listOf(v["operating_income"]!!, v["equity"]!!)),
            metric("roic_wacc_spread", "ROIC − WACC spread", (roic - w) * 100, "%", "ROIC − WACC", linkedMapOf("roic_pct" to roic * 100, "wacc_pct" to waccPct), notes = listOf("Positive spread = value creation; the wider and more persistent, the stronger the moat.")),
            metric("eva", "Economic value added (TTM)", (roic - w) * ic, "USD", "EVA = (ROIC − WACC) × invested_capital", linkedMapOf("roic_pct" to roic * 100, "wacc_pct" to waccPct, "invested_capital" to ic)),
            metric("moat_persistence", "Years ROIC > WACC", yearsAbove.toDouble(), "x", "count(years with ROIC > current WACC) over ${history.size} yrs of 10-K history", LinkedHashMap<String, Double?>(history.associate { "FY${it.first}" to (it.second * 100) })),
        )
    }

    fun run(fin: NormalizedFinancials, a: AssumptionsCore, price: Double?): ModelResult {
        val coc = costOfCapital(fin, a, price)
        val wacc = coc.first { it.key == "wacc" }.value?.takeIf { it != 0.0 } ?: a.hurdleRatePct
        val g1 = fcffGrowth(fin, a)
        val dcfM = dcf(fin, a, wacc, g1.value ?: 0.0)
        val roic = roicEva(fin, wacc)
        val vals = dcfM.filter { it.key == "dcf_perpetuity" || it.key == "dcf_exit_multiple" }.mapNotNull { it.value }
        val fair = if (vals.isNotEmpty()) vals.sum() / vals.size else null
        val composite = metric("model_b_fair_value", "Model B fair value", fair, "USD/share", "mean(dcf_perpetuity, dcf_exit_multiple)", LinkedHashMap<String, Double?>(dcfM.take(2).associate { it.key to it.value }))
        return ModelResult("Modern Fundamental Fair Value (DCF / ROIC)", fair, composite, coc + g1 + dcfM + roic, ModelA.marginOfSafety(fair, price, a))
    }
}
