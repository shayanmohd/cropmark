package com.mohdshayan.cropmark.data.repo

import android.content.Context
import android.net.Uri
import com.mohdshayan.cropmark.core.backup.BackupCapture
import com.mohdshayan.cropmark.core.backup.BackupCodec
import com.mohdshayan.cropmark.core.backup.BackupCustomSpec
import com.mohdshayan.cropmark.core.backup.BackupEdit
import com.mohdshayan.cropmark.core.backup.BackupExport
import com.mohdshayan.cropmark.core.backup.BackupManifest
import com.mohdshayan.cropmark.core.backup.ImportPlanner
import com.mohdshayan.cropmark.core.backup.NotABackupException
import com.mohdshayan.cropmark.data.db.AppDatabase
import com.mohdshayan.cropmark.data.db.CustomSpec
import com.mohdshayan.cropmark.data.db.ExportRecord
import com.mohdshayan.cropmark.data.db.PhotoCapture
import com.mohdshayan.cropmark.data.db.PhotoEdit
import com.mohdshayan.cropmark.data.prefs.AppPrefs
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class RestoreSummary(val photos: Int, val skipped: Int, val sizes: Int)

/** One zip holds manifest.json, captures/<sha256>.jpg and mattes/<sha256>.png. */
class BackupRepository(
    private val context: Context,
    private val db: AppDatabase,
    private val prefs: AppPrefs,
    private val photos: PhotoRepository,
) {
    fun suggestedName(): String = "cropmark-backup-${SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())}.zip"

    suspend fun write(uri: Uri): Int = withContext(Dispatchers.IO) {
        val photoDao = db.photoDao()
        val captures = photoDao.all()
        val edits = photoDao.allEdits().associateBy { it.captureId }
        val exports = db.exportDao().all().groupBy { it.captureId }
        val manifest = BackupManifest(
            format = BackupCodec.FORMAT,
            exportedAt = System.currentTimeMillis(),
            captures = captures.map { c ->
                BackupCapture(
                    id = c.id, createdAt = c.createdAt, source = c.source, lensFacing = c.lensFacing,
                    originalSha256 = c.originalSha256, widthPx = c.widthPx, heightPx = c.heightPx,
                    faceJson = c.faceJson, hasMatte = c.matteFile != null, updatedAt = c.updatedAt,
                    edit = edits[c.id]?.let {
                        BackupEdit(it.specId, it.backgroundMode, it.backgroundArgb, it.featherLevel, it.exposureEv, it.nudgeScale, it.nudgeXmm, it.nudgeYmm)
                    },
                    exports = exports[c.id].orEmpty().map {
                        BackupExport(it.specId, it.kind, it.paper, it.copies, it.widthPx, it.heightPx, it.bytes, it.fileName, it.checksPassed, it.checksTotal, it.createdAt)
                    },
                )
            },
            customSpecs = db.customSpecDao().all().map {
                BackupCustomSpec(it.id, it.name, it.widthMm, it.heightMm, it.widthPx, it.heightPx, it.dpi, it.headMinPct, it.headMaxPct, it.eyeMinPct, it.eyeMaxPct, it.minKb, it.maxKb, it.backgroundArgb, it.createdAt)
            },
            settings = prefs.exportable(),
        )
        val stream = context.contentResolver.openOutputStream(uri, "wt") ?: throw java.io.IOException("No output stream")
        ZipOutputStream(stream.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(BackupCodec.MANIFEST))
            zip.write(BackupCodec.encode(manifest).toByteArray())
            zip.closeEntry()
            for (c in captures) {
                photos.fileFor(c.originalFile).takeIf { it.exists() }?.let { f ->
                    zip.putNextEntry(ZipEntry(BackupCodec.captureEntry(c.originalSha256)))
                    f.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
                c.matteFile?.let { photos.fileFor(it) }?.takeIf { it.exists() }?.let { f ->
                    zip.putNextEntry(ZipEntry(BackupCodec.matteEntry(c.originalSha256)))
                    f.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
        captures.size
    }

    suspend fun restore(uri: Uri): RestoreSummary = withContext(Dispatchers.IO) {
        val staging = File(context.cacheDir, "restore").apply { deleteRecursively(); mkdirs() }
        var manifestText: String? = null
        try {
            val input = context.contentResolver.openInputStream(uri) ?: throw NotABackupException()
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = entry.name
                    when {
                        entry.isDirectory -> Unit
                        name == BackupCodec.MANIFEST -> manifestText = zip.readBytes().toString(Charsets.UTF_8)
                        SAFE_ENTRY.matches(name) -> File(staging, name).apply { parentFile?.mkdirs() }.outputStream().use { zip.copyTo(it) }
                    }
                }
            }
        } catch (e: NotABackupException) {
            throw e
        } catch (e: Exception) {
            throw NotABackupException()
        }
        val manifest = BackupCodec.decode(manifestText ?: throw NotABackupException())
        val photoDao = db.photoDao()
        val customDao = db.customSpecDao()
        val plan = ImportPlanner.plan(manifest, photoDao.allShas().toSet(), customDao.all().map { it.name }.toSet())
        var imported = 0
        db.withTransaction {
            val idMap = HashMap<Long, Long>()
            for (s in plan.customSpecs) {
                val src = s.source
                idMap[src.id] = customDao.insert(
                    CustomSpec(
                        name = s.name, widthMm = src.widthMm, heightMm = src.heightMm, widthPx = src.widthPx, heightPx = src.heightPx,
                        dpi = src.dpi, headMinPct = src.headMinPct, headMaxPct = src.headMaxPct, eyeMinPct = src.eyeMinPct,
                        eyeMaxPct = src.eyeMaxPct, minKb = src.minKb, maxKb = src.maxKb, backgroundArgb = src.backgroundArgb, createdAt = src.createdAt,
                    ),
                )
            }
            for (c in plan.captures) {
                val staged = File(staging, BackupCodec.captureEntry(c.originalSha256))
                if (!staged.exists()) continue
                val original = "captures/${c.originalSha256}.jpg"
                staged.copyTo(photos.fileFor(original), overwrite = true)
                val stagedMatte = File(staging, BackupCodec.matteEntry(c.originalSha256))
                val matte = if (c.hasMatte && stagedMatte.exists()) {
                    "mattes/${c.originalSha256}.png".also { stagedMatte.copyTo(photos.fileFor(it), overwrite = true) }
                } else null
                val newId = photoDao.insert(
                    PhotoCapture(
                        createdAt = c.createdAt, source = c.source, lensFacing = c.lensFacing, originalFile = original,
                        originalSha256 = c.originalSha256, widthPx = c.widthPx, heightPx = c.heightPx,
                        faceJson = if (matte != null) c.faceJson else null, matteFile = matte, updatedAt = c.updatedAt,
                    ),
                )
                c.edit?.let { e ->
                    photoDao.upsertEdit(
                        PhotoEdit(newId, ImportPlanner.remapSpecId(e.specId, idMap), e.backgroundMode, e.backgroundArgb, e.featherLevel, e.exposureEv, e.nudgeScale, e.nudgeXmm, e.nudgeYmm),
                    )
                }
                for (x in c.exports) {
                    db.exportDao().insert(
                        ExportRecord(
                            captureId = newId, specId = ImportPlanner.remapSpecId(x.specId, idMap), kind = x.kind, paper = x.paper,
                            copies = x.copies, widthPx = x.widthPx, heightPx = x.heightPx, bytes = x.bytes, fileName = x.fileName,
                            savedUri = null, checksPassed = x.checksPassed, checksTotal = x.checksTotal, createdAt = x.createdAt,
                        ),
                    )
                }
                imported++
            }
        }
        prefs.restore(manifest.settings)
        staging.deleteRecursively()
        RestoreSummary(imported, plan.skippedDuplicates, plan.customSpecs.size)
    }

    private companion object {
        val SAFE_ENTRY = Regex("""(captures/[0-9a-f]{64}\.jpg|mattes/[0-9a-f]{64}\.png)""")
    }
}
