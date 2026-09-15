package com.mohdshayan.cropmark.core.crop

import com.mohdshayan.cropmark.core.check.ComplianceChecker
import com.mohdshayan.cropmark.core.check.FrameRect
import com.mohdshayan.cropmark.core.check.LiveFace
import com.mohdshayan.cropmark.core.spec.DocSpec

/** The spec frame's guide lines as fractions of the photo: from the top for y, from the left for x. */
data class FrameGuides(
    val crown: Float,
    val chin: Float,
    val eyeTop: Float,
    val eyeBottom: Float,
    val mid: Float,
    val halfWidth: Float,
    /** Where the crown may sit for the head height to pass, from the top. */
    val crownRangeTop: Float,
    val crownRangeBottom: Float,
) {
    companion object {
        /** Eyes sit a little under half way from crown to chin on an adult head. */
        private const val EYE_DEPTH = 0.47f

        fun fromCrop(face: FaceGeometry, crop: CropResult, spec: DocSpec): FrameGuides {
            fun fy(y: Float) = (y - crop.top) / crop.height
            val band = spec.eyeFraction ?: ComplianceChecker.DEFAULT_EYE_BAND
            val chin = fy(face.chinY)
            val range = spec.headFraction
            return FrameGuides(
                crown = fy(CropSolver.crownFor(face, spec)),
                chin = chin,
                eyeTop = 1f - band.max,
                eyeBottom = 1f - band.min,
                mid = (face.midlineX - crop.left) / crop.width,
                halfWidth = ((face.faceRightX - face.faceLeftX) * 0.62f / crop.width).coerceIn(0.12f, 0.45f),
                crownRangeTop = chin - range.max,
                crownRangeBottom = chin - range.min,
            )
        }

        /** Where a typical head lands once the crop is solved; used before any face is known. */
        fun canonical(spec: DocSpec): FrameGuides {
            val face = FaceGeometry(
                faceCount = 1, imageWidth = 4000, imageHeight = 4000,
                chinY = 2600f, foreheadY = 1100f + 1500f * 0.17f, crownY = 1100f, crownEstimated = false,
                leftEyeX = 1880f, leftEyeY = 1100f + 1500f * EYE_DEPTH, rightEyeX = 2120f, rightEyeY = 1100f + 1500f * EYE_DEPTH,
                midlineX = 2000f, faceLeftX = 1560f, faceRightX = 2440f,
            )
            return fromCrop(face, CropSolver.solve(face, spec), spec)
        }

        /** Guides following a face tracked in the viewfinder, relative to the frame rectangle. */
        fun fromLive(face: LiveFace, frame: FrameRect, spec: DocSpec): FrameGuides {
            val c = canonical(spec)
            val crown = (face.crownY - frame.top) / frame.height
            val chin = (face.chinY - frame.top) / frame.height
            val range = spec.headFraction
            return c.copy(
                crown = crown,
                chin = chin,
                mid = (face.midX - frame.left) / frame.width,
                halfWidth = ((chin - crown) * 0.55f * frame.height / frame.width).coerceIn(0.12f, 0.45f),
                crownRangeTop = chin - range.max,
                crownRangeBottom = chin - range.min,
            )
        }
    }
}
