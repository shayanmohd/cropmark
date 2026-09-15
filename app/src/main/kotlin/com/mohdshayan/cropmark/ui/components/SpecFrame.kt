package com.mohdshayan.cropmark.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mohdshayan.cropmark.core.check.Guide
import com.mohdshayan.cropmark.core.crop.FrameGuides
import com.mohdshayan.cropmark.ui.theme.Cropmark

/**
 * The spec frame: printer's crop-mark brackets at crown and chin, the eye band, and a midline when
 * it matters. Every stroke carries a Backdrop halo so magenta stays visible over skin and hair.
 * When [ready] the brackets fill in.
 */
@Composable
fun SpecFrame(
    guides: FrameGuides,
    modifier: Modifier = Modifier,
    ready: Boolean = false,
    highlight: Guide = Guide.None,
    description: String? = null,
) {
    val magenta = Cropmark.colors.magenta
    val halo = Cropmark.colors.backdrop
    Canvas(modifier.semantics { if (description != null) contentDescription = description }) {
        val base = if (ready) 3.5.dp.toPx() else 2.dp.toPx()
        fun widthFor(g: Guide) = if (highlight == g) 4.5.dp.toPx() else base
        fun alphaFor(g: Guide) = if (highlight == Guide.None || highlight == g) 1f else 0.4f

        // Eye band.
        val bandTop = guides.eyeTop * size.height
        val bandBottom = guides.eyeBottom * size.height
        val bandLeft = size.width * 0.14f
        val bandRight = size.width * 0.86f
        val eyeAlpha = alphaFor(Guide.EyeBand)
        // No tinted fill: a translucent wash turns muddy over dark hair. Dashed edges and solid end
        // ticks mark the band and keep their halo on any photo.
        val dash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
        val eyeWidth = widthFor(Guide.EyeBand) * 0.7f
        for (y in listOf(bandTop, bandBottom)) {
            haloLine(halo, magenta.copy(alpha = eyeAlpha), Offset(bandLeft, y), Offset(bandRight, y), eyeWidth, dash)
        }
        for (x in listOf(bandLeft, bandRight)) {
            haloLine(halo, magenta.copy(alpha = eyeAlpha), Offset(x, bandTop), Offset(x, bandBottom), eyeWidth)
        }

        // Midline.
        if (highlight == Guide.Midline) {
            val x = size.width / 2f
            haloLine(halo, magenta, Offset(x, 0f), Offset(x, size.height), widthFor(Guide.Midline) * 0.6f, dash)
        }

        // Crown and chin brackets.
        val cx = guides.mid * size.width
        val hw = guides.halfWidth * size.width
        val arm = (hw * 0.42f).coerceAtLeast(10.dp.toPx())
        val drop = arm * 0.7f
        val crownY = guides.crown * size.height
        val chinY = guides.chin * size.height
        val w = widthFor(Guide.HeadBrackets)
        val c = magenta.copy(alpha = alphaFor(Guide.HeadBrackets))
        bracket(halo, c, Offset(cx - hw, crownY), arm, drop, w, left = true, top = true, filled = ready)
        bracket(halo, c, Offset(cx + hw, crownY), arm, drop, w, left = false, top = true, filled = ready)
        bracket(halo, c, Offset(cx - hw, chinY), arm, drop, w, left = true, top = false, filled = ready)
        bracket(halo, c, Offset(cx + hw, chinY), arm, drop, w, left = false, top = false, filled = ready)
    }
}

private fun DrawScope.haloLine(halo: Color, color: Color, a: Offset, b: Offset, width: Float, effect: PathEffect? = null) {
    drawLine(halo, a, b, width + 3.dp.toPx(), StrokeCap.Round, effect)
    drawLine(color, a, b, width, StrokeCap.Round, effect)
}

private fun DrawScope.bracket(
    halo: Color, color: Color, corner: Offset, arm: Float, drop: Float, width: Float,
    left: Boolean, top: Boolean, filled: Boolean,
) {
    val hx = if (left) arm else -arm
    val vy = if (top) drop else -drop
    haloLine(halo, color, corner, Offset(corner.x + hx, corner.y), width)
    haloLine(halo, color, corner, Offset(corner.x, corner.y + vy), width)
    if (filled) {
        val s = width * 1.6f
        drawRect(color, Offset(corner.x - s / 2, corner.y - s / 2), Size(s, s))
    }
}
