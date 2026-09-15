package com.mohdshayan.cropmark.core.crop

/** The crown row in working-copy pixels and whether it was measured from the matte or estimated. */
data class CrownResult(val y: Float, val estimated: Boolean)

/**
 * Finds the top of the head, hair included, by walking up the person matte from the forehead
 * landmark in a band around the face midline. When there is no matte, or what it shows is not a
 * plausible head, it falls back to the forehead plus a proportion of the face height.
 */
object CrownFinder {

    /** Chin to crown is about 1.2 times chin to the forehead landmark on an adult head. */
    const val FOREHEAD_TO_CROWN = 0.2f

    fun estimate(foreheadY: Float, chinY: Float): Float =
        (foreheadY - (chinY - foreheadY) * FOREHEAD_TO_CROWN).coerceAtLeast(0f)

    fun find(
        matte: FloatArray?,
        width: Int,
        height: Int,
        midlineX: Float,
        foreheadY: Float,
        chinY: Float,
        faceHalfWidth: Float,
    ): CrownResult {
        val fallback = CrownResult(estimate(foreheadY, chinY), estimated = true)
        if (matte == null || matte.size != width * height) return fallback
        val faceH = chinY - foreheadY
        if (faceH <= 0f) return fallback

        val band = (faceHalfWidth * 0.35f).coerceAtLeast(2f)
        val x0 = (midlineX - band).toInt().coerceIn(0, width - 1)
        val x1 = (midlineX + band).toInt().coerceIn(0, width - 1)
        val startY = foreheadY.toInt().coerceIn(0, height - 1)
        val limitY = (foreheadY - faceH * 0.9f).toInt().coerceAtLeast(0)

        // The forehead itself must be inside the person, or the matte is not describing this face.
        if (rowMean(matte, width, startY, x0, x1) < 0.5f) return fallback

        var crown = limitY
        var y = startY
        while (y > limitY) {
            if (rowMean(matte, width, y - 1, x0, x1) < 0.5f) {
                crown = y
                break
            }
            y--
        }
        val gap = foreheadY - crown
        return if (gap < faceH * 0.06f) fallback else CrownResult(crown.toFloat(), estimated = false)
    }

    private fun rowMean(m: FloatArray, w: Int, y: Int, x0: Int, x1: Int): Float {
        var s = 0f
        for (x in x0..x1) s += m[y * w + x]
        return s / (x1 - x0 + 1)
    }
}
