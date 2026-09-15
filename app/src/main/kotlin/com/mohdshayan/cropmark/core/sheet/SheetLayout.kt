package com.mohdshayan.cropmark.core.sheet

import kotlin.math.floor

enum class Paper(val key: String, val label: String, val widthMm: Float, val heightMm: Float, val isPdf: Boolean) {
    FourBySix("4x6", "4 x 6 in", 101.6f, 152.4f, false),
    FiveBySeven("5x7", "5 x 7 in", 127f, 177.8f, false),
    A4("a4", "A4", 210f, 297f, true),
    Letter("letter", "Letter", 215.9f, 279.4f, true);

    companion object {
        fun fromKey(key: String?): Paper = entries.firstOrNull { it.key == key } ?: FourBySix
    }
}

data class CellMm(val x: Float, val y: Float, val w: Float, val h: Float)

data class SheetPlan(
    val paper: Paper,
    /** Page size in the chosen orientation. */
    val pageWidthMm: Float,
    val pageHeightMm: Float,
    val landscape: Boolean,
    val cols: Int,
    val rows: Int,
    val capacity: Int,
    val cells: List<CellMm>,
    /** Edge-to-edge with no margin or gap: print it with no borders. */
    val tight: Boolean,
    /** Where the 50 mm check ruler starts, on A4 and Letter only. */
    val rulerXmm: Float?,
    val rulerYmm: Float?,
)

/**
 * Lays identical photos on a sheet: 3 mm margins, 2 mm gaps, in whichever orientation fits more.
 * When fewer than four fit that way on photo paper, it switches to an edge-to-edge grid that needs
 * borderless printing. A4 and Letter keep a band at the bottom for a 50 mm check ruler.
 */
object SheetLayout {
    const val MARGIN_MM = 3f
    const val GAP_MM = 2f
    const val RULER_BAND_MM = 10f
    const val RULER_LENGTH_MM = 50f

    fun plan(paper: Paper, photoWmm: Float, photoHmm: Float, copies: Int? = null): SheetPlan {
        val spaced = spaced(paper, photoWmm, photoHmm)
        val best = if (!paper.isPdf && spaced.capacity < 4) {
            val t = tight(paper, photoWmm, photoHmm)
            if (t.capacity > spaced.capacity) t else spaced
        } else spaced
        return limit(best, copies)
    }

    fun spaced(paper: Paper, w: Float, h: Float): SheetPlan {
        val band = if (paper.isPdf) RULER_BAND_MM else 0f
        return bestOrientation(paper, w, h, MARGIN_MM, GAP_MM, band, tight = false)
    }

    fun tight(paper: Paper, w: Float, h: Float): SheetPlan =
        bestOrientation(paper, w, h, 0f, 0f, 0f, tight = true)

    private fun bestOrientation(paper: Paper, w: Float, h: Float, margin: Float, gap: Float, band: Float, tight: Boolean): SheetPlan {
        // PDF pages stay portrait: office printers and print apps expect it.
        val options = if (paper.isPdf) listOf(false) else listOf(false, true)
        return options.map { landscape ->
            val pw = if (landscape) paper.heightMm else paper.widthMm
            val ph = if (landscape) paper.widthMm else paper.heightMm
            layout(paper, pw, ph, landscape, w, h, margin, gap, band, tight)
        }.maxBy { it.capacity }
    }

    private fun layout(
        paper: Paper, pw: Float, ph: Float, landscape: Boolean,
        w: Float, h: Float, margin: Float, gap: Float, band: Float, tight: Boolean,
    ): SheetPlan {
        val usableW = pw - 2 * margin
        val usableH = ph - 2 * margin - band
        val eps = 1e-3f
        val cols = maxOf(0, floor((usableW + gap + eps) / (w + gap)).toInt())
        val rows = maxOf(0, floor((usableH + gap + eps) / (h + gap)).toInt())
        val gridW = cols * w + (cols - 1).coerceAtLeast(0) * gap
        val gridH = rows * h + (rows - 1).coerceAtLeast(0) * gap
        val x0 = margin + (usableW - gridW) / 2f
        val y0 = margin + (usableH - gridH) / 2f
        val cells = buildList {
            for (r in 0 until rows) for (c in 0 until cols) {
                add(CellMm(x0 + c * (w + gap), y0 + r * (h + gap), w, h))
            }
        }
        val ruler = band > 0f
        return SheetPlan(
            paper, pw, ph, landscape, cols, rows, cols * rows, cells, tight,
            rulerXmm = if (ruler) (pw - RULER_LENGTH_MM) / 2f else null,
            rulerYmm = if (ruler) ph - margin - band / 2f else null,
        )
    }

    private fun limit(plan: SheetPlan, copies: Int?): SheetPlan {
        if (copies == null || copies >= plan.capacity) return plan
        return plan.copy(cells = plan.cells.take(copies.coerceAtLeast(1)))
    }
}
