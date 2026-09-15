package com.mohdshayan.cropmark.core.matte

import kotlin.math.pow

/**
 * Edge clean-up and compositing on packed ARGB pixels. Decontamination estimates the old
 * background colour around each edge pixel and removes its share, so a brick-red fringe does not
 * survive on hair laid over a white background.
 */
object Decontaminate {

    /**
     * The local colour of what is behind the person, around every pixel: a box mean of the image
     * weighted by how much of each pixel is background. Computed once at matte resolution.
     */
    fun estimateBackground(argb: IntArray, alpha: FloatArray, w: Int, h: Int, r: Int): IntArray {
        val inv = FloatArray(w * h) { 1f - alpha[it] }
        val meanInv = GuidedFilter.boxMean(inv, w, h, r)
        val bg = Array(3) { ch ->
            val weighted = FloatArray(w * h) { i -> channel(argb[i], ch) * inv[i] }
            GuidedFilter.boxMean(weighted, w, h, r)
        }
        return IntArray(w * h) { i ->
            val d = meanInv[i]
            if (d < 1e-3f) argb[i] else {
                val r0 = (bg[0][i] / d).toInt().coerceIn(0, 255)
                val g0 = (bg[1][i] / d).toInt().coerceIn(0, 255)
                val b0 = (bg[2][i] / d).toInt().coerceIn(0, 255)
                (0xFF shl 24) or (r0 shl 16) or (g0 shl 8) or b0
            }
        }
    }

    /** Removes the background's share from one channel of an edge pixel. */
    fun unmix(observed: Float, background: Float, alpha: Float): Float =
        if (alpha <= 0.02f || alpha >= 0.98f) observed else ((observed - (1f - alpha) * background) / alpha).coerceIn(0f, 255f)

    fun estimateForeground(argb: IntArray, alpha: FloatArray, w: Int, h: Int, r: Int): IntArray {
        val bg = estimateBackground(argb, alpha, w, h, r)
        return IntArray(w * h) { i ->
            var rgb = 0
            for (ch in 0 until 3) {
                val f = unmix(channel(argb[i], ch), channel(bg[i], ch), alpha[i])
                rgb = rgb or (f.toInt() shl (16 - 8 * ch))
            }
            (0xFF shl 24) or rgb
        }
    }

    /** Five feather steps: 0 is a crisp edge, 4 keeps the matte's full softness. */
    fun feather(alpha: FloatArray, level: Int): FloatArray {
        val half = when (level.coerceIn(0, 4)) {
            0 -> 0.08f
            1 -> 0.16f
            2 -> 0.26f
            3 -> 0.38f
            else -> 0.5f
        }
        val lo = 0.5f - half
        val hi = 0.5f + half
        return FloatArray(alpha.size) {
            val t = ((alpha[it] - lo) / (hi - lo)).coerceIn(0f, 1f)
            t * t * (3 - 2 * t)
        }
    }

    fun exposureGain(ev: Float): Float = 2f.pow(ev)

    fun channel(c: Int, ch: Int): Float = ((c shr (16 - 8 * ch)) and 0xFF).toFloat()

    /**
     * Renders the crop to an output of [outW] x [outH]. Colour comes from the full-size [src]; the
     * matte [alpha] and the background estimate [bgEstimate] are at matte size ([mw] x [mh]) and are
     * sampled at the same relative position. Everything that is not person becomes [bgArgb]. With
     * [keepBackground] the original pixels are used whole and only the area outside the photo takes
     * the background colour.
     */
    fun renderCrop(
        src: IntArray,
        w: Int,
        h: Int,
        alpha: FloatArray?,
        bgEstimate: IntArray?,
        mw: Int,
        mh: Int,
        left: Float,
        top: Float,
        cropW: Float,
        cropH: Float,
        outW: Int,
        outH: Int,
        bgArgb: Int,
        exposureEv: Float,
        keepBackground: Boolean,
    ): IntArray {
        val out = IntArray(outW * outH)
        val gain = exposureGain(exposureEv)
        val sx = cropW / outW
        val sy = cropH / outH
        val mxScale = mw.toFloat() / w
        val myScale = mh.toFloat() / h
        val bgc = floatArrayOf(channel(bgArgb, 0), channel(bgArgb, 1), channel(bgArgb, 2))
        val useMatte = !keepBackground && alpha != null
        for (y in 0 until outH) {
            val fy = top + (y + 0.5f) * sy - 0.5f
            for (x in 0 until outW) {
                val fx = left + (x + 0.5f) * sx - 0.5f
                if (fx < -0.5f || fy < -0.5f || fx > w - 0.5f || fy > h - 0.5f) {
                    out[y * outW + x] = bgArgb or (0xFF shl 24)
                    continue
                }
                var a = 1f
                var mi = -1
                if (useMatte) {
                    val mx = ((fx + 0.5f) * mxScale - 0.5f).coerceIn(0f, (mw - 1).toFloat())
                    val my = ((fy + 0.5f) * myScale - 0.5f).coerceIn(0f, (mh - 1).toFloat())
                    a = sample(alpha!!, mw, mh, mx, my)
                    mi = (my + 0.5f).toInt().coerceAtMost(mh - 1) * mw + (mx + 0.5f).toInt().coerceAtMost(mw - 1)
                }
                val cx = fx.coerceIn(0f, (w - 1).toFloat())
                val cy = fy.coerceIn(0f, (h - 1).toFloat())
                val x0 = cx.toInt()
                val y0 = cy.toInt()
                val x1 = minOf(x0 + 1, w - 1)
                val y1 = minOf(y0 + 1, h - 1)
                val tx = cx - x0
                val ty = cy - y0
                var rgb = 0
                for (ch in 0 until 3) {
                    var v = lerp2(
                        channel(src[y0 * w + x0], ch), channel(src[y0 * w + x1], ch),
                        channel(src[y1 * w + x0], ch), channel(src[y1 * w + x1], ch), tx, ty,
                    )
                    if (mi >= 0 && bgEstimate != null) v = unmix(v, channel(bgEstimate[mi], ch), a)
                    v = (v * gain).coerceIn(0f, 255f)
                    val o = v * a + bgc[ch] * (1f - a)
                    rgb = rgb or ((o + 0.5f).toInt().coerceIn(0, 255) shl (16 - 8 * ch))
                }
                out[y * outW + x] = (0xFF shl 24) or rgb
            }
        }
        return out
    }

    private fun sample(m: FloatArray, mw: Int, mh: Int, x: Float, y: Float): Float {
        val x0 = x.toInt()
        val y0 = y.toInt()
        val x1 = minOf(x0 + 1, mw - 1)
        val y1 = minOf(y0 + 1, mh - 1)
        return lerp2(m[y0 * mw + x0], m[y0 * mw + x1], m[y1 * mw + x0], m[y1 * mw + x1], x - x0, y - y0)
    }

    private fun lerp2(a: Float, b: Float, c: Float, d: Float, tx: Float, ty: Float): Float =
        (a * (1 - tx) + b * tx) * (1 - ty) + (c * (1 - tx) + d * tx) * ty
}
