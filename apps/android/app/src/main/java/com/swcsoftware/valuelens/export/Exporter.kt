package com.swcsoftware.valuelens.export

import com.swcsoftware.valuelens.ui.displayName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.swcsoftware.valuelens.domain.ValuationModel
import com.swcsoftware.valuelens.domain.ValuationReport
import com.swcsoftware.valuelens.domain.result
import com.swcsoftware.valuelens.domain.verdictEnum
import com.swcsoftware.valuelens.ui.Fmt
import java.io.File

/**
 * PDF dossier (android.graphics.pdf) and branded share cards (Bitmap) handed to the system
 * share sheet via FileProvider. Files land in the app's Documents dir (visible via Files/Storage).
 */
object Exporter {
    private const val BG = 0xFF0B0D10.toInt(); private const val MINT = 0xFF2ED99E.toInt(); private const val AMBER = 0xFFF5A623.toInt()
    private const val T1 = 0xFFF2F2F2.toInt(); private const val T2 = 0xFF9EA3AB.toInt(); private const val T3 = 0xFF6B7079.toInt(); private const val RED = 0xFFEF5450.toInt()

    private fun docsDir(context: Context): File = File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir, "ValueLens").apply { mkdirs() }

    private fun share(context: Context, file: File, mime: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val intent = Intent(Intent.ACTION_SEND).apply { type = mime; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        context.startActivity(Intent.createChooser(intent, "Share ${file.name}").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun paint(color: Int, size: Float, bold: Boolean = false, mono: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color; textSize = size
        typeface = if (mono) Typeface.MONOSPACE else Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    fun sharePdf(context: Context, r: ValuationReport, model: ValuationModel) {
        val res = r.result(model); val mos = res.marginOfSafety
        val doc = PdfDocument()
        val page = doc.startPage(PdfDocument.PageInfo.Builder(612, 792, 1).create())
        val c = page.canvas; var y = 50f; val left = 36f; val right = 576f
        fun line(text: String, p: Paint, dy: Float = 14f) { c.drawText(text, left, y, p); y += dy }
        fun row(l: String, v: String, p: Paint = paint(Color.BLACK, 9f)) { c.drawText(l, left, y, p); val w = p.measureText(v); c.drawText(v, right - w, y, p); y += 13f }
        fun header(t: String) { y += 8f; line(t.uppercase(), paint(Color.GRAY, 8f, bold = true), 12f) }
        line("ValueLens Valuation Dossier", paint(Color.BLACK, 18f, bold = true), 20f)
        line("${r.company.displayName} (${r.company.ticker}) · CIK ${r.company.cik} · ${r.generatedAt.take(10)}", paint(Color.DKGRAY, 10f), 18f)
        // The dossier is a record: keep the filed name alongside the display form.
        if (r.company.displayName != r.company.name) line("Filed with the SEC as ${r.company.name}", paint(Color.GRAY, 8f), 12f)
        header("Executive summary")
        line("Market price ${Fmt.money(r.quote?.price)} vs. ${model.label} intrinsic value ${Fmt.money(res.intrinsicValuePerShare)}. Verdict: ${mos.verdictEnum.title}.", paint(Color.BLACK, 9f))
        mos.marginOfSafetyPct?.let { line("Margin of safety ${Fmt.pct(it)}. Buy-below: " + mos.bands.joinToString(", ") { b -> "${b.discountPct.toInt()}% → ${Fmt.money(b.buyBelow)}" }, paint(Color.BLACK, 9f)) }
        line("Model: ${res.name}", paint(Color.DKGRAY, 8f))
        header("Valuation metrics")
        res.metrics.filter { it.key != "fcff_projection" }.forEach { m -> row(m.label, Fmt.metric(m)); line(m.formula, paint(Color.GRAY, 7f, mono = true), 11f) }
        header("Key balance sheet ratios (TTM)")
        listOf("equity" to "Book value", "cash" to "Cash", "total_debt" to "Total debt").forEach { (k, l) -> row(l, r.snapshot[k]?.let { Fmt.compact(it.value) } ?: "—") }
        listOf("current_ratio" to "Current ratio", "debt_to_equity" to "Debt / equity").forEach { (k, l) -> row(l, r.snapshot[k]?.let { Fmt.number(it.value) + "×" } ?: "—") }
        listOf("roic" to "ROIC", "roe" to "ROE").forEach { (k, l) -> row(l, r.snapshot[k]?.let { Fmt.pct(it.value, 1, true) } ?: "—") }
        header("Historical owner earnings")
        r.history.forEach { h -> row("FY${h.fiscalYear}", "${Fmt.compact(h.ownerEarnings)}   (NI ${Fmt.compact(h.netIncome)})") }
        header("Assumptions")
        val a = r.assumptions
        line("AAA ${Fmt.pct(a.aaaYieldPct, 2)} · 10-yr ${Fmt.pct(a.treasury10yPct, 2)} · hurdle ${Fmt.pct(a.hurdleRatePct)} · ERP ${Fmt.pct(a.equityRiskPremiumPct)} · β ${Fmt.number(a.beta)} · g ${Fmt.pct(a.terminalGrowthPct)} · exit ${Fmt.number(a.exitMultiple, 0)}× · ${a.rateSource}", paint(Color.BLACK, 8f))
        y = 760f; line(r.disclaimer, paint(Color.GRAY, 7f))
        doc.finishPage(page)
        val file = File(docsDir(context), "ValueLens-${r.company.ticker}-${System.currentTimeMillis()}.pdf")
        file.outputStream().use { doc.writeTo(it) }; doc.close()
        share(context, file, "application/pdf")
    }

    fun shareCard(context: Context, r: ValuationReport, model: ValuationModel, square: Boolean) {
        val w = if (square) 1080 else 1920; val h = 1080
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp); c.drawColor(BG)
        val pad = if (square) 80f else 70f
        val res = r.result(model); val mos = res.marginOfSafety; val v = mos.verdictEnum
        val vc = when (v) { com.swcsoftware.valuelens.domain.Verdict.DEEP_VALUE, com.swcsoftware.valuelens.domain.Verdict.WITHIN_MARGIN -> MINT; com.swcsoftware.valuelens.domain.Verdict.THIN_MARGIN -> 0xFFFAC73F.toInt(); com.swcsoftware.valuelens.domain.Verdict.ABOVE_INTRINSIC -> RED; else -> T3 }
        c.drawCircle(pad + 28f, pad + 28f, 28f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = MINT; style = Paint.Style.STROKE; strokeWidth = 8f })
        c.drawText("ValueLens", pad + 74f, pad + 44f, paint(T1, 44f, bold = true))
        val sub = "${model.label} · ${model.subtitle}"; val sp = paint(T2, 26f); c.drawText(sub, w - pad - sp.measureText(sub), pad + 40f, sp)
        c.drawText(r.company.ticker, pad, h * 0.42f, paint(T1, 110f, bold = true))
        c.drawText(r.company.displayName, pad, h * 0.42f + 50f, paint(T2, 36f))
        var x = pad; val statY = h * 0.42f + 140f
        fun stat(label: String, value: String, color: Int) { c.drawText(label, x, statY, paint(color, 22f, bold = true)); c.drawText(value, x, statY + 70f, paint(color, 64f, bold = true)); x += paint(color, 64f, bold = true).measureText(value) + 80f }
        stat("MARKET PRICE", Fmt.money(mos.marketPrice), AMBER); stat("INTRINSIC VALUE", Fmt.money(mos.intrinsicValue), MINT)
        mos.marginOfSafetyPct?.let { stat("MARGIN OF SAFETY", (if (it < 0) "−" else "") + Fmt.pct(kotlin.math.abs(it), 0), vc) }
        c.drawText(v.title.uppercase(), pad, statY + 150f, paint(vc, 30f, bold = true))
        c.drawText(r.disclaimer, pad, h - pad, paint(T3, 22f))
        val file = File(docsDir(context), "ValueLens-${r.company.ticker}-${if (square) "1x1" else "16x9"}.png")
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        share(context, file, "image/png")
    }
}
