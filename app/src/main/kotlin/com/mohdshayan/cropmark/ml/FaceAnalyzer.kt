package com.mohdshayan.cropmark.ml

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import com.mohdshayan.cropmark.core.check.LiveFace
import com.mohdshayan.cropmark.core.crop.CrownFinder
import com.mohdshayan.cropmark.core.crop.FaceGeometry
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * MediaPipe Face Landmarker over the bundled face_landmarker.task. One instance for stills (IMAGE
 * mode) and one for camera frames (VIDEO mode); both are created on first use and closed when the
 * system asks for memory back.
 */
class FaceAnalyzer(private val context: Context) {

    private var still: FaceLandmarker? = null
    private var video: FaceLandmarker? = null

    private fun build(mode: RunningMode): FaceLandmarker {
        val options = FaceLandmarker.FaceLandmarkerOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath(MODEL).build())
            .setRunningMode(mode)
            .setNumFaces(2)
            .setMinFaceDetectionConfidence(0.5f)
            .setMinFacePresenceConfidence(0.5f)
            .setOutputFaceBlendshapes(mode == RunningMode.IMAGE)
            .setOutputFacialTransformationMatrixes(true)
            .build()
        return FaceLandmarker.createFromOptions(context, options)
    }

    @Synchronized
    fun analyzeStill(bitmap: Bitmap): FaceGeometry {
        val lm = still ?: build(RunningMode.IMAGE).also { still = it }
        val result = lm.detect(BitmapImageBuilder(bitmap).build())
        return toGeometry(result, bitmap.width, bitmap.height)
    }

    /** A camera frame, already upright. Coordinates come back normalised to the frame. */
    @Synchronized
    fun analyzeFrame(bitmap: Bitmap, timestampMs: Long, mirror: Boolean): Pair<Int, LiveFace?> {
        val lm = video ?: build(RunningMode.VIDEO).also { video = it }
        val result = lm.detectForVideo(BitmapImageBuilder(bitmap).build(), timestampMs)
        val faces = result.faceLandmarks()
        if (faces.size != 1) return faces.size to null
        val g = toGeometry(result, 1, 1)
        fun x(v: Float) = if (mirror) 1f - v else v
        return 1 to LiveFace(
            crownY = g.crownY,
            chinY = g.chinY,
            eyeY = g.eyeY,
            midX = x(g.midlineX),
            rollDeg = g.rollDeg,
            yawDeg = g.yawDeg,
            pitchDeg = g.pitchDeg,
        )
    }

    @Synchronized
    fun close() {
        still?.close(); still = null
        video?.close(); video = null
    }

    private fun toGeometry(result: FaceLandmarkerResult, w: Int, h: Int): FaceGeometry {
        val faces = result.faceLandmarks()
        if (faces.size != 1) return FaceGeometry(faceCount = faces.size, imageWidth = w, imageHeight = h)
        val p = faces[0]
        fun px(l: NormalizedLandmark) = l.x() * w
        fun py(l: NormalizedLandmark) = l.y() * h
        // Iris centres when the model provides them (478 points), eye corner midpoints otherwise.
        val (lx, ly) = if (p.size > 473) px(p[468]) to py(p[468]) else ((px(p[33]) + px(p[133])) / 2 to (py(p[33]) + py(p[133])) / 2)
        val (rx, ry) = if (p.size > 473) px(p[473]) to py(p[473]) else ((px(p[362]) + px(p[263])) / 2 to (py(p[362]) + py(p[263])) / 2)
        val chin = p[152]
        val forehead = p[10]
        val mid = (px(p[168]) + px(p[1]) + px(chin) + px(forehead)) / 4f
        var yaw = 0f
        var pitch = 0f
        result.facialTransformationMatrixes().orElse(null)?.firstOrNull()?.let { m ->
            // Column-major 4x4; the third column is the face's forward axis in camera space.
            val fx = m[8]
            val fy = m[9]
            val fz = abs(m[10])
            yaw = Math.toDegrees(atan2(fx.toDouble(), fz.toDouble())).toFloat()
            pitch = Math.toDegrees(atan2(fy.toDouble(), sqrt((fx * fx + fz * fz).toDouble()))).toFloat()
        }
        var blinkL = 0f
        var blinkR = 0f
        var jaw = 0f
        result.faceBlendshapes().orElse(null)?.firstOrNull()?.forEach { c ->
            when (c.categoryName()) {
                "eyeBlinkLeft" -> blinkL = c.score()
                "eyeBlinkRight" -> blinkR = c.score()
                "jawOpen" -> jaw = c.score()
            }
        }
        val fy = py(forehead)
        val cy = py(chin)
        return FaceGeometry(
            faceCount = 1,
            imageWidth = w,
            imageHeight = h,
            chinX = px(chin),
            chinY = cy,
            foreheadY = fy,
            crownY = CrownFinder.estimate(fy, cy),
            crownEstimated = true,
            leftEyeX = lx,
            leftEyeY = ly,
            rightEyeX = rx,
            rightEyeY = ry,
            midlineX = mid,
            faceLeftX = px(p[234]),
            faceRightX = px(p[454]),
            rollDeg = FaceGeometry.rollFromEyes(lx, ly, rx, ry),
            yawDeg = yaw,
            pitchDeg = pitch,
            leftEyeBlink = blinkL,
            rightEyeBlink = blinkR,
            jawOpen = jaw,
        )
    }

    companion object {
        const val MODEL = "models/face_landmarker.task"
    }
}
