package com.mohdshayan.cropmark.core.crop

import kotlinx.serialization.Serializable

/**
 * Everything the pipeline keeps about the face in a stored capture, in pixels of the working copy.
 * Serialised into PhotoCapture.faceJson so a re-export never runs the models again.
 */
@Serializable
data class FaceGeometry(
    val faceCount: Int,
    val imageWidth: Int,
    val imageHeight: Int,
    val chinX: Float = 0f,
    val chinY: Float = 0f,
    val foreheadY: Float = 0f,
    val crownY: Float = 0f,
    val crownEstimated: Boolean = true,
    val leftEyeX: Float = 0f,
    val leftEyeY: Float = 0f,
    val rightEyeX: Float = 0f,
    val rightEyeY: Float = 0f,
    val midlineX: Float = 0f,
    val faceLeftX: Float = 0f,
    val faceRightX: Float = 0f,
    val rollDeg: Float = 0f,
    val yawDeg: Float = 0f,
    val pitchDeg: Float = 0f,
    val leftEyeBlink: Float = 0f,
    val rightEyeBlink: Float = 0f,
    val jawOpen: Float = 0f,
) {
    val eyeY: Float get() = (leftEyeY + rightEyeY) / 2f
    val headHeight: Float get() = chinY - crownY
    val faceHeight: Float get() = chinY - foreheadY
    val faceHalfWidth: Float get() = (faceRightX - faceLeftX) / 2f

    fun withCrown(y: Float, estimated: Boolean) = copy(crownY = y, crownEstimated = estimated)

    companion object {
        /** Roll in degrees from the two eye centres; positive when the right side of the image is lower. */
        fun rollFromEyes(ax: Float, ay: Float, bx: Float, by: Float): Float {
            val (lx, ly, rx, ry) = if (ax <= bx) listOf(ax, ay, bx, by) else listOf(bx, by, ax, ay)
            return Math.toDegrees(kotlin.math.atan2((ry - ly).toDouble(), (rx - lx).toDouble())).toFloat()
        }
    }
}
