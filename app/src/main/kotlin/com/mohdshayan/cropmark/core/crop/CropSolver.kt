package com.mohdshayan.cropmark.core.crop

import com.mohdshayan.cropmark.core.spec.DocSpec
import kotlin.math.abs

/** The user's fine tune on top of the automatic crop. Scale 1 means the middle of the head range. */
data class Nudge(val scale: Float = 1f, val xMm: Float = 0f, val yMm: Float = 0f)

/** A crop rectangle in working-copy pixels plus where the face landed inside it. */
data class CropResult(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
    val headFraction: Float,
    /** Eye line measured up from the bottom edge, as a fraction of the height. */
    val eyeFraction: Float,
    /** Face midline minus crop centre, as a fraction of the width. */
    val centerOffset: Float,
    val nudge: Nudge,
    val mmPerPx: Float,
)

/**
 * Solves the crop for a spec: head height to the middle of the allowed range, eyes to the middle of
 * the eye band (or a balanced crown gap when the issuer gives no band), midline centred. Nudges are
 * clamped so the result still satisfies the spec.
 */
object CropSolver {

    /** How far a horizontal nudge may move the face off centre, as a fraction of the width. */
    const val MAX_CENTER_SHIFT = 0.04f

    /**
     * The crown row the issuer measures to. Most count hair; a skull rule never sits above the
     * forehead-proportion estimate, so hair piled high does not shrink the head.
     */
    fun crownFor(face: FaceGeometry, spec: DocSpec): Float =
        if (spec.crownAtSkull) maxOf(face.crownY, CrownFinder.estimate(face.foreheadY, face.chinY)) else face.crownY

    fun solve(face: FaceGeometry, spec: DocSpec, nudge: Nudge = Nudge()): CropResult {
        val crownY = crownFor(face, spec)
        val headPx = (face.chinY - crownY).coerceAtLeast(1f)
        val range = spec.headFraction
        val mid = range.mid
        // Keep a sliver inside the limits so rounding at output size never lands on the boundary.
        val inset = (range.max - range.min) * 0.05f
        val lo = range.min + inset
        val hi = range.max - inset
        val target = (mid * nudge.scale).coerceIn(lo, hi)
        val scale = target / mid

        val cropH = headPx / target
        val cropW = cropH * spec.aspect
        val mmPerPx = spec.heightMm / cropH
        val eyeY = face.eyeY

        // Vertical placement before the nudge.
        val eyeRange = spec.eyeFraction
        val baseTop = if (eyeRange != null) {
            eyeY - cropH * (1f - eyeRange.mid)
        } else {
            // No eye rule: eyes to the middle of the usual band, but never tighter than a 3 percent crown gap.
            val band = com.mohdshayan.cropmark.core.check.ComplianceChecker.DEFAULT_EYE_BAND
            minOf(eyeY - cropH * (1f - band.mid), crownY - 0.03f * cropH)
                .coerceAtLeast(face.chinY - cropH * 0.99f)
        }

        // Every top that keeps crown and chin inside and the eyes in their band.
        var topMin = face.chinY - cropH
        var topMax = crownY
        if (eyeRange != null) {
            val eyeInset = (eyeRange.max - eyeRange.min) * 0.05f
            topMin = maxOf(topMin, eyeY - cropH * (1f - (eyeRange.min + eyeInset)))
            topMax = minOf(topMax, eyeY - cropH * (1f - (eyeRange.max - eyeInset)))
        }
        val wantedTop = baseTop - nudge.yMm / mmPerPx
        val top = if (topMin <= topMax) wantedTop.coerceIn(topMin, topMax) else baseTop
        val yMm = (baseTop - top) * mmPerPx

        val maxShiftPx = cropW * MAX_CENTER_SHIFT
        val shiftPx = (nudge.xMm / mmPerPx).coerceIn(-maxShiftPx, maxShiftPx)
        val left = face.midlineX - cropW / 2f - shiftPx
        val xMm = shiftPx * mmPerPx

        return CropResult(
            left = left,
            top = top,
            width = cropW,
            height = cropH,
            headFraction = headPx / cropH,
            eyeFraction = 1f - (eyeY - top) / cropH,
            centerOffset = (face.midlineX - (left + cropW / 2f)) / cropW,
            nudge = Nudge(scale, xMm, yMm),
            mmPerPx = mmPerPx,
        )
    }

    /** True when the crop needs pixels outside the photo, which a kept background cannot supply. */
    fun overhangs(crop: CropResult, imageWidth: Int, imageHeight: Int): Boolean {
        val tol = 1f
        return crop.left < -tol || crop.top < -tol ||
            crop.left + crop.width > imageWidth + tol || crop.top + crop.height > imageHeight + tol
    }

    fun isCentred(crop: CropResult): Boolean = abs(crop.centerOffset) <= 0.05f
}
