package com.swcsoftware.valuelens.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Shared output contract of the valuation core (docs/API.md), serialized identically to the Python
// reference so the oracle test can diff them. iOS decodes the same JSON via ValuationReport.swift.

@Serializable data class CompanyRef(val ticker: String, val cik: Long, val name: String)

@Serializable data class Quote(val ticker: String, val price: Double, val currency: String,
                               @SerialName("as_of") val asOf: String, val source: String)

@Serializable data class Assumptions(
    @SerialName("aaa_yield_pct") val aaaYieldPct: Double,
    @SerialName("treasury_10y_pct") val treasury10yPct: Double,
    @SerialName("hurdle_rate_pct") val hurdleRatePct: Double,
    @SerialName("equity_risk_premium_pct") val equityRiskPremiumPct: Double,
    val beta: Double,
    @SerialName("terminal_growth_pct") val terminalGrowthPct: Double,
    @SerialName("exit_multiple") val exitMultiple: Double,
    @SerialName("tax_rate_pct") val taxRatePct: Double,
    @SerialName("projection_years") val projectionYears: Int,
    @SerialName("max_growth_pct") val maxGrowthPct: Double,
    @SerialName("mos_bands_pct") val mosBandsPct: List<Double>,
    @SerialName("rate_source") val rateSource: String,
)

@Serializable data class SourcedValue(
    val value: Double, val tag: String, val accession: String, val form: String,
    @SerialName("period_end") val periodEnd: String,
    @SerialName("period_start") val periodStart: String? = null,
    val filed: String, val derived: Boolean = false, val note: String = "",
)

@Serializable data class Metric(
    val key: String, val label: String, val value: Double? = null, val unit: String,
    val formula: String, val inputs: Map<String, Double?> = emptyMap(),
    val sources: List<SourcedValue> = emptyList(), val notes: List<String> = emptyList(),
)

@Serializable data class MoSBand(@SerialName("discount_pct") val discountPct: Double,
                                 @SerialName("buy_below") val buyBelow: Double? = null)

@Serializable data class DataCheck(
    val key: String, val label: String, val status: String,   // "pass" | "warn" | "fail"
    val message: String, val inputs: List<String> = emptyList(),
)

@Serializable data class MarginOfSafety(
    @SerialName("intrinsic_value") val intrinsicValue: Double? = null,
    @SerialName("market_price") val marketPrice: Double? = null,
    @SerialName("margin_of_safety_pct") val marginOfSafetyPct: Double? = null,
    val bands: List<MoSBand>, val formula: String, val verdict: String,
)

@Serializable data class ModelResult(
    val name: String,
    @SerialName("intrinsic_value_per_share") val intrinsicValuePerShare: Double? = null,
    val composite: Metric, val metrics: List<Metric>,
    @SerialName("margin_of_safety") val marginOfSafety: MarginOfSafety,
)

@Serializable data class HistoryPoint(
    @SerialName("fiscal_year") val fiscalYear: Int? = null,
    @SerialName("period_end") val periodEnd: String,
    val revenue: Double? = null, @SerialName("net_income") val netIncome: Double? = null,
    @SerialName("eps_diluted") val epsDiluted: Double? = null, val fcf: Double? = null,
    @SerialName("owner_earnings") val ownerEarnings: Double? = null, val equity: Double? = null,
    val roic: Double? = null, @SerialName("book_value_per_share") val bookValuePerShare: Double? = null,
    val cfo: Double? = null, val capex: Double? = null,
)

@Serializable data class GrowthEntry(
    @SerialName("full_period_years") val fullPeriodYears: Int? = null,
    @SerialName("full_period_cagr") val fullPeriodCagr: Double? = null,
    @SerialName("five_year_cagr") val fiveYearCagr: Double? = null,
)

@Serializable data class ValuationReport(
    val company: CompanyRef, val quote: Quote? = null, val assumptions: Assumptions,
    val snapshot: Map<String, SourcedValue>, val history: List<HistoryPoint>,
    val growth: Map<String, GrowthEntry>,
    @SerialName("model_a") val modelA: ModelResult, @SerialName("model_b") val modelB: ModelResult,
    val warnings: List<String>, val disclaimer: String,
    @SerialName("generated_at") val generatedAt: String,
    @SerialName("data_checks") val dataChecks: List<DataCheck> = emptyList(),
    /** Every model input with its provenance ("sec", "market", "derived", "assumed"). */
    val provenance: Map<String, String> = emptyMap(),
)

// ---- Sprint 1 additions -------------------------------------------------------------------

@Serializable data class EngineHealth(
    val status: String, val version: String,
    @SerialName("lan_addresses") val lanAddresses: List<String> = emptyList(),
    @SerialName("price_providers") val priceProviders: List<String> = emptyList(),
    @SerialName("require_identity") val requireIdentity: Boolean = false,
)

enum class ValuationModel(val label: String, val subtitle: String) {
    A("Model A", "Graham · Buffett · Munger"),
    B("Model B", "DCF · WACC · ROIC");
}

fun ValuationReport.result(model: ValuationModel): ModelResult = if (model == ValuationModel.A) modelA else modelB
fun ModelResult.metric(key: String): Metric? = metrics.firstOrNull { it.key == key }
val ValuationReport.price: Double? get() = quote?.price

/** Verdict strings from the engine, with display titles and a rank (lower is better). */
enum class Verdict(val wire: String, val title: String, val rank: Int) {
    DEEP_VALUE("deep_value", "Deep value", 0),
    WITHIN_MARGIN("within_margin", "Within margin of safety", 1),
    THIN_MARGIN("below_intrinsic_thin_margin", "Below intrinsic, thin margin", 2),
    ABOVE_INTRINSIC("above_intrinsic", "Priced above intrinsic value", 3),
    INSUFFICIENT("insufficient_data", "Insufficient data", 4);

    companion object {
        fun from(wire: String): Verdict = entries.firstOrNull { it.wire == wire } ?: INSUFFICIENT
    }
}

val MarginOfSafety.verdictEnum: Verdict get() = Verdict.from(verdict)

/** SEC EDGAR fair-access identity, sent as `User-Agent: Full Name email`. */
@Serializable data class SecIdentity(val fullName: String, val email: String) {
    val userAgent: String get() = "${fullName.trim()} ${email.trim()}"
    val isValid: Boolean get() = fullName.trim().split(Regex("\\s+")).size >= 2 && Regex(".+@.+\\..+").matches(email.trim())
}

/** User overrides for engine assumptions; null = use engine live/default value. */
@Serializable data class RateOverrides(
    val aaaYieldPct: Double? = null, val treasury10yPct: Double? = null, val hurdleRatePct: Double? = null,
    val equityRiskPremiumPct: Double? = null, val beta: Double? = null, val terminalGrowthPct: Double? = null,
    val exitMultiple: Double? = null,
) {
    fun queryParams(): Map<String, String> = buildMap {
        aaaYieldPct?.let { put("aaa_yield_pct", it.toString()) }
        treasury10yPct?.let { put("treasury_10y_pct", it.toString()) }
        hurdleRatePct?.let { put("hurdle_rate_pct", it.toString()) }
        equityRiskPremiumPct?.let { put("equity_risk_premium_pct", it.toString()) }
        beta?.let { put("beta", it.toString()) }
        terminalGrowthPct?.let { put("terminal_growth_pct", it.toString()) }
        exitMultiple?.let { put("exit_multiple", it.toString()) }
    }
    companion object { val NONE = RateOverrides() }
}

/** Mirrors the engine error taxonomy (docs/API.md). */
sealed class EngineException(val title: String, message: String) : Exception(message) {
    class UnknownTicker(m: String) : EngineException("Unknown ticker", m)
    class NoAnnualData(m: String) : EngineException("No annual filings", m)
    class RateLimited(m: String) : EngineException("Slow down", m)
    class InvalidIdentity(m: String) : EngineException("Identity needed", m)
    class UpstreamUnavailable(m: String) : EngineException("Data source unavailable", m)
    class Server(val status: Int, m: String) : EngineException("Engine error", m)
    class Offline : EngineException("Engine offline", "The valuation engine is unreachable. Check the engine URL in Settings, or open one of the bundled sample companies.")

    companion object {
        @Serializable data class Envelope(val error: Body? = null, val detail: String? = null) {
            @Serializable data class Body(val code: String, val message: String)
        }
        fun from(status: Int, body: String, json: kotlinx.serialization.json.Json): EngineException {
            val env = runCatching { json.decodeFromString<Envelope>(body) }.getOrNull()
            val msg = env?.error?.message ?: env?.detail ?: body
            return when (env?.error?.code) {
                "unknown_ticker" -> UnknownTicker(msg)
                "no_annual_data" -> NoAnnualData(msg)
                "rate_limited" -> RateLimited(msg)
                "invalid_identity" -> InvalidIdentity(msg)
                "upstream_unavailable" -> UpstreamUnavailable(msg)
                else -> if (status == 404) UnknownTicker(msg) else Server(status, msg)
            }
        }
    }
}
