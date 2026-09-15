package com.mohdshayan.cropmark.data.repo

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.mohdshayan.cropmark.core.jpeg.JfifDensity
import com.mohdshayan.cropmark.core.jpeg.JpegEncoder
import com.mohdshayan.cropmark.core.jpeg.SizeOutcome
import com.mohdshayan.cropmark.core.jpeg.SizeSearch
import com.mohdshayan.cropmark.core.sheet.Paper
import com.mohdshayan.cropmark.core.sheet.SheetLayout
import com.mohdshayan.cropmark.core.sheet.SheetPlan
import com.mohdshayan.cropmark.core.spec.DocSpec
import com.mohdshayan.cropmark.core.spec.SpecMath
import com.mohdshayan.cropmark.data.db.ExportDao
import com.mohdshayan.cropmark.data.db.ExportRecord
import com.mohdshayan.cropmark.data.db.PhotoEdit
import com.mohdshayan.cropmark.render.PhotoRenderer
import com.mohdshayan.cropmark.render.SheetRenderer
import com.mohdshayan.cropmark.render.Solved
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** A file ready to save or share. */
class ExportFile(
    val bytes: ByteArray,
    val fileName: String,
    val mime: String,
    val widthPx: Int,
    val heightPx: Int,
    val kind: String,
    val paper: String?,
    val copies: Int?,
    val quality: Int?,
)

sealed class FormFileResult {
    data class Ready(val file: ExportFile) : FormFileResult()
    data class TooLarge(val widthPx: Int, val maxKb: Int) : FormFileResult()
    data class TooSmall(val widthPx: Int, val minKb: Int) : FormFileResult()
}

class ExportRepository(private val context: Context, private val dao: ExportDao) {

    fun observeFor(captureId: Long) = dao.observeFor(captureId)

    suspend fun formFile(session: PhotoSession, spec: DocSpec, edit: PhotoEdit, solved: Solved, requestedWidth: Int?): FormFileResult =
        withContext(Dispatchers.Default) {
            val digital = spec.digital
            val (w0, _) = SpecMath.formPixels(spec, requestedWidth)
            val minW = digital?.minPx ?: w0
            val maxW = digital?.maxPx ?: w0
            val heightFor: (Int) -> Int = { w -> digital?.heightFor(w) ?: SpecMath.formPixels(spec).second }
            val rendered = HashMap<Int, Bitmap>()
            val encoder = JpegEncoder { w, h, q ->
                val bmp = rendered.getOrPut(w) { PhotoRenderer.render(session, solved.crop, edit, w, h) }
                val out = ByteArrayOutputStream()
                bmp.compress(Bitmap.CompressFormat.JPEG, q, out)
                JfifDensity.withDensity(out.toByteArray(), spec.dpi)
            }
            val outcome = SizeSearch.search(
                w0, minW, maxW, heightFor,
                SizeSearch.minBytes(digital?.minKb), SizeSearch.maxBytes(digital?.maxKb), encoder,
                maxQuality = if (digital?.maxCompression != null) 100 else SizeSearch.MAX_QUALITY,
                maxCompression = digital?.maxCompression,
            )
            rendered.values.forEach { it.recycle() }
            when (outcome) {
                is SizeOutcome.Fits -> FormFileResult.Ready(
                    ExportFile(
                        outcome.bytes, fileName(spec, "photo", "jpg"), "image/jpeg",
                        outcome.widthPx, outcome.heightPx, KIND_FORM, null, null, outcome.quality,
                    ),
                )
                is SizeOutcome.TooLarge -> FormFileResult.TooLarge(outcome.widthPx, digital?.maxKb ?: 0)
                is SizeOutcome.TooSmall -> FormFileResult.TooSmall(outcome.widthPx, digital?.minKb ?: 0)
            }
        }

    fun sheetPlan(spec: DocSpec, paper: Paper, copies: Int?): SheetPlan =
        SheetLayout.plan(paper, spec.widthMm, spec.heightMm, copies)

    suspend fun sheetFile(session: PhotoSession, spec: DocSpec, edit: PhotoEdit, solved: Solved, plan: SheetPlan, cutLines: Boolean): ExportFile =
        withContext(Dispatchers.Default) {
            val (pw, ph) = PhotoRenderer.printPixels(spec)
            val photo = PhotoRenderer.render(session, solved.crop, edit, pw, ph)
            val pdf = plan.paper.isPdf
            val bytes = if (pdf) SheetRenderer.renderPdf(plan, photo, cutLines) else SheetRenderer.renderJpeg(plan, photo, cutLines)
            photo.recycle()
            val pxPerMm = 300f / 25.4f
            ExportFile(
                bytes,
                fileName(spec, "sheet-${plan.paper.key}", if (pdf) "pdf" else "jpg"),
                if (pdf) "application/pdf" else "image/jpeg",
                Math.round(plan.pageWidthMm * pxPerMm), Math.round(plan.pageHeightMm * pxPerMm),
                if (pdf) KIND_PDF else KIND_SHEET, plan.paper.key, plan.cells.size, null,
            )
        }

    /** Android 10 and later: straight into Pictures/Cropmark (JPEG) or Documents/Cropmark (PDF). */
    suspend fun saveToMediaStore(file: ExportFile): Uri = withContext(Dispatchers.IO) {
        check(Build.VERSION.SDK_INT >= 29)
        val resolver = context.contentResolver
        val isPdf = file.mime == "application/pdf"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, file.fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, file.mime)
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                (if (isPdf) Environment.DIRECTORY_DOCUMENTS else Environment.DIRECTORY_PICTURES) + "/Cropmark",
            )
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val collection = if (isPdf) MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        else MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values) ?: throw java.io.IOException("Could not create the file")
        try {
            resolver.openOutputStream(uri)?.use { it.write(file.bytes) } ?: throw java.io.IOException("No output stream")
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        uri
    }

    /** Android 8 and 9: into a location the user picked with the system document picker. */
    suspend fun writeTo(uri: Uri, file: ExportFile) = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(uri)?.use { it.write(file.bytes) } ?: throw java.io.IOException("No output stream")
    }

    suspend fun shareIntent(file: ExportFile): Intent = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val f = File(dir, file.fileName).apply { writeBytes(file.bytes) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", f)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = file.mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        Intent.createChooser(send, null).apply {
            // Cropmark accepts shared photos itself; it should not be offered its own export.
            putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(android.content.ComponentName(context, com.mohdshayan.cropmark.MainActivity::class.java)))
        }
    }

    suspend fun record(captureId: Long, spec: DocSpec, file: ExportFile, savedUri: Uri?, solved: Solved) {
        dao.insert(
            ExportRecord(
                captureId = captureId, specId = spec.id, kind = file.kind, paper = file.paper, copies = file.copies,
                widthPx = file.widthPx, heightPx = file.heightPx, bytes = file.bytes.size.toLong(),
                fileName = file.fileName, savedUri = savedUri?.toString(),
                checksPassed = solved.passed, checksTotal = solved.total, createdAt = System.currentTimeMillis(),
            ),
        )
    }

    private fun fileName(spec: DocSpec, what: String, ext: String): String {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date())
        return "cropmark-${spec.id}-$what-$stamp.$ext"
    }

    companion object {
        const val KIND_FORM = "form_file"
        const val KIND_SHEET = "sheet_jpeg"
        const val KIND_PDF = "sheet_pdf"
    }
}
