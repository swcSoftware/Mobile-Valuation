package com.swcsoftware.valuelens.ui

import com.swcsoftware.valuelens.domain.ValuationReport
import com.swcsoftware.valuelens.domain.ModelResult
import com.swcsoftware.valuelens.domain.Verdict
import com.swcsoftware.valuelens.domain.verdictEnum

/**
 * Which presentation of a company the user has chosen.
 * Mirrors apps/ios/ValueLens/DesignSystem/LayoutStyle.swift.
 *
 * A layout is *presentation only*: it reads the report, the selected model's result and the core's
 * plain-language summary, and renders them. It never computes a number, a label or a verdict — that
 * is what keeps two layouts from disagreeing about the same company (CLAUDE.md non-negotiable 7).
 */
enum class LayoutStyle(val displayName: String, val blurb: String) {
    CLASSIC("Classic", "Dense, everything on one page."),
    REPORT_CARD("Report card", "Graded health facts, the decision first.");

    companion object {
        /** Stays the default until the owner promotes the other one (Sprint 5 Track D). */
        val DEFAULT = CLASSIC
        fun from(raw: String?): LayoutStyle = entries.firstOrNull { it.name == raw } ?: DEFAULT
    }
}

/**
 * The four things that make the app trustworthy, and the four a second layout can silently drop.
 * See docs/DESIGN.md, "The four things every layout must carry".
 */
enum class RequiredComponent {
    /** A value we refused to show, presented as a deliberate act rather than an error or a blank. */
    WITHHELD_VALUE,
    /** `warnings` and any data check that did not pass. */
    DATA_NOTES,
    /** Which inputs were measured and which were assumed. */
    PROVENANCE,
    /** Operating company / bank / REIT — a company is not valued like its neighbour. */
    SECTOR_MODE;

    companion object {
        /**
         * What *this* report obliges a layout to show. A report with nothing withheld does not owe
         * a withheld-value card; one that withholds owes it in every layout.
         *
         * Kept as pure logic, identical to the Swift version, so both platforms agree on what is
         * owed even though only iOS can currently assert that a rendered layout paid it
         * (ISSUES #87).
         */
        fun demandedBy(report: ValuationReport, result: ModelResult): Set<RequiredComponent> {
            val out = mutableSetOf(PROVENANCE)
            val withheld = report.dataChecks.any { it.status == "fail" } ||
                result.marginOfSafety.verdictEnum == Verdict.INSUFFICIENT
            if (withheld) out += WITHHELD_VALUE
            if (report.warnings.isNotEmpty() || report.dataChecks.any { it.status != "pass" }) out += DATA_NOTES
            report.sector?.let { if (it.mode != "general") out += SECTOR_MODE }
            return out
        }
    }
}
