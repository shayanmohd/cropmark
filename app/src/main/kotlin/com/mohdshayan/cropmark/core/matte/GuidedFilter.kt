package com.mohdshayan.cropmark.core.matte

/**
 * The guided filter of He, Sun and Tang (2010) on single-channel float images, plus the bilinear
 * upsample it refines. The segmenter's 256 px mask is upsampled to the working copy and then
 * filtered against image luminance, which pulls the soft edge onto real edges such as hair strands.
 */
object GuidedFilter {

    /** Mean over a (2r+1) square window, clamped at the borders, in O(n) with an integral image. */
    fun boxMean(src: FloatArray, w: Int, h: Int, r: Int): FloatArray {
        val iw = w + 1
        val integral = DoubleArray(iw * (h + 1))
        for (y in 0 until h) {
            var row = 0.0
            val base = (y + 1) * iw
            val prev = y * iw
            for (x in 0 until w) {
                row += src[y * w + x]
                integral[base + x + 1] = integral[prev + x + 1] + row
            }
        }
        val out = FloatArray(w * h)
        for (y in 0 until h) {
            val y0 = maxOf(0, y - r)
            val y1 = minOf(h - 1, y + r)
            for (x in 0 until w) {
                val x0 = maxOf(0, x - r)
                val x1 = minOf(w - 1, x + r)
                val sum = integral[(y1 + 1) * iw + x1 + 1] - integral[y0 * iw + x1 + 1] -
                    integral[(y1 + 1) * iw + x0] + integral[y0 * iw + x0]
                val n = (y1 - y0 + 1) * (x1 - x0 + 1)
                out[y * w + x] = (sum / n).toFloat()
            }
        }
        return out
    }

    fun filter(guide: FloatArray, p: FloatArray, w: Int, h: Int, r: Int, eps: Float): FloatArray {
        require(guide.size == w * h && p.size == w * h)
        val meanI = boxMean(guide, w, h, r)
        val meanP = boxMean(p, w, h, r)
        val ip = FloatArray(w * h) { guide[it] * p[it] }
        val ii = FloatArray(w * h) { guide[it] * guide[it] }
        val meanIp = boxMean(ip, w, h, r)
        val meanII = boxMean(ii, w, h, r)
        val a = FloatArray(w * h)
        val b = FloatArray(w * h)
        for (i in 0 until w * h) {
            val cov = meanIp[i] - meanI[i] * meanP[i]
            val v = meanII[i] - meanI[i] * meanI[i]
            a[i] = cov / (v + eps)
            b[i] = meanP[i] - a[i] * meanI[i]
        }
        val meanA = boxMean(a, w, h, r)
        val meanB = boxMean(b, w, h, r)
        return FloatArray(w * h) { (meanA[it] * guide[it] + meanB[it]).coerceIn(0f, 1f) }
    }

    fun bilinearUpsample(src: FloatArray, sw: Int, sh: Int, dw: Int, dh: Int): FloatArray {
        val out = FloatArray(dw * dh)
        val sx = sw.toFloat() / dw
        val sy = sh.toFloat() / dh
        for (y in 0 until dh) {
            val fy = ((y + 0.5f) * sy - 0.5f).coerceIn(0f, (sh - 1).toFloat())
            val y0 = fy.toInt()
            val y1 = minOf(y0 + 1, sh - 1)
            val ty = fy - y0
            for (x in 0 until dw) {
                val fx = ((x + 0.5f) * sx - 0.5f).coerceIn(0f, (sw - 1).toFloat())
                val x0 = fx.toInt()
                val x1 = minOf(x0 + 1, sw - 1)
                val tx = fx - x0
                val top = src[y0 * sw + x0] * (1 - tx) + src[y0 * sw + x1] * tx
                val bottom = src[y1 * sw + x0] * (1 - tx) + src[y1 * sw + x1] * tx
                out[y * dw + x] = top * (1 - ty) + bottom * ty
            }
        }
        return out
    }

    /** Rec. 709 luminance in 0..1 from packed ARGB. */
    fun luminance(argb: IntArray): FloatArray = FloatArray(argb.size) {
        val c = argb[it]
        val r = (c shr 16) and 0xFF
        val g = (c shr 8) and 0xFF
        val b = c and 0xFF
        (0.2126f * r + 0.7152f * g + 0.0722f * b) / 255f
    }

    /** Full refinement: upsample the model mask to the image and filter it against luminance. */
    fun refine(mask: FloatArray, mw: Int, mh: Int, argb: IntArray, w: Int, h: Int): FloatArray {
        val up = bilinearUpsample(mask, mw, mh, w, h)
        val r = maxOf(2, maxOf(w, h) / 160)
        return filter(luminance(argb), up, w, h, r, 1e-3f)
    }
}
