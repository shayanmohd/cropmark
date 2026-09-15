package com.mohdshayan.cropmark.core.check

import com.mohdshayan.cropmark.core.crop.CropResult
import com.mohdshayan.cropmark.core.crop.FaceGeometry
import com.mohdshayan.cropmark.core.spec.DocSpec
import kotlin.math.abs
import kotlin.math.max

enum class CheckId { OneFace, HeadHeight, EyeLine, Centred, Level, FacingCamera, EyesOpen, MouthClosed, Exposure, Lighting, Background, Resolution }

/** The guide on the photo a failed check points at. */
enum class Guide { None, HeadBrackets, EyeBand, Midline }

data class CheckResult(
    val id: CheckId,
    val passed: Boolean,
    /** Advisory checks count toward the total but some issuers accept them. */
    val advisory: Boolean = false,
    /** The measured value, in the unit the check reads naturally (degrees, fraction, ratio). */
    val measured: Float = 0f,
    val guide: Guide = Guide.None,
    /** For FacingCamera: true when [measured] is the up or down tip, false when it is the sideways turn. */
    val pitchAxis: Boolean = false,
)

/** Photo statistics measured by the renderer, all luminance in 0..1. */
data class PhotoStats(
    val faceLuma: Float,
    val leftFaceLuma: Float,
    val rightFaceLuma: Float,
    /** Standard deviation of luminance over the kept background, or null when it was replaced. */
    val backgroundStd: Float?,
    /** Mean luminance of the kept background. */
    val backgroundMean: Float = 1f,
    /** Mean luminance step between neighbouring samples: pattern such as bricks or tiles. */
    val backgroundTexture: Float = 0f,
)

data class CheckInput(
    val spec: DocSpec,
    val face: FaceGeometry,
    val crop: CropResult?,
    val stats: PhotoStats?,
    val outputHeightPx: Int,
    /** The background is kept as shot but the crop reaches past the photo's edge, so the edge would be filled. */
    val keptBackgroundOverhangs: Boolean = false,
)

object ComplianceChecker {
    const val MAX_ROLL = 5f
    const val MAX_YAW_PITCH = 8f
    /** Pitch from a single photo carries the camera's height, so it gets more room than yaw. */
    const val MAX_PITCH = 12f
    const val BLINK_LIMIT = 0.5f
    const val JAW_LIMIT = 0.25f
    const val EXPOSURE_MIN = 0.32f
    const val EXPOSURE_MAX = 0.85f
    const val LIGHTING_MAX_DIFF = 0.22f
    const val BACKGROUND_MAX_STD = 0.07f
    const val BACKGROUND_MIN_LUMA = 0.6f
    const val BACKGROUND_MAX_TEXTURE = 0.035f
    const val RESOLUTION_FACTOR = 0.8f

    fun run(input: CheckInput): List<CheckResult> {
        val face = input.face
        val crop = input.crop
        val spec = input.spec
        val results = mutableListOf<CheckResult>()
        results += CheckResult(CheckId.OneFace, face.faceCount == 1, measured = face.faceCount.toFloat())
        if (face.faceCount != 1 || crop == null) {
            return results + CheckId.entries.drop(1).map { CheckResult(it, passed = false) }
        }

        results += CheckResult(
            CheckId.HeadHeight, crop.headFraction in spec.headFraction,
            measured = crop.headFraction, guide = Guide.HeadBrackets,
        )
        val eyeBand = spec.eyeFraction ?: DEFAULT_EYE_BAND
        results += CheckResult(CheckId.EyeLine, crop.eyeFraction in eyeBand, measured = crop.eyeFraction, guide = Guide.EyeBand)
        results += CheckResult(CheckId.Centred, abs(crop.centerOffset) <= 0.05f, measured = crop.centerOffset, guide = Guide.Midline)
        results += CheckResult(CheckId.Level, abs(face.rollDeg) < MAX_ROLL, measured = face.rollDeg, guide = Guide.EyeBand)
        val facing = abs(face.yawDeg) < MAX_YAW_PITCH && abs(face.pitchDeg) < MAX_PITCH
        // Report the axis closest to its own limit, so the number shown matches the limit shown.
        val pitchWorse = abs(face.pitchDeg) / MAX_PITCH > abs(face.yawDeg) / MAX_YAW_PITCH
        val turn = if (pitchWorse) abs(face.pitchDeg) else abs(face.yawDeg)
        results += CheckResult(CheckId.FacingCamera, facing, measured = turn, guide = Guide.Midline, pitchAxis = pitchWorse)
        val blink = max(face.leftEyeBlink, face.rightEyeBlink)
        results += CheckResult(CheckId.EyesOpen, blink < BLINK_LIMIT, measured = blink, guide = Guide.EyeBand)
        results += CheckResult(CheckId.MouthClosed, face.jawOpen < JAW_LIMIT, advisory = true, measured = face.jawOpen)

        val stats = input.stats
        if (stats == null) {
            results += CheckResult(CheckId.Exposure, true)
            results += CheckResult(CheckId.Lighting, true)
            results += CheckResult(CheckId.Background, true)
        } else {
            results += CheckResult(CheckId.Exposure, stats.faceLuma in EXPOSURE_MIN..EXPOSURE_MAX, measured = stats.faceLuma)
            val brighter = max(stats.leftFaceLuma, stats.rightFaceLuma).coerceAtLeast(1e-3f)
            val diff = abs(stats.leftFaceLuma - stats.rightFaceLuma) / brighter
            results += CheckResult(CheckId.Lighting, diff <= LIGHTING_MAX_DIFF, measured = diff)
            val std = stats.backgroundStd
            val plain = std == null || (std <= BACKGROUND_MAX_STD && stats.backgroundMean >= BACKGROUND_MIN_LUMA && stats.backgroundTexture <= BACKGROUND_MAX_TEXTURE)
            results += CheckResult(CheckId.Background, plain, measured = std ?: 0f)
        }
        if (input.keptBackgroundOverhangs) {
            // A filled strip is not the wall behind the person: the photo was shot too close.
            val i = results.indexOfFirst { it.id == CheckId.Background }
            results[i] = CheckResult(CheckId.Background, false, measured = OVERHANG)
        }
        val needed = input.outputHeightPx * RESOLUTION_FACTOR
        results += CheckResult(CheckId.Resolution, crop.height >= needed, measured = crop.height)
        return results
    }

    /** Background check value meaning the crop runs past the photo's edge. */
    const val OVERHANG = -1f

    fun passedCount(results: List<CheckResult>): Int = results.count { it.passed }

    /** Where eyes sit on an ICAO photo when the issuer gives no band. */
    val DEFAULT_EYE_BAND = com.mohdshayan.cropmark.core.spec.Range(0.42f, 0.72f)
}
