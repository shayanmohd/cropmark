package com.mohdshayan.cropmark.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.mohdshayan.cropmark.core.crop.FrameGuides
import com.mohdshayan.cropmark.ui.theme.Cropmark
import com.mohdshayan.cropmark.ui.theme.RulerStyle
import kotlin.math.roundToInt

/**
 * A millimetre ruler beside the photo, 0 at the top edge. The magenta bar shows where the crown
 * may fall for the head height to pass, measured up from the chin.
 */
@Composable
fun MmRuler(heightMm: Float, guides: FrameGuides?, modifier: Modifier = Modifier, percent: Boolean = false) {
    val c = Cropmark.colors
    val measurer = rememberTextMeasurer()
    val style = RulerStyle.copy(color = c.slate)
    val unit = if (percent) "percent" else "millimetres"
    val desc = guides?.let {
        val lo = (it.crownRangeTop * heightMm).roundToInt().coerceIn(0, heightMm.roundToInt())
        val hi = (it.crownRangeBottom * heightMm).roundToInt().coerceIn(0, heightMm.roundToInt())
        "Ruler in $unit. Crown should fall between $lo and $hi $unit from the top."
    } ?: "Ruler in $unit"
    Canvas(modifier.semantics { contentDescription = desc }) {
        val pxPerMm = size.height / heightMm
        val step = when {
            pxPerMm >= 5.dp.toPx() -> 1
            pxPerMm >= 2.5.dp.toPx() -> 2
            else -> 5
        }
        val x0 = 0f
        drawLine(c.slate, Offset(x0, 0f), Offset(x0, size.height), 1.dp.toPx())
        var mm = 0
        while (mm <= heightMm + 0.01f) {
            val y = mm * pxPerMm
            val len = when {
                mm % 10 == 0 -> 12.dp.toPx()
                mm % 5 == 0 -> 8.dp.toPx()
                else -> 4.dp.toPx()
            }
            drawLine(c.slate, Offset(x0, y), Offset(x0 + len, y), 1.dp.toPx())
            if (mm % 10 == 0 && mm > 0 && y < size.height - 6.dp.toPx()) {
                val t = measurer.measure(mm.toString(), style)
                drawText(t, topLeft = Offset(x0 + 14.dp.toPx(), y - t.size.height / 2f))
            }
            mm += step
        }
        if (guides != null) {
            val top = (guides.crownRangeTop * size.height).coerceIn(0f, size.height)
            val bottom = (guides.crownRangeBottom * size.height).coerceIn(0f, size.height)
            drawLine(c.magenta, Offset(x0 + 3.dp.toPx(), top), Offset(x0 + 3.dp.toPx(), bottom), 5.dp.toPx(), StrokeCap.Butt)
            val chin = (guides.chin * size.height).coerceIn(0f, size.height)
            drawLine(c.magenta, Offset(x0, chin), Offset(x0 + 12.dp.toPx(), chin), 2.dp.toPx())
        }
    }
}
