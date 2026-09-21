package com.swcsoftware.valuelens.core

import com.swcsoftware.valuelens.domain.CompanyRef
import com.swcsoftware.valuelens.domain.Quote
import com.swcsoftware.valuelens.domain.RateOverrides
import com.swcsoftware.valuelens.domain.ValuationReport
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.Serializable

/** Everything the apps need, behind one blocking facade. Call off the main thread. */
class ValuationCore(
    private val fetcher: Fetcher, private val cache: KeyValueCache, private val clock: Clock = Clock { currentTimeMillis() },
    /** GitHub Pages base, e.g. https://swcsoftware.github.io/Mobile-Valuation — rates.json and tickers.json live there. */
    val publishedBaseUrl: String = "https://swcsoftware.github.io/Mobile-Valuation",
    /** Used when rates.json is unreachable and nothing is cached (yesterday's FRED values shipped in the app). */
    private val bundledRates: RatesSnapshot? = null,
) {
    val json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true }
    private fun edgar(userAgent: String) = Edgar(fetcher, cache, userAgent, clock, "$publishedBaseUrl/tickers.json")
    private val market = Market(fetcher, cache, clock)

    @Throws(Exception::class)
    fun search(query: String, userAgent: String, limit: Int = 15): List<CompanyRef> = edgar(userAgent).search(query, limit)

    fun rates(): RatesSnapshot? = Rates.fetch(fetcher, cache, clock, "$publishedBaseUrl/rates.json") ?: bundledRates

    /**
     * Full report. Order of precedence for assumptions: user override > measured/published > default.
     */
    @Throws(Exception::class)
    fun valuation(ticker: String, userAgent: String, priceOverride: Double? = null, overrides: RateOverrides = RateOverrides.NONE,
                  defaults: AssumptionsCore = AssumptionsCore(aaaYieldPct = 5.0, treasury10yPct = 4.2)): ValuationReport {
        val ed = edgar(userAgent)
        val resolved = ed.resolve(ticker)
        var ref = resolved
        var fin = try { Statements.normalize(ed.companyFacts(resolved)) } catch (e: com.swcsoftware.valuelens.domain.EngineException.NoAnnualData) {
            // No companyfacts at all (ETFs, trusts): explain from the filing profile instead of a bare 404.
            throw com.swcsoftware.valuelens.domain.EngineException.NoAnnualData(FilerIdentity.noAnnualDataReason(resolved.ticker, ed.profile(resolved.cik)))
        }
        if (resolved.name.isNotBlank() && fin.annual.isNotEmpty())  // companyfacts entityName can be a co-registrant (BAC → "BofA Finance LLC")
            fin = NormalizedFinancials(fin.ticker, fin.cik, resolved.name, fin.annual, fin.ttm, fin.currentShares, fin.warnings)
        var predecessor: Predecessor? = null
        var profile: FilerProfile? = ed.profile(resolved.cik)  // SIC for sector mode (cached 24 h); null → general
        if (fin.annual.isEmpty()) {
            // Successor-issuer fallback (holding-company reorganizations). Never reached for ordinary filers.
            val today = Day(floorDiv(clock.nowMillis(), Edgar.DAY))
            predecessor = ed.findPredecessor(resolved, profile, today)
            if (predecessor != null) {
                val pf = Statements.normalize(ed.companyFacts(predecessor.ref))
                if (pf.annual.isNotEmpty()) {
                    ref = predecessor.ref
                    val note = "Filings come from predecessor ${predecessor.ref.name} (CIK ${predecessor.ref.cik}); ${resolved.ticker} is now ${predecessor.successorName} (CIK ${resolved.cik}, first filing ${predecessor.successorFirstFiling ?: "n/a"}" + (if (predecessor.viaSuccessorNotice) ", successor notice 8-K12B" else "") + ")."
                    fin = NormalizedFinancials(resolved.ticker, predecessor.ref.cik, predecessor.ref.name, pf.annual, pf.ttm, pf.currentShares, listOf(note) + pf.warnings)
                    profile = ed.profile(predecessor.ref.cik) ?: profile
                }
            }
        }
        if (fin.annual.isEmpty()) throw com.swcsoftware.valuelens.domain.EngineException.NoAnnualData(FilerIdentity.noAnnualDataReason(resolved.ticker, profile))
        val quote = priceOverride?.let { Quote(ref.ticker, it, "USD", Market.isoFromMillis(clock.nowMillis()), "manual") } ?: market.quote(ref.ticker)
        val measuredBeta = if (overrides.beta == null) market.beta(ref.ticker) else null
        val rates = rates()
        val ratesOverridden = overrides.aaaYieldPct != null || overrides.treasury10yPct != null
        val a = AssumptionsCore(
            aaaYieldPct = overrides.aaaYieldPct ?: rates?.aaa_yield_pct ?: defaults.aaaYieldPct,
            treasury10yPct = overrides.treasury10yPct ?: rates?.treasury_10y_pct ?: defaults.treasury10yPct,
            hurdleRatePct = overrides.hurdleRatePct ?: defaults.hurdleRatePct,
            equityRiskPremiumPct = overrides.equityRiskPremiumPct ?: defaults.equityRiskPremiumPct,
            beta = overrides.beta ?: measuredBeta?.beta ?: defaults.beta,
            terminalGrowthPct = overrides.terminalGrowthPct ?: defaults.terminalGrowthPct,
            exitMultiple = overrides.exitMultiple ?: defaults.exitMultiple,
            taxRatePct = defaults.taxRatePct, projectionYears = defaults.projectionYears, maxGrowthPct = defaults.maxGrowthPct, mosBandsPct = defaults.mosBandsPct,
            rateSource = when { ratesOverridden -> "user override"; rates != null -> "FRED (published ${rates.as_of})"; else -> "defaults" },
            betaSource = when { overrides.beta != null -> "override"; measuredBeta != null -> "measured"; else -> "assumed" },
        )
        val sector = Sector.info(profile, fin)
        val checks = DataChecks.run(fin, quote, priceOverride != null, a, rates, ratesOverridden, clock.nowMillis(), predecessor, sector)
        val report = Report.build(fin, a, quote, Market.isoFromMillis(clock.nowMillis()), checks.checks, checks.provenance, sector)
        return if (measuredBeta != null) report.withBeta(measuredBeta) else report
    }

    /** JSON form for hosts without Kotlin interop (iOS decodes it with ValuationReport.swift). */
    @Throws(Exception::class)
    fun valuationJson(ticker: String, userAgent: String, priceOverride: Double? = null, overridesJson: String? = null): String {
        val ov = overridesJson?.let { json.decodeFromString<RateOverrides>(it) } ?: RateOverrides.NONE
        return json.encodeToString(valuation(ticker, userAgent, priceOverride, ov))
    }
    @Throws(Exception::class)
    fun searchJson(query: String, userAgent: String): String = json.encodeToString(search(query, userAgent))

    /** Plain-language layer for a report (iOS passes the JSON it received back in). */
    @Throws(Exception::class)
    fun explainJson(reportJson: String): String {
        val r = json.decodeFromString<ValuationReport>(reportJson)
        val mode = r.sector?.mode ?: "general"
        return json.encodeToString(ExplainSummary(
            verdictA = Explain.verdictSentence(r, r.modelA), verdictB = Explain.verdictSentence(r, r.modelB),
            facts = Explain.healthFacts(r), checksSummary = Explain.checksSummary(r),
            blurbA = Explain.modelBlurb(true, mode), blurbB = Explain.modelBlurb(false, mode), sectorNote = r.sector?.note ?: "",
        ))
    }
    @Throws(Exception::class)
    fun glossaryJson(): String = json.encodeToString(Explain.glossary)
    @Throws(Exception::class)
    fun ratesJson(): String? = rates()?.let { json.encodeToString(it) }

    /** Stable error code for the host ("unknown_ticker", "offline", …) — Swift sees Kotlin exceptions as NSError. */
    companion object {
        fun errorCode(e: Throwable): String = when (e) {
            is com.swcsoftware.valuelens.domain.EngineException.UnknownTicker -> "unknown_ticker"
            is com.swcsoftware.valuelens.domain.EngineException.NoAnnualData -> "no_annual_data"
            is com.swcsoftware.valuelens.domain.EngineException.RateLimited -> "rate_limited"
            is com.swcsoftware.valuelens.domain.EngineException.InvalidIdentity -> "invalid_identity"
            is com.swcsoftware.valuelens.domain.EngineException.UpstreamUnavailable -> "upstream_unavailable"
            is com.swcsoftware.valuelens.domain.EngineException.Offline -> "offline"
            else -> "engine_error"
        }
    }
}

@Serializable
data class ExplainSummary(val verdictA: String, val verdictB: String, val facts: List<Explain.Fact>, val checksSummary: String,
                          val blurbA: String = "", val blurbB: String = "", val sectorNote: String = "")

private fun ValuationReport.withBeta(b: BetaResult): ValuationReport =
    copy(warnings = warnings, provenance = provenance + mapOf("beta_detail" to "β ${PyFmt.fixed(b.beta, 2)} · ${b.months} monthly returns ${b.window} · R² ${PyFmt.fixed(b.r2, 2)}"))

expect fun currentTimeMillis(): Long
