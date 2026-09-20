package com.swcsoftware.valuelens.ui

import com.swcsoftware.valuelens.domain.Metric
import java.util.Locale
import kotlin.math.abs

/** Number formatting shared by all screens — mirrors iOS `Fmt`. */
object Fmt {
    fun money(v: Double?, decimals: Int = 2): String = v?.let { String.format(Locale.US, "%s$%,.${decimals}f", if (it < 0) "−" else "", abs(it)) } ?: "—"

    fun compact(v: Double?): String {
        v ?: return "—"
        val s = if (v < 0) "−" else ""
        val a = abs(v)
        return when {
            a >= 1e12 -> String.format(Locale.US, "%s$%.2fT", s, a / 1e12)
            a >= 1e9 -> String.format(Locale.US, "%s$%.1fB", s, a / 1e9)
            a >= 1e6 -> String.format(Locale.US, "%s$%.1fM", s, a / 1e6)
            a >= 1e3 -> String.format(Locale.US, "%s$%.1fK", s, a / 1e3)
            else -> String.format(Locale.US, "%s$%.2f", s, a)
        }
    }

    fun pct(v: Double?, decimals: Int = 1, isFraction: Boolean = false): String =
        v?.let { String.format(Locale.US, "%.${decimals}f%%", if (isFraction) it * 100 else it) } ?: "—"

    fun number(v: Double?, decimals: Int = 2): String =
        v?.let { if (abs(it) >= 1e6) compact(it).replace("$", "") else String.format(Locale.US, "%,.${decimals}f", it) } ?: "—"

    fun metric(m: Metric): String = when (m.unit) {
        "USD/share" -> money(m.value)
        "USD" -> compact(m.value)
        "%" -> pct(m.value)
        "x" -> m.value?.let { number(it, if (it == Math.rint(it)) 0 else 1) + "×" } ?: "—"
        "shares" -> number(m.value, 0)
        else -> number(m.value)
    }

    fun input(key: String, v: Double?): String = when {
        v == null -> "—"
        key == "tax_rate" -> pct(v, 1, isFraction = true)
        key.endsWith("_pct") -> pct(v)
        key.contains("weight") || key == "beta" -> number(v, 3)
        key == "years" || key.contains("shares") -> number(v, 0)
        abs(v) >= 1e5 -> compact(v)
        else -> number(v)
    }

    fun relative(epochMs: Long?): String {
        epochMs ?: return "Not refreshed yet"
        val s = (System.currentTimeMillis() - epochMs) / 1000
        return when {
            s < 60 -> "Updated just now"
            s < 3600 -> "Updated ${s / 60} min ago"
            s < 86400 -> "Updated ${s / 3600} h ago"
            else -> "Updated ${s / 86400} d ago"
        }
    }
}
