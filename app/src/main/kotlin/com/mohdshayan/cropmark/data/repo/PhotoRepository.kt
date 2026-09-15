package com.mohdshayan.cropmark.data.repo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import com.mohdshayan.cropmark.core.crop.CrownFinder
import com.mohdshayan.cropmark.core.crop.FaceGeometry
import com.mohdshayan.cropmark.core.matte.Decontaminate
import com.mohdshayan.cropmark.core.matte.GuidedFilter
import com.mohdshayan.cropmark.data.db.PhotoCapture
import com.mohdshayan.cropmark.data.db.PhotoDao
import com.mohdshayan.cropmark.data.db.PhotoEdit
import com.mohdshayan.cropmark.ml.FaceAnalyzer
import com.mohdshayan.cropmark.ml.PersonSegmenter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest

/**
 * Everything the pipeline needs in memory for one capture: the upright working copy, the refined
 * person matte at matte resolution, the background colour estimate for edge clean-up and the face.
 */
class PhotoSession(
    val captureId: Long,
    val argb: IntArray,
    val width: Int,
    val height: Int,
    val matte: FloatArray?,
    val matteWidth: Int,
    val matteHeight: Int,
    val bgEstimate: IntArray?,
    val face: FaceGeometry,
    val thumbnail: Bitmap,
)

class UnreadablePhotoException : Exception("This file could not be opened as a photo.")

/** Owns filesDir/captures and filesDir/mattes and the capture rows. */
class PhotoRepository(
    private val context: Context,
    private val dao: PhotoDao,
    private val faces: FaceAnalyzer,
    private val segmenter: PersonSegmenter,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val capturesDir get() = File(context.filesDir, "captures").apply { mkdirs() }
    private val mattesDir get() = File(context.filesDir, "mattes").apply { mkdirs() }

    @Volatile
    private var cached: PhotoSession? = null

    suspend fun importUri(uri: Uri, source: String): Long = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { readCapped(it) } ?: throw UnreadablePhotoException()
        importBytes(bytes, source, null)
    }

    suspend fun importFile(file: File, source: String, lensFacing: String?): Long = withContext(Dispatchers.IO) {
        val bytes = file.readBytes()
        importBytes(bytes, source, lensFacing).also { file.delete() }
    }

    /** Decodes upright, scales to the 2048 px working copy and stores it. A photo already on file is reused. */
    private suspend fun importBytes(bytes: ByteArray, source: String, lensFacing: String?): Long {
        val sha = sha256(bytes)
        dao.findBySha(sha)?.let { return it.id }
        val bitmap = decodeUpright(bytes) ?: throw UnreadablePhotoException()
        val name = "captures/$sha.jpg"
        capturesDir
        File(context.filesDir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        val now = System.currentTimeMillis()
        val id = dao.insert(
            PhotoCapture(
                createdAt = now, source = source, lensFacing = lensFacing, originalFile = name,
                originalSha256 = sha, widthPx = bitmap.width, heightPx = bitmap.height,
                faceJson = null, matteFile = null, updatedAt = now,
            ),
        )
        bitmap.recycle()
        return id
    }

    suspend fun get(id: Long): PhotoCapture? = dao.get(id)

    suspend fun edit(id: Long): PhotoEdit? = dao.edit(id)

    suspend fun saveEdit(edit: PhotoEdit) = dao.upsertEdit(edit)

    fun observeHistory() = dao.observeHistory()

    fun fileFor(relative: String) = File(context.filesDir, relative).also { it.parentFile?.mkdirs() }

    /**
     * Loads a capture for editing, running face landmarks and the matte the first time and storing
     * both so later sessions and re-exports skip the models.
     */
    suspend fun openSession(id: Long): PhotoSession = withContext(Dispatchers.Default) {
        cached?.takeIf { it.captureId == id }?.let { return@withContext it }
        val capture = dao.get(id) ?: throw UnreadablePhotoException()
        val file = fileFor(capture.originalFile)
        val bitmap = BitmapFactory.decodeFile(file.path)?.copy(Bitmap.Config.ARGB_8888, false) ?: throw UnreadablePhotoException()
        val w = bitmap.width
        val h = bitmap.height
        val argb = IntArray(w * h).also { bitmap.getPixels(it, 0, w, 0, 0, w, h) }

        var face = capture.faceJson?.let { runCatching { json.decodeFromString(FaceGeometry.serializer(), it) }.getOrNull() }
        if (face == null) {
            val t0 = System.nanoTime()
            face = faces.analyzeStill(bitmap)
            android.util.Log.i("Cropmark", "Face landmarks ${w}x$h in ${(System.nanoTime() - t0) / 1_000_000} ms")
        }

        val scale = minOf(1f, MATTE_MAX.toFloat() / maxOf(w, h))
        val mw = (w * scale).toInt().coerceAtLeast(1)
        val mh = (h * scale).toInt().coerceAtLeast(1)
        val small = if (scale < 1f) Bitmap.createScaledBitmap(bitmap, mw, mh, true) else bitmap
        val smallArgb = IntArray(mw * mh).also { small.getPixels(it, 0, mw, 0, 0, mw, mh) }

        var matte: FloatArray? = null
        var matteFile = capture.matteFile
        if (face.faceCount == 1) {
            matte = matteFile?.let { loadMatte(fileFor(it), mw, mh) }
            if (matte == null) {
                val t0 = System.nanoTime()
                val mask = segmenter.segment(bitmap)
                val t1 = System.nanoTime()
                matte = GuidedFilter.refine(mask.values, mask.width, mask.height, smallArgb, mw, mh)
                android.util.Log.i(
                    "Cropmark",
                    "Segmenter ${(t1 - t0) / 1_000_000} ms, guided filter ${mw}x$mh in ${(System.nanoTime() - t1) / 1_000_000} ms",
                )
                matteFile = "mattes/${capture.originalSha256}.png"
                saveMatte(matte, mw, mh, fileFor(matteFile))
            }
            if (capture.faceJson == null || face.crownEstimated) {
                val crown = CrownFinder.find(
                    matte, mw, mh, face.midlineX * scale, face.foreheadY * scale, face.chinY * scale, face.faceHalfWidth * scale,
                )
                face = face.withCrown(if (crown.estimated) face.crownY else crown.y / scale, crown.estimated)
            }
        }
        if (capture.faceJson == null || capture.matteFile != matteFile) {
            dao.updateAnalysis(id, json.encodeToString(FaceGeometry.serializer(), face), matteFile, System.currentTimeMillis())
        }
        val bgEstimate = matte?.let { Decontaminate.estimateBackground(smallArgb, it, mw, mh, maxOf(4, mw / 48)) }
        val thumbScale = 480f / maxOf(w, h)
        val thumb = Bitmap.createScaledBitmap(bitmap, (w * thumbScale).toInt(), (h * thumbScale).toInt(), true)
        if (small !== bitmap) small.recycle()
        bitmap.recycle()
        PhotoSession(id, argb, w, h, matte, mw, mh, bgEstimate, face, thumb).also { cached = it }
    }

    suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        val c = dao.get(id) ?: return@withContext
        dao.delete(id)
        fileFor(c.originalFile).delete()
        c.matteFile?.let { fileFor(it).delete() }
        if (cached?.captureId == id) cached = null
    }

    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        dao.deleteAll()
        capturesDir.listFiles()?.forEach { it.delete() }
        mattesDir.listFiles()?.forEach { it.delete() }
        cached = null
    }

    fun invalidateCache() {
        cached = null
    }

    fun trimMemory() {
        faces.close()
        segmenter.close()
    }

    private fun decodeUpright(bytes: ByteArray): Bitmap? {
        val decoded: Bitmap? = if (Build.VERSION.SDK_INT >= 28) {
            runCatching {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(java.nio.ByteBuffer.wrap(bytes))) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    val long = maxOf(info.size.width, info.size.height)
                    if (long > WORKING_MAX) {
                        val s = WORKING_MAX.toFloat() / long
                        decoder.setTargetSize((info.size.width * s).toInt(), (info.size.height * s).toInt())
                    }
                }
            }.getOrNull()
        } else {
            decodeLegacy(bytes)
        }
        return decoded?.let { scaleDown(it) }
    }

    private fun decodeLegacy(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= WORKING_MAX) sample *= 2
        val raw = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val orientation = runCatching {
            ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(270f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            else -> return raw
        }
        return Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
    }

    private fun scaleDown(b: Bitmap): Bitmap {
        val long = maxOf(b.width, b.height)
        val out = if (long <= WORKING_MAX) b else {
            val s = WORKING_MAX.toFloat() / long
            Bitmap.createScaledBitmap(b, (b.width * s).toInt(), (b.height * s).toInt(), true)
        }
        return if (out.config == Bitmap.Config.ARGB_8888) out else out.copy(Bitmap.Config.ARGB_8888, false)
    }

    private fun saveMatte(m: FloatArray, w: Int, h: Int, file: File) {
        val px = IntArray(w * h) { val v = (m[it] * 255f + 0.5f).toInt().coerceIn(0, 255); (0xFF shl 24) or (v shl 16) or (v shl 8) or v }
        val bmp = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
        file.parentFile?.mkdirs()
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
    }

    private fun loadMatte(file: File, w: Int, h: Int): FloatArray? {
        if (!file.exists()) return null
        val bmp = BitmapFactory.decodeFile(file.path) ?: return null
        val scaled = if (bmp.width != w || bmp.height != h) Bitmap.createScaledBitmap(bmp, w, h, true) else bmp
        val px = IntArray(w * h).also { scaled.getPixels(it, 0, w, 0, 0, w, h) }
        return FloatArray(w * h) { ((px[it] shr 16) and 0xFF) / 255f }
    }

    /** Reads a picked file, refusing anything past [MAX_IMPORT_BYTES]: no phone photo is that big. */
    private fun readCapped(input: java.io.InputStream): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            total += n
            if (total > MAX_IMPORT_BYTES) throw UnreadablePhotoException()
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    companion object {
        const val MAX_IMPORT_BYTES = 80L * 1024 * 1024
        const val WORKING_MAX = 2048
        const val MATTE_MAX = 1024

        fun sha256(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
