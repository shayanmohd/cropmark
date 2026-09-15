package com.mohdshayan.cropmark.ml

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.ByteBufferExtractor
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.imagesegmenter.ImageSegmenter
import java.nio.ByteOrder

/** A low resolution person confidence mask from the bundled selfie_segmenter.tflite. */
class PersonSegmenter(private val context: Context) {

    data class Mask(val values: FloatArray, val width: Int, val height: Int)

    private var segmenter: ImageSegmenter? = null

    @Synchronized
    fun segment(bitmap: Bitmap): Mask {
        val seg = segmenter ?: ImageSegmenter.createFromOptions(
            context,
            ImageSegmenter.ImageSegmenterOptions.builder()
                .setBaseOptions(BaseOptions.builder().setModelAssetPath(MODEL).build())
                .setRunningMode(RunningMode.IMAGE)
                .setOutputConfidenceMasks(true)
                .setOutputCategoryMask(false)
                .build(),
        ).also { segmenter = it }
        val input = if (maxOf(bitmap.width, bitmap.height) > INPUT_MAX) {
            val s = INPUT_MAX.toFloat() / maxOf(bitmap.width, bitmap.height)
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * s).toInt(), (bitmap.height * s).toInt(), true)
        } else bitmap
        val result = seg.segment(BitmapImageBuilder(input).build())
        val image = result.confidenceMasks().get()[0]
        val buffer = ByteBufferExtractor.extract(image).order(ByteOrder.nativeOrder()).asFloatBuffer()
        val values = FloatArray(image.width * image.height)
        buffer.get(values)
        result.confidenceMasks().get().forEach { it.close() }
        if (input !== bitmap) input.recycle()
        return Mask(values, image.width, image.height)
    }

    @Synchronized
    fun close() {
        segmenter?.close()
        segmenter = null
    }

    companion object {
        const val MODEL = "models/selfie_segmenter.tflite"
        const val INPUT_MAX = 512
    }
}
