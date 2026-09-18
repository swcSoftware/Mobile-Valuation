package com.swcsoftware.valuelens.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Kotlin mirror of the valuation-engine JSON contract (docs/API.md). Keep in sync with
// apps/ios/ValueLens/Domain/Models/ValuationReport.swift.

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
)
