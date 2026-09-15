package com.mohdshayan.cropmark.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mohdshayan.cropmark.core.crop.FrameGuides
import com.mohdshayan.cropmark.core.spec.DocSpec
import com.mohdshayan.cropmark.ui.theme.Cropmark
import com.mohdshayan.cropmark.ui.theme.RulerStyle
import kotlin.math.roundToInt

/** The photo drawn to its own proportions with a head placed where the spec wants it. */
@Composable
fun SizeDiagram(spec: DocSpec, height: Dp, modifier: Modifier = Modifier) {
    val guides = remember(spec) { FrameGuides.canonical(spec) }
    val c = Cropmark.colors
    val d = spec.digital
    val w = if (spec.print == null && d != null) "${d.minPx} px" else "${fmtMm(spec.widthMm)} mm"
    val h = if (spec.print == null && d != null) "${d.heightFor(d.minPx)} px" else "${fmtMm(spec.heightMm)} mm"
    Row(modifier.height(height + 22.dp), verticalAlignment = Alignment.Top) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.height(height).aspectRatio(spec.aspect)) {
                Canvas(
                    Modifier
                        .fillMaxHeight()
                        .clipToBounds()
                        .aspectRatio(spec.aspect)
                        .semantics { contentDescription = "Photo ${spec.sizeLabel}, head placed to the rules" },
                ) {
                    drawRect(c.panel)
                    drawRect(c.slate, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
                    val cx = guides.mid * size.width
                    val crown = guides.crown * size.height
                    val chin = guides.chin * size.height
                    val headH = chin - crown
                    val headW = headH * 0.74f
                    val silhouette = c.slate.copy(alpha = 0.35f)
                    drawOval(silhouette, Offset(cx - headW / 2, crown), Size(headW, headH))
                    val shoulders = Path().apply {
                        moveTo(cx - headW * 0.3f, chin - headH * 0.05f)
                        cubicTo(cx - headW * 1.3f, chin + headH * 0.12f, cx - headW * 1.5f, chin + headH * 0.3f, cx - headW * 1.6f, size.height)
                        lineTo(cx + headW * 1.6f, size.height)
                        cubicTo(cx + headW * 1.5f, chin + headH * 0.3f, cx + headW * 1.3f, chin + headH * 0.12f, cx + headW * 0.3f, chin - headH * 0.05f)
                        close()
                    }
                    drawPath(shoulders, silhouette)
                }
                SpecFrame(guides, Modifier.fillMaxHeight().aspectRatio(spec.aspect))
            }
            Spacer(Modifier.height(4.dp))
            Text(w, style = RulerStyle, color = c.slate)
        }
        Spacer(Modifier.width(6.dp))
        Box(Modifier.height(height), contentAlignment = Alignment.Center) {
            Text(h, style = RulerStyle, color = c.slate)
        }
    }
}

fun fmtMm(v: Float): String = if (kotlin.math.abs(v - v.roundToInt()) < 0.05f) v.roundToInt().toString() else String.format(java.util.Locale.ROOT, "%.1f", v)
