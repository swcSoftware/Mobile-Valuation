package com.swcsoftware.valuelens.core

import com.swcsoftware.valuelens.domain.Metric
import com.swcsoftware.valuelens.domain.ModelResult
import com.swcsoftware.valuelens.domain.SectorInfo
import kotlin.math.pow

/**
 * Sector modes (Sprint 3, Track B). Industrial/tech/consumer companies keep the general models
 * untouched. Banks, insurers and REITs have accounting that makes owner earnings, NNWC and FCFF
 * meaningless, so they get value-investing models built for them — and the report says which.
 */
enum class SectorMode { GENERAL, FINANCIAL, REIT }

object Sector {
    /** Low-beta financials produce CAPM costs of equity below what any value investor would accept; floor it. */
    const val KE_FLOOR_SPREAD_PCT = 4.0
    const val MAX_JUSTIFIED_PB = 4.0

    fun costOfEquity(a: AssumptionsCore): Pair<Double, Boolean> {
        val capm = (a.treasury10yPct + a.beta * a.equityRiskPremiumPct) / 100.0
        val floor = (a.treasury10yPct + KE_FLOOR_SPREAD_PCT) / 100.0
        return if (capm < floor) floor to true else capm to false
    }
    /** SIC → mode. Ranges per SEC's Standard Industrial Classification list. */
    fun modeFor(sic: String?, fin: NormalizedFinancials? = null): SectorMode {
        val code = sic?.toIntOrNull() ?: return SectorMode.GENERAL
        return when (code) {
            in 6020..6199, 6712 -> SectorMode.FINANCIAL        // banks, savings institutions, credit, bank holding cos
            in 6200..6299 -> SectorMode.FINANCIAL              // brokers, dealers, exchanges, asset managers
            in 6311..6411 -> SectorMode.FINANCIAL              // insurers and agents
            6798 -> if (fin != null && isMortgageReit(fin)) SectorMode.FINANCIAL else SectorMode.REIT
            else -> SectorMode.GENERAL
        }
    }

    /** Mortgage REITs share SIC 6798 with property REITs but own loans, not buildings: no depreciation to add back. */
    fun isMortgageReit(fin: NormalizedFinancials): Boolean {
        val t = fin.ttm?.values ?: return false
        val da = t["d_and_a"]?.value ?: return true
        val rev = t["revenue"]?.value ?: return false
        return rev > 0 && da / rev < 0.05
    }

    fun info(profile: FilerProfile?, fin: NormalizedFinancials? = null): SectorInfo {
        val mode = modeFor(profile?.sic, fin)
        val mreit = profile?.sic == "6798" && mode == SectorMode.FINANCIAL
        val desc = when {
            mreit -> "Mortgage REIT — owns loans, not buildings, so it is valued like a financial: book value and return on equity."
            mode == SectorMode.FINANCIAL -> "Bank / insurer / broker — valued on book value and return on equity; cash-flow models don't apply to financial balance sheets."
            mode == SectorMode.REIT -> "REIT — valued on funds from operations (FFO) and dividends; GAAP earnings understate real estate cash flow."
            profile?.sic == null -> "Industry unknown (SEC profile unavailable) — general models used; check the industry before relying on the value."
            else -> "Operating company — earnings, owner earnings and discounted free cash flow."
        }
        return SectorInfo(sic = profile?.sic, sicDescription = null, mode = mode.name.lowercase(), note = desc)
    }

    private fun m(key: String, label: String, value: Double?, unit: String, formula: String, inputs: Map<String, Double?> = emptyMap(),
                  sources: List<SourcedValueCore> = emptyList(), notes: List<String> = emptyList()) =
        Metric(key, label, value, unit, formula, LinkedHashMap(inputs), sources.map { it.toDomain() }, notes)

    // ------------------------------------------------------------------ banks & insurers
    /**
     * Model A (financial): Graham EPS formulas + book value × justified P/B.
     *   justified P/B = (ROE − g) / (Ke − g)   (Gordon growth on book equity; g = terminal growth)
     * Composite = min(Graham revised, book-value intrinsic).
     */
    fun financialModelA(fin: NormalizedFinancials, a: AssumptionsCore, price: Double?): ModelResult {
        val g = ModelA.grahamGrowthRate(fin, a)
        val graham = ModelA.grahamValues(fin, a, g)
        val v = fin.ttm?.values
        val shares = fin.currentShares?.value
        val out = mutableListOf(g) + graham
        val extra = mutableListOf<Metric>()
        var bookIntrinsic: Double? = null
        val equity = v?.get("equity"); val ni = v?.get("net_income")
        if (equity != null && shares != null && shares > 0) {
            val bvps = equity.value / shares
            extra += m("book_value_per_share", "Book value / share", bvps, "USD/share", "equity / shares_outstanding", linkedMapOf("equity" to equity.value, "shares" to shares), listOf(equity))
            if (ni != null && equity.value > 0) {
                val roe = ni.value / equity.value
                val (ke, floored) = costOfEquity(a)
                val gr = a.terminalGrowthPct / 100.0
                extra += m("roe", "Return on equity (TTM)", roe * 100, "%", "net_income / equity", linkedMapOf("net_income" to ni.value, "equity" to equity.value), listOf(ni, equity))
                val notes = mutableListOf("Graham on banks: pay no more than ~1.2× book unless returns on equity are durably high; the justified multiple makes that explicit.")
                if (floored) notes += "Cost of equity floored at rf + ${PyFmt.g(KE_FLOOR_SPREAD_PCT)}% (${PyFmt.fixed(ke * 100, 2)}%); the measured beta implied less than a value investor would accept."
                var pb = if (ke > gr) ((roe - gr) / (ke - gr)).coerceAtLeast(0.0) else null
                if (pb == null) notes += "Cost of equity ≤ growth; justified P/B undefined."
                if (pb != null && pb > MAX_JUSTIFIED_PB) { notes += "Justified P/B ${PyFmt.fixed(pb, 1)}× capped at ${PyFmt.g(MAX_JUSTIFIED_PB)}×: today's return on equity is unlikely to persist forever."; pb = MAX_JUSTIFIED_PB }
                extra += m("justified_pb", "Justified price / book", pb, "x", "P/B* = min((ROE − g) / (Ke − g), ${PyFmt.g(MAX_JUSTIFIED_PB)})", linkedMapOf("roe_pct" to roe * 100, "ke_pct" to ke * 100, "g_pct" to gr * 100), notes = notes)
                bookIntrinsic = pb?.let { bvps * it }
                extra += m("book_value_intrinsic", "Book-value intrinsic value", bookIntrinsic, "USD/share", "V = book_value_per_share × P/B*", linkedMapOf("book_value_per_share" to bvps, "justified_pb" to pb))
            }
        }
        val candidates = LinkedHashMap<String, Double?>()
        graham.firstOrNull { it.key == "graham_revised" }?.value?.let { candidates["graham_revised"] = it }
        bookIntrinsic?.let { candidates["book_value_intrinsic"] = it }
        val intrinsic = candidates.values.filterNotNull().filter { it > 0 }.minOrNull()
        val composite = m("model_a_intrinsic", "Model A intrinsic value", intrinsic, "USD/share", "min(graham_revised, book_value_intrinsic)", candidates,
            notes = listOf("Conservative blend: the lower of Graham's earnings value and the justified book value."))
        return ModelResult("Traditional Value for Financials (Graham / book value)", intrinsic, composite, out + extra, ModelA.marginOfSafety(intrinsic, price, a))
    }

    /**
     * Model B (financial): residual income (excess return on book equity).
     *   V = B0 + Σ (ROE − Ke)·B_{t−1}/(1+Ke)^t + [(ROE − Ke)·B_N/(Ke − g)]/(1+Ke)^N ;  B grows at ROE × (1 − payout)
     */
    fun financialModelB(fin: NormalizedFinancials, a: AssumptionsCore, price: Double?): ModelResult {
        val v = fin.ttm?.values
        val shares = fin.currentShares?.value
        val equity = v?.get("equity"); val ni = v?.get("net_income")
        val (ke, floored) = costOfEquity(a)
        val metrics = mutableListOf(m("cost_of_equity", "Cost of equity (CAPM, floored)", ke * 100, "%", "Ke = max(rf + β × ERP, rf + ${PyFmt.g(KE_FLOOR_SPREAD_PCT)}%)", linkedMapOf("rf_pct" to a.treasury10yPct, "beta" to a.beta, "erp_pct" to a.equityRiskPremiumPct),
            notes = if (floored) listOf("Floored: the measured beta implied a cost of equity below rf + ${PyFmt.g(KE_FLOOR_SPREAD_PCT)}%.") else emptyList()))
        var fair: Double? = null
        if (equity != null && ni != null && shares != null && shares > 0 && equity.value > 0) {
            val roe = ni.value / equity.value
            val div = v["dividends_paid"]?.value?.let { kotlin.math.abs(it) }
            val payout = if (div != null && ni.value > 0) (div / ni.value).coerceIn(0.0, 1.0) else 0.4
            val bg = (roe * (1 - payout)).coerceIn(0.0, a.maxGrowthPct / 100.0)
            val gt = a.terminalGrowthPct / 100.0; val n = a.projectionYears
            var b = equity.value / shares; var pv = 0.0
            for (t in 1..n) { pv += (roe - ke) * b / (1 + ke).pow(t); b *= (1 + bg) }
            val notes = mutableListOf<String>()
            val spreadTerminal = (roe - ke).coerceAtMost(0.15)
            if (roe - ke > 0.15) notes += "Terminal excess return capped at 15 points over Ke: a ${PyFmt.fixed(roe * 100, 0)}% ROE won't persist indefinitely."
            val tv = if (ke > gt) spreadTerminal * b / (ke - gt) / (1 + ke).pow(n) else { notes += "Ke ≤ terminal growth; terminal term omitted."; 0.0 }
            val bvps = equity.value / shares
            fair = (bvps + pv + tv).takeIf { it > 0 }
            metrics += m("roe", "Return on equity (TTM)", roe * 100, "%", "net_income / equity", linkedMapOf("net_income" to ni.value, "equity" to equity.value), listOf(ni, equity))
            metrics += m("payout_ratio", "Dividend payout", payout * 100, "%", "dividends_paid / net_income" + (if (div == null) " (assumed 40%)" else ""), linkedMapOf("dividends_paid" to div, "net_income" to ni.value), notes = if (div == null) listOf("No dividend tag; 40% payout assumed.") else emptyList())
            metrics += m("book_growth", "Book value growth (sustainable)", bg * 100, "%", "g_b = ROE × (1 − payout), capped", linkedMapOf("roe_pct" to roe * 100, "payout_pct" to payout * 100))
            metrics += m("residual_income_value", "Residual-income fair value", fair, "USD/share", "V = B0 + Σ(ROE−Ke)·B_{t−1}/(1+Ke)^t + TV", linkedMapOf("book_value_per_share" to bvps, "pv_excess_returns" to pv, "terminal" to tv, "ke_pct" to ke * 100, "years" to n.toDouble()), listOf(equity), notes)
            metrics += m("roe_ke_spread", "ROE − Ke spread", (roe - ke) * 100, "%", "ROE − Ke", linkedMapOf("roe_pct" to roe * 100, "ke_pct" to ke * 100), notes = listOf("Positive spread = the bank earns more on its equity than investors require; that excess is what the model capitalizes."))
            val hist = fin.annual.mapNotNull { p -> p.get("roe")?.let { (p.fiscalYear ?: 0) to it } }
            metrics += m("roe_persistence", "Years ROE > Ke", hist.count { it.second > ke }.toDouble(), "x", "count(years with ROE > Ke) over ${hist.size} yrs", LinkedHashMap<String, Double?>(hist.associate { "FY${it.first}" to (it.second * 100) }))
        } else metrics += m("residual_income_value", "Residual-income fair value", null, "USD/share", "see notes", notes = listOf("Equity, net income or share count missing."))
        val composite = m("model_b_fair_value", "Model B fair value", fair, "USD/share", "residual_income_value", linkedMapOf("residual_income_value" to fair))
        return ModelResult("Modern Fair Value for Financials (residual income)", fair, composite, metrics, ModelA.marginOfSafety(fair, price, a))
    }

    // ------------------------------------------------------------------ REITs
    private fun ffo(fin: NormalizedFinancials): Pair<Double, List<SourcedValueCore>>? {
        val v = fin.ttm?.values ?: return null
        val ni = v["net_income"] ?: return null; val da = v["d_and_a"] ?: return null
        val gain = v["gain_on_sale"]
        return (ni.value + da.value - (gain?.value ?: 0.0)) to listOfNotNull(ni, da, gain)
    }

    /** Model A (REIT): Graham's formula on FFO per share (earnings + depreciation − property gains). */
    fun reitModelA(fin: NormalizedFinancials, a: AssumptionsCore, price: Double?): ModelResult {
        val g = ModelA.grahamGrowthRate(fin, a)
        val shares = fin.currentShares?.value
        val f = ffo(fin)
        val metrics = mutableListOf(g)
        var value: Double? = null
        if (f != null && shares != null && shares > 0) {
            val (ffoTotal, srcs) = f
            val ffops = ffoTotal / shares
            metrics += m("ffo", "Funds from operations (TTM)", ffoTotal, "USD", "FFO = net_income + d_and_a − gain_on_sale", linkedMapOf("net_income" to srcs[0].value, "d_and_a" to srcs[1].value, "gain_on_sale" to srcs.getOrNull(2)?.value), srcs,
                listOf("NAREIT definition. Depreciation is added back because real estate generally appreciates; gains on sale are removed because they are not recurring."))
            metrics += m("ffo_per_share", "FFO / share", ffops, "USD/share", "ffo / shares_outstanding", linkedMapOf("ffo" to ffoTotal, "shares" to shares))
            val gv = g.value
            if (gv != null) {
                val classic = ffops * (8.5 + 2 * gv)
                value = if (a.aaaYieldPct > 0) classic * 4.4 / a.aaaYieldPct else null
                metrics += m("graham_ffo_value", "Graham value on FFO (revised)", value, "USD/share", "V* = FFO/share × (8.5 + 2g) × 4.4 / Y", linkedMapOf("ffo_per_share" to ffops, "g_pct" to gv, "aaa_yield_pct" to a.aaaYieldPct),
                    notes = listOf("Graham's earnings formula applied to FFO, the REIT equivalent of earnings."))
            }
            fin.ttm?.values?.get("dividends_per_share")?.let { dps ->
                metrics += m("dividend_coverage", "FFO / dividend coverage", if (dps.value > 0) ffops / dps.value else null, "x", "ffo_per_share / dividends_per_share", linkedMapOf("ffo_per_share" to ffops, "dividends_per_share" to dps.value), listOf(dps),
                    listOf("Below 1.0× the dividend is not covered by cash operations."))
            }
        } else metrics += m("ffo", "Funds from operations (TTM)", null, "USD", "FFO = net_income + d_and_a − gain_on_sale", notes = listOf("Net income, D&A or share count missing."))
        val composite = m("model_a_intrinsic", "Model A intrinsic value", value, "USD/share", "graham_ffo_value", linkedMapOf("graham_ffo_value" to value))
        return ModelResult("Traditional Value for REITs (Graham on FFO)", value, composite, metrics, ModelA.marginOfSafety(value, price, a))
    }

    /** Model B (REIT): FFO multiple and dividend discount, averaged. */
    fun reitModelB(fin: NormalizedFinancials, a: AssumptionsCore, price: Double?): ModelResult {
        val shares = fin.currentShares?.value
        val f = ffo(fin)
        val (ke, floored) = costOfEquity(a)
        val gt = a.terminalGrowthPct / 100.0
        val metrics = mutableListOf(m("cost_of_equity", "Cost of equity (CAPM, floored)", ke * 100, "%", "Ke = max(rf + β × ERP, rf + ${PyFmt.g(KE_FLOOR_SPREAD_PCT)}%)", linkedMapOf("rf_pct" to a.treasury10yPct, "beta" to a.beta, "erp_pct" to a.equityRiskPremiumPct),
            notes = if (floored) listOf("Floored: the measured beta implied a cost of equity below rf + ${PyFmt.g(KE_FLOOR_SPREAD_PCT)}%.") else emptyList()))
        val vals = LinkedHashMap<String, Double?>()
        if (f != null && shares != null && shares > 0) {
            val ffops = f.first / shares
            val mult = ffops * a.exitMultiple
            vals["ffo_multiple_value"] = mult.takeIf { it > 0 }
            metrics += m("ffo_multiple_value", "FFO-multiple value", vals["ffo_multiple_value"], "USD/share", "V = FFO/share × multiple", linkedMapOf("ffo_per_share" to ffops, "multiple" to a.exitMultiple),
                notes = listOf("Uses the 'exit multiple' assumption (${PyFmt.g(a.exitMultiple)}×) as the FFO multiple; adjust it in Settings."))
        }
        fin.ttm?.values?.get("dividends_per_share")?.let { dps ->
            val ddm = if (ke > gt && dps.value > 0) dps.value * (1 + gt) / (ke - gt) else null
            vals["dividend_discount_value"] = ddm
            metrics += m("dividend_discount_value", "Dividend-discount value", ddm, "USD/share", "V = DPS × (1+g) / (Ke − g)", linkedMapOf("dividends_per_share" to dps.value, "g_pct" to gt * 100, "ke_pct" to ke * 100), listOf(dps))
        }
        val nonNull = vals.values.filterNotNull()
        val fair = if (nonNull.isNotEmpty()) nonNull.sum() / nonNull.size else null
        val composite = m("model_b_fair_value", "Model B fair value", fair, "USD/share", "mean(ffo_multiple_value, dividend_discount_value)", vals)
        return ModelResult("Modern Fair Value for REITs (FFO multiple / dividends)", fair, composite, metrics, ModelA.marginOfSafety(fair, price, a))
    }
}
