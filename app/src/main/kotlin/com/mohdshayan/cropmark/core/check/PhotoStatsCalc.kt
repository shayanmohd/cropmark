package com.mohdshayan.cropmark.core.check

import com.mohdshayan.cropmark.core.crop.CropResult
import com.mohdshayan.cropmark.core.crop.FaceGeometry
import com.mohdshayan.cropmark.core.matte.Decontaminate
import kotlin.math.sqrt

/** Measures exposure, lighting balance and background evenness from the working copy. */
object PhotoStatsCalc {

    fun measure(
        argb: IntArray,
        w: Int,
        h: Int,
        face: FaceGeometry,
        alpha: FloatArray?,
        mw: Int,
        mh: Int,
        crop: CropResult,
        keepBackground: Boolean,
        exposureEv: Float,
    ): PhotoStats {
        val gain = Decontaminate.exposureGain(exposureEv)
        // Cheek and forehead region: the middle of the face box, clear of hair and beard.
        val fx0 = face.faceLeftX + (face.faceRightX - face.faceLeftX) * 0.18f
        val fx1 = face.faceRightX - (face.faceRightX - face.faceLeftX) * 0.18f
        val fy0 = face.foreheadY + face.faceHeight * 0.15f
        val fy1 = face.chinY - face.faceHeight * 0.25f
        val midX = face.midlineX
        var sum = 0.0; var n = 0
        var sumL = 0.0; var nL = 0
        var sumR = 0.0; var nR = 0
        val step = maxOf(1, ((fx1 - fx0) / 40f).toInt())
        var y = fy0.toInt().coerceIn(0, h - 1)
        while (y <= fy1.toInt().coerceIn(0, h - 1)) {
            var x = fx0.toInt().coerceIn(0, w - 1)
            while (x <= fx1.toInt().coerceIn(0, w - 1)) {
                val l = (luma(argb[y * w + x]) * gain).coerceAtMost(1f)
                sum += l; n++
                if (x < midX) { sumL += l; nL++ } else { sumR += l; nR++ }
                x += step
            }
            y += step
        }
        var bgStd: Float? = null
        var bgMean = 1f
        var bgTexture = 0f
        if (keepBackground) {
            var s = 0.0; var s2 = 0.0; var c = 0
            var diffSum = 0.0; var diffN = 0
            val bstep = maxOf(1, (crop.width / 60f).toInt())
            var by = crop.top.toInt().coerceIn(0, h - 1)
            val byEnd = (crop.top + crop.height).toInt().coerceIn(0, h - 1)
            val bxStart = crop.left.toInt().coerceIn(0, w - 1)
            val bxEnd = (crop.left + crop.width).toInt().coerceIn(0, w - 1)
            while (by <= byEnd) {
                var bx = bxStart
                while (bx <= bxEnd) {
                    val a = if (alpha != null) {
                        val mx = (bx * mw / w).coerceIn(0, mw - 1)
                        val my = (by * mh / h).coerceIn(0, mh - 1)
                        alpha[my * mw + mx]
                    } else 0f
                    if (a < 0.1f) {
                        val l = luma(argb[by * w + bx]).toDouble()
                        s += l; s2 += l * l; c++
                        if (bx + bstep < w && by + bstep < h) {
                            diffSum += kotlin.math.abs(luma(argb[by * w + bx + bstep]) - l) + kotlin.math.abs(luma(argb[(by + bstep) * w + bx]) - l)
                            diffN += 2
                        }
                    }
                    bx += bstep
                }
                by += bstep
            }
            if (c > 20) {
                val mean = s / c
                bgStd = sqrt((s2 / c - mean * mean).coerceAtLeast(0.0)).toFloat()
                bgMean = mean.toFloat()
                if (diffN > 0) bgTexture = (diffSum / diffN).toFloat()
            }
        }
        return PhotoStats(
            faceLuma = if (n > 0) (sum / n).toFloat() else 0.5f,
            leftFaceLuma = if (nL > 0) (sumL / nL).toFloat() else 0.5f,
            rightFaceLuma = if (nR > 0) (sumR / nR).toFloat() else 0.5f,
            backgroundStd = bgStd,
            backgroundMean = bgMean,
            backgroundTexture = bgTexture,
        )
    }

    private fun luma(c: Int): Float =
        (0.2126f * ((c shr 16) and 0xFF) + 0.7152f * ((c shr 8) and 0xFF) + 0.0722f * (c and 0xFF)) / 255f
}
