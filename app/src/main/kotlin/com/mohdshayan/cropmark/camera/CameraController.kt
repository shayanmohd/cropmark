package com.mohdshayan.cropmark.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Size
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.mohdshayan.cropmark.core.check.LiveFace
import com.mohdshayan.cropmark.ml.FaceAnalyzer
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Preview, a full quality still capture and 640 x 480 analysis frames for the live spec frame.
 * Frames are analysed in memory and dropped; only the photo taken is written, to the cache, and
 * the repository moves it into app storage.
 */
class CameraController(private val context: Context, private val faces: FaceAnalyzer) {
    private var provider: ProcessCameraProvider? = null
    private var capture: ImageCapture? = null
    private var camera: Camera? = null
    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var startMs = 0L

    var front: Boolean = false
        private set

    suspend fun bind(
        owner: LifecycleOwner,
        previewView: PreviewView,
        frontLens: Boolean,
        onFrame: (Int, LiveFace?) -> Unit,
    ): Camera {
        val p = provider ?: awaitProvider().also { provider = it }
        front = frontLens
        val selector4by3 = ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
            .build()
        val preview = Preview.Builder().setResolutionSelector(selector4by3).build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }
        val still = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setResolutionSelector(selector4by3)
            .build()
        val analysis = ImageAnalysis.Builder()
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                    .setResolutionStrategy(ResolutionStrategy(Size(640, 480), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER))
                    .build(),
            )
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
        startMs = System.currentTimeMillis()
        var lastTs = -1L
        analysis.setAnalyzer(analysisExecutor) { proxy ->
            try {
                val raw = proxy.toBitmap()
                val rotation = proxy.imageInfo.rotationDegrees
                val upright = if (rotation == 0) raw else Bitmap.createBitmap(
                    raw, 0, 0, raw.width, raw.height, Matrix().apply { postRotate(rotation.toFloat()) }, true,
                )
                var ts = System.currentTimeMillis() - startMs
                if (ts <= lastTs) ts = lastTs + 1
                lastTs = ts
                val (count, face) = faces.analyzeFrame(upright, ts, mirror = front)
                onFrame(count, face)
                if (upright !== raw) upright.recycle()
                raw.recycle()
            } catch (_: Throwable) {
                onFrame(0, null)
            } finally {
                proxy.close()
            }
        }
        p.unbindAll()
        capture = still
        val selector = if (frontLens) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
        return p.bindToLifecycle(owner, selector, preview, still, analysis).also { camera = it }
    }

    fun hasLens(frontLens: Boolean): Boolean = runCatching {
        val p = provider ?: return true
        p.hasCamera(if (frontLens) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA)
    }.getOrDefault(false)

    suspend fun takePhoto(rotation: Int): File = suspendCancellableCoroutine { cont ->
        val still = capture ?: return@suspendCancellableCoroutine cont.resumeWithException(IllegalStateException("Camera not ready"))
        still.targetRotation = rotation
        val file = File(context.cacheDir, "capture-${System.currentTimeMillis()}.jpg")
        still.takePicture(
            ImageCapture.OutputFileOptions.Builder(file).build(),
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    if (cont.isActive) cont.resume(file)
                }

                override fun onError(exception: ImageCaptureException) {
                    if (cont.isActive) cont.resumeWithException(exception)
                }
            },
        )
    }

    fun unbind() {
        runCatching { provider?.unbindAll() }
        capture = null
        camera = null
    }

    fun shutdown() {
        unbind()
        analysisExecutor.shutdown()
    }

    private suspend fun awaitProvider(): ProcessCameraProvider = suspendCancellableCoroutine { cont ->
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                cont.resume(future.get())
            } catch (e: Exception) {
                cont.resumeWithException(e)
            }
        }, ContextCompat.getMainExecutor(context))
    }
}
