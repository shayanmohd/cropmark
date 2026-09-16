package com.mohdshayan.cropmark.core.check

import com.mohdshayan.cropmark.core.crop.FaceGeometry
import com.mohdshayan.cropmark.core.spec.DocSpec
import kotlin.math.abs
import kotlin.math.max

enum class LiveHint { NoFace, TwoFaces, MoveCloser, MoveBack, CenterFace, RaisePhone, LowerPhone, TiltLevel, LookStraight, Ready }

/** A face seen in a camera frame, in coordinates normalised to the viewfinder (0..1 both ways). */
data class LiveFace(
    val crownY: Float,
    val chinY: Float,
    val eyeY: Float,
    val midX: Float,
    val rollDeg: Float,
    val yawDeg: Float,
    val pitchDeg: Float,
) {
    companion object {
        /**
         * A face measured in the frame's own pixels, normalised to it. The angles are measured
         * before normalising: an atan2 over axes scaled differently is not the angle on the wall.
         */
        fun from(g: FaceGeometry, mirror: Boolean): LiveFace {
            val w = g.imageWidth.toFloat().coerceAtLeast(1f)
            val h = g.imageHeight.toFloat().coerceAtLeast(1f)
            val midX = g.midlineX / w
            return LiveFace(
                crownY = g.crownY / h,
                chinY = g.chinY / h,
                eyeY = g.eyeY / h,
                midX = if (mirror) 1f - midX else midX,
                rollDeg = g.rollDeg,
                yawDeg = g.yawDeg,
                pitchDeg = g.pitchDeg,
            )
        }
    }
}

/** Where the spec frame sits in the viewfinder, normalised the same way. */
data class FrameRect(val left: Float, val top: Float, val width: Float, val height: Float) {
    val right: Float get() = left + width
    val bottom: Float get() = top + height
}

/**
 * Turns a live face into one instruction. Size comes first because it decides whether the photo
 * has the resolution, then centring, then height, then pose. The tolerances are looser than the
 * final check because the crop is solved again from the full-resolution photo.
 */
object LiveGuide {

    /** Widest the frame may be, leaving a tenth of the viewfinder on the right for the millimetre ruler. */
    const val MAX_FRAME_WIDTH = 0.8f

    /** The spec frame drawn on the viewfinder: centred, the spec's aspect, filling most of the height. */
    fun frameFor(spec: DocSpec, viewAspect: Float, fill: Float = 0.78f): FrameRect {
        var h = fill
        var w = h * spec.aspect / viewAspect
        if (w > MAX_FRAME_WIDTH) {
            w = MAX_FRAME_WIDTH
            h = w * viewAspect / spec.aspect
        }
        return FrameRect((1f - w) / 2f, (1f - h) / 2f, w, h)
    }

    fun evaluate(faceCount: Int, face: LiveFace?, frame: FrameRect, spec: DocSpec): LiveHint {
        if (faceCount == 0 || face == null) return LiveHint.NoFace
        if (faceCount > 1) return LiveHint.TwoFaces
        val head = (face.chinY - face.crownY) / frame.height
        val range = spec.headFraction
        if (head < range.min * 0.85f) return LiveHint.MoveCloser
        if (head > range.max * 1.1f) return LiveHint.MoveBack
        val offset = (face.midX - (frame.left + frame.width / 2f)) / frame.width
        if (abs(offset) > 0.1f) return LiveHint.CenterFace
        val eyeBand = spec.eyeFraction ?: ComplianceChecker.DEFAULT_EYE_BAND
        val eyeFromBottom = (frame.bottom - face.eyeY) / frame.height
        // Eyes too high in the frame: raising the phone moves the face down the picture.
        if (eyeFromBottom > eyeBand.max + 0.08f) return LiveHint.RaisePhone
        if (eyeFromBottom < eyeBand.min - 0.08f) return LiveHint.LowerPhone
        if (abs(face.rollDeg) >= ComplianceChecker.MAX_ROLL) return LiveHint.TiltLevel
        if (abs(face.yawDeg) >= ComplianceChecker.MAX_YAW_PITCH + 2f || abs(face.pitchDeg) >= ComplianceChecker.MAX_PITCH + 2f) return LiveHint.LookStraight
        return LiveHint.Ready
    }
}
