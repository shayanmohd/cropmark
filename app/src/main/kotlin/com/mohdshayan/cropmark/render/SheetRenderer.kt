package com.mohdshayan.cropmark.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.mohdshayan.cropmark.core.sheet.SheetPlan
import com.mohdshayan.cropmark.core.sheet.SheetLayout
import java.io.ByteArrayOutputStream

/**
 * Draws a sheet plan. Photo paper sheets are 300 dpi JPEGs for kiosks; A4 and Letter are one-page
 * PDFs with a 50 mm check ruler. Paper, cut lines and ruler use print colours, not theme colours.
 */
object SheetRenderer {
    private const val DPI = 300f
    private const val PAPER = 0xFFFFFFFF.toInt()
    private const val CUT = 0xFFB5B5B5.toInt()
    private const val PRINT_INK = 0xFF222222.toInt()

    fun renderJpeg(plan: SheetPlan, photo: Bitmap, cutLines: Boolean): ByteArray {
        val pxPerMm = DPI / 25.4f
        val w = Math.round(plan.pageWidthMm * pxPerMm)
        val h = Math.round(plan.pageHeightMm * pxPerMm)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        draw(Canvas(bmp), plan, photo, cutLines, pxPerMm, 1f)
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 95, out)
        bmp.recycle()
        return com.mohdshayan.cropmark.core.jpeg.JfifDensity.withDensity(out.toByteArray(), DPI.toInt())
    }

    fun renderPdf(plan: SheetPlan, photo: Bitmap, cutLines: Boolean): ByteArray {
        val ptPerMm = 72f / 25.4f
        val doc = PdfDocument()
        val info = PdfDocument.PageInfo.Builder(Math.round(plan.pageWidthMm * ptPerMm), Math.round(plan.pageHeightMm * ptPerMm), 1).create()
        val page = doc.startPage(info)
        draw(page.canvas, plan, photo, cutLines, ptPerMm, 0.35f)
        doc.finishPage(page)
        val out = ByteArrayOutputStream()
        doc.writeTo(out)
        doc.close()
        return out.toByteArray()
    }

    /** A small preview of the sheet for the export screen. */
    fun preview(plan: SheetPlan, photo: Bitmap, cutLines: Boolean, longSidePx: Int): Bitmap {
        val scale = longSidePx / maxOf(plan.pageWidthMm, plan.pageHeightMm)
        val bmp = Bitmap.createBitmap(Math.round(plan.pageWidthMm * scale), Math.round(plan.pageHeightMm * scale), Bitmap.Config.ARGB_8888)
        draw(Canvas(bmp), plan, photo, cutLines, scale, 1f)
        return bmp
    }

    private fun draw(canvas: Canvas, plan: SheetPlan, photo: Bitmap, cutLines: Boolean, unit: Float, stroke: Float) {
        canvas.drawColor(PAPER)
        val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        val cut = Paint().apply { color = CUT; strokeWidth = maxOf(stroke, unit * 0.12f) }
        if (cutLines && plan.cells.isNotEmpty()) {
            val xs = plan.cells.flatMap { listOf(it.x, it.x + it.w) }.distinct()
            val ys = plan.cells.flatMap { listOf(it.y, it.y + it.h) }.distinct()
            val top = plan.cells.minOf { it.y } - 2.5f
            val bottom = plan.cells.maxOf { it.y + it.h } + 2.5f
            val left = plan.cells.minOf { it.x } - 2.5f
            val right = plan.cells.maxOf { it.x + it.w } + 2.5f
            xs.forEach { x -> canvas.drawLine(x * unit, maxOf(0f, top) * unit, x * unit, minOf(plan.pageHeightMm, bottom) * unit, cut) }
            ys.forEach { y -> canvas.drawLine(maxOf(0f, left) * unit, y * unit, minOf(plan.pageWidthMm, right) * unit, y * unit, cut) }
        }
        plan.cells.forEach { c ->
            canvas.drawBitmap(photo, null, RectF(c.x * unit, c.y * unit, (c.x + c.w) * unit, (c.y + c.h) * unit), bitmapPaint)
        }
        if (cutLines && plan.tight) {
            // Edge to edge there are no gaps, so the lines go on top of the photo borders.
            val xs = plan.cells.map { it.x }.distinct().drop(1)
            val ys = plan.cells.map { it.y }.distinct().drop(1)
            xs.forEach { x -> canvas.drawLine(x * unit, 0f, x * unit, plan.pageHeightMm * unit, cut) }
            ys.forEach { y -> canvas.drawLine(0f, y * unit, plan.pageWidthMm * unit, y * unit, cut) }
        }
        val rx = plan.rulerXmm
        val ry = plan.rulerYmm
        if (rx != null && ry != null) {
            val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PRINT_INK; strokeWidth = maxOf(stroke, unit * 0.2f) }
            canvas.drawLine(rx * unit, ry * unit, (rx + SheetLayout.RULER_LENGTH_MM) * unit, ry * unit, ink)
            for (mm in 0..SheetLayout.RULER_LENGTH_MM.toInt()) {
                val len = when { mm % 10 == 0 -> 2.5f; mm % 5 == 0 -> 1.8f; else -> 1f }
                canvas.drawLine((rx + mm) * unit, ry * unit, (rx + mm) * unit, (ry - len) * unit, ink)
            }
            val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = PRINT_INK
                textSize = 2.6f * unit
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            }
            canvas.drawText("Check ruler: this line prints 50 mm long at actual size", rx * unit, (ry + 3.6f) * unit, text)
        }
    }
}
