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
    /** Shown in coverage reports so a gap report names the build it came from. */
    val appVersion: String = "dev",
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
        var facts = try { ed.companyFacts(resolved) } catch (e: com.swcsoftware.valuelens.domain.EngineException.NoAnnualData) {
            // No companyfacts at all (ETFs, trusts): explain from the filing profile instead of a bare 404.
            throw com.swcsoftware.valuelens.domain.EngineException.NoAnnualData(FilerIdentity.noAnnualDataReason(resolved.ticker, ed.profile(resolved.cik)))
        }
        var fin = Statements.normalize(facts)
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
                    facts = ed.companyFacts(predecessor.ref)
                    val note = "Filings come from predecessor ${predecessor.ref.name} (CIK ${predecessor.ref.cik}); ${resolved.ticker} is now ${predecessor.successorName} (CIK ${resolved.cik}, first filing ${predecessor.successorFirstFiling ?: "n/a"}" + (if (predecessor.viaSuccessorNotice) ", successor notice 8-K12B" else "") + ")."
                    fin = NormalizedFinancials(resolved.ticker, predecessor.ref.cik, predecessor.ref.name, pf.annual, pf.ttm, pf.currentShares, listOf(note) + pf.warnings)
                    profile = ed.profile(predecessor.ref.cik) ?: profile
                }
            }
        }
        if (fin.annual.isEmpty()) throw com.swcsoftware.valuelens.domain.EngineException.NoAnnualData(FilerIdentity.noAnnualDataReason(resolved.ticker, profile))
        // Multi-class share resolution: only when the ordinary share count is missing or inconsistent.
        var classResolution: ClassResolution? = null
        if (needsClassResolution(fin)) {
            classResolution = resolveClasses(ed, fin, resolved.ticker)
            if (classResolution != null) {
                val src = fin.currentShares
                val today = Day(floorDiv(clock.nowMillis(), Edgar.DAY))
                val sv = SourcedValueCore(classResolution.totalInSearchedClass, "EntityCommonStockSharesOutstanding", "dei", classResolution.source.accession, classResolution.source.form,
                    classResolution.classes.firstNotNullOfOrNull { it.asOf?.let { d -> Day.parse(d) } } ?: today, null, classResolution.source.filed ?: today, derived = true,
                    note = "Σ class shares × EPS ratio, in ${classResolution.searchedClass}-share terms: " + classResolution.classes.joinToString("; ") { "${it.cls}${it.ticker?.let { t -> " ($t)" } ?: ""} ${PyFmt.commas(it.shares, 0)} × ${PyFmt.fixed(it.ratioToSearched, 4)}" })
                val ttm = fin.ttm?.also { t -> classResolution.dilutedInSearchedClass?.let { d -> t.values["shares_diluted"] = SourcedValueCore(d, "WeightedAverageNumberOfDilutedSharesOutstanding", "us-gaap", classResolution.source.accession, classResolution.source.form, sv.periodEnd, null, sv.filed, derived = true, note = "Per-class weighted averages converted to ${classResolution.searchedClass}-share terms") } }
                // Multi-class filers tag EPS per class only; derive a TTM EPS in searched-class terms so Graham's formulas can run.
                var derivedEps = false
                ttm?.let { t ->
                    val ni = t.values["net_income"]
                    if (t.values["eps_diluted"] == null && ni != null && classResolution.totalInSearchedClass > 0) {
                        t.values["eps_diluted"] = SourcedValueCore(ni.value / classResolution.totalInSearchedClass, "derived", "valuelens", ni.accession, ni.form, ni.periodEnd, ni.periodStart, ni.filed, derived = true,
                            note = "net_income ÷ shares in ${classResolution.searchedClass}-share terms (per-class EPS is dimensioned in the filing)")
                        derivedEps = true
                    }
                }
                val cleaned = fin.warnings.filterNot { it.contains("multi-class") || it.contains("No usable share count") || it.contains("Using diluted weighted-average shares as current") }
                fin = NormalizedFinancials(fin.ticker, fin.cik, fin.name, fin.annual, ttm, sv, cleaned + classResolution.notes + (if (derivedEps) listOf("TTM EPS derived from net income ÷ ${classResolution.searchedClass}-equivalent shares.") else emptyList()))
                if (src == null) { /* per-share metrics now possible */ }
            }
        }
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
        val coverage = Coverage.analyze(fin, facts, appVersion, Sector.modeFor(profile?.sic, fin))
        val checks = DataChecks.run(fin, quote, priceOverride != null, a, rates, ratesOverridden, clock.nowMillis(), predecessor, sector, classResolution)
        val report = Report.build(fin, a, quote, Market.isoFromMillis(clock.nowMillis()), checks.checks, checks.provenance, sector, classResolution?.classes ?: emptyList(), coverage)
        return if (measuredBeta != null) report.withBeta(measuredBeta) else report
    }

    /** Missing count, stale multi-class cover page, or cover vs diluted average disagreeing beyond 30%. */
    internal fun needsClassResolution(fin: NormalizedFinancials): Boolean {
        val cur = fin.currentShares?.value ?: return true
        if (fin.warnings.any { it.contains("multi-class") || it.contains("Using diluted weighted-average shares as current") }) return true
        val sh = fin.ttm?.values?.get("shares_diluted")?.value ?: return false
        val r = cur / sh
        return r < 0.7 || r > 1.3
    }

    private fun resolveClasses(ed: Edgar, fin: NormalizedFinancials, ticker: String): ClassResolution? {
        val subs = ed.submissionsText(fin.cik) ?: return null
        val ref = ShareClasses.latestFilingRef(subs, fin.cik) ?: return null
        val xml = ed.instance(ref) ?: return null
        return ShareClasses.resolve(xml, ticker, ref)
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
    /**
     * Everything the report-card layout shows that is a judgement — grades against printed rules,
     * historical reads, the verdict chips — for one investor lens (Sprint 5 Track C).
     *
     * A separate call rather than new fields on `explainJson`, so the classic layout's facts cannot
     * move because the report card exists. `lens` is `VALUE` or `GROWTH`; anything else is Value.
     */
    @Throws(Exception::class)
    fun reportCardJson(reportJson: String, lens: String): String {
        val r = json.decodeFromString<ValuationReport>(reportJson)
        return json.encodeToString(Grading.reportCard(r, Lens.from(lens)))
    }

    /** Prefilled GitHub issue URL for a coverage report (no token, no server; the user submits it). */
    @Throws(Exception::class)
    fun coverageIssueUrl(reportJson: String): String {
        val r = json.decodeFromString<ValuationReport>(reportJson)
        return Coverage.issueUrl(r.coverage ?: com.swcsoftware.valuelens.domain.CoverageReport(r.company.ticker, r.company.cik, r.company.name, r.generatedAt.take(10), appVersion, Concepts.VERSION))
    }

    @Throws(Exception::class)
    fun glossaryJson(): String = json.encodeToString(Explain.glossary)
    /**
     * A company name as it should be *shown*: "Merck & Co., Inc.", not "MERCK & CO., INC.". Display
     * only — the filed name stays the evidence everywhere it is used as such (ISSUES #85).
     */
    fun displayName(name: String, ticker: String): String = CompanyNames.display(name, ticker)

    /** The investor lenses with their copy and live example rule (Sprint 5 Track D). */
    @Throws(Exception::class)
    fun lensesJson(): String = json.encodeToString(Grading.lenses())
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
