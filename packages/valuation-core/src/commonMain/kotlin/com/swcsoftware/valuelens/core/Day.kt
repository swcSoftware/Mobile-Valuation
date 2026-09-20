package com.swcsoftware.valuelens.core

import kotlin.math.abs

/** Calendar date as days since 1970-01-01 (proleptic Gregorian). Keeps the core free of platform date APIs. */
class Day(val epochDays: Long) : Comparable<Day> {
    val year: Int get() = civil().first
    val month: Int get() = civil().second
    val day: Int get() = civil().third

    operator fun plus(days: Int) = Day(epochDays + days)
    operator fun minus(days: Int) = Day(epochDays - days)
    fun daysUntil(other: Day): Long = other.epochDays - epochDays
    fun near(other: Day, days: Int) = abs(daysUntil(other)) <= days
    override fun compareTo(other: Day) = epochDays.compareTo(other.epochDays)
    override fun equals(other: Any?) = other is Day && other.epochDays == epochDays
    override fun hashCode() = epochDays.hashCode()
    override fun toString(): String {
        val (y, m, d) = civil()
        return "$y-${m.toString().padStart(2, '0')}-${d.toString().padStart(2, '0')}"
    }

    // Howard Hinnant's days-from-civil / civil-from-days.
    private fun civil(): Triple<Int, Int, Int> {
        val z = epochDays + 719468
        val era = (if (z >= 0) z else z - 146096) / 146097
        val doe = z - era * 146097
        val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
        val y = yoe + era * 400
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        val mp = (5 * doy + 2) / 153
        val d = (doy - (153 * mp + 2) / 5 + 1).toInt()
        val m = (if (mp < 10) mp + 3 else mp - 9).toInt()
        return Triple((if (m <= 2) y + 1 else y).toInt(), m, d)
    }

    companion object {
        fun of(y: Int, m: Int, d: Int): Day {
            val yy = if (m <= 2) y - 1 else y
            val era = (if (yy >= 0) yy else yy - 399) / 400
            val yoe = yy - era * 400
            val doy = (153 * (if (m > 2) m - 3 else m + 9) + 2) / 5 + d - 1
            val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
            return Day(era * 146097L + doe - 719468)
        }
        /** Parses "YYYY-MM-DD"; returns null on anything else. */
        fun parse(s: String?): Day? {
            if (s == null || s.length < 10) return null
            val y = s.substring(0, 4).toIntOrNull() ?: return null
            val m = s.substring(5, 7).toIntOrNull() ?: return null
            val d = s.substring(8, 10).toIntOrNull() ?: return null
            return of(y, m, d)
        }
    }
}

/** Python-style number formatting used in provenance notes so the oracle diff stays exact. */
internal object PyFmt {
    /** `{:,.0f}` / `{:,.2f}` */
    fun commas(v: Double, decimals: Int): String {
        val neg = v < 0
        val s = fixed(abs(v), decimals)
        val (intPart, frac) = s.split('.').let { if (it.size == 2) it[0] to it[1] else it[0] to "" }
        val grouped = intPart.reversed().chunked(3).joinToString(",").reversed()
        return (if (neg) "-" else "") + grouped + (if (decimals > 0) ".$frac" else "")
    }

    /** `{:.Nf}` with round-half-even on the decimal expansion (matches CPython for our magnitudes). */
    fun fixed(v: Double, decimals: Int): String {
        var scale = 1.0
        repeat(decimals) { scale *= 10 }
        val scaled = v * scale
        var r = kotlin.math.round(scaled)  // ties-to-even
        if (abs(scaled - kotlin.math.floor(scaled) - 0.5) > 1e-9) r = kotlin.math.floor(scaled + 0.5)
        val asLong = r.toLong()
        if (decimals == 0) return asLong.toString()
        val str = abs(asLong).toString().padStart(decimals + 1, '0')
        val sign = if (asLong < 0) "-" else ""
        return sign + str.dropLast(decimals) + "." + str.takeLast(decimals)
    }

    /** `{:g}` for the small ratios we print (4.0 → "4", 1.5 → "1.5"). */
    fun g(v: Double): String = if (v == kotlin.math.floor(v) && abs(v) < 1e15) v.toLong().toString() else v.toString().trimEnd('0').trimEnd('.')
}
