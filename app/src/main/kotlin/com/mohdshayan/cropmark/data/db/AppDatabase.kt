package com.mohdshayan.cropmark.data.db

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "photo_capture", indices = [Index("originalSha256")])
data class PhotoCapture(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: Long,
    /** camera, gallery or share */
    val source: String,
    val lensFacing: String?,
    /** Relative to filesDir, for example captures/<sha256>.jpg */
    val originalFile: String,
    val originalSha256: String,
    val widthPx: Int,
    val heightPx: Int,
    val faceJson: String?,
    val matteFile: String?,
    val updatedAt: Long,
)

@Entity(
    tableName = "photo_edit",
    foreignKeys = [ForeignKey(PhotoCapture::class, ["id"], ["captureId"], onDelete = ForeignKey.CASCADE)],
)
data class PhotoEdit(
    @PrimaryKey val captureId: Long,
    val specId: String,
    /** keep, white, light_blue, light_grey or custom */
    val backgroundMode: String,
    val backgroundArgb: Int,
    val featherLevel: Int,
    val exposureEv: Float,
    val nudgeScale: Float,
    val nudgeXmm: Float,
    val nudgeYmm: Float,
)

@Entity(
    tableName = "export_record",
    foreignKeys = [ForeignKey(PhotoCapture::class, ["id"], ["captureId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("captureId")],
)
data class ExportRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val captureId: Long,
    val specId: String,
    /** form_file, sheet_jpeg or sheet_pdf */
    val kind: String,
    val paper: String?,
    val copies: Int?,
    val widthPx: Int,
    val heightPx: Int,
    val bytes: Long,
    val fileName: String,
    val savedUri: String?,
    val checksPassed: Int,
    val checksTotal: Int,
    val createdAt: Long,
)

@Entity(tableName = "custom_spec")
data class CustomSpec(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val widthMm: Float?,
    val heightMm: Float?,
    val widthPx: Int?,
    val heightPx: Int?,
    val dpi: Int,
    val headMinPct: Float,
    val headMaxPct: Float,
    val eyeMinPct: Float?,
    val eyeMaxPct: Float?,
    val minKb: Int?,
    val maxKb: Int?,
    val backgroundArgb: Int,
    val createdAt: Long,
)

/** A capture with its edit, for the history grid. */
data class CaptureWithEdit(
    @ColumnInfo(name = "id") val id: Long,
    val createdAt: Long,
    val originalFile: String,
    val specId: String?,
    val exportCount: Int,
)

@Dao
interface PhotoDao {
    @Insert
    suspend fun insert(capture: PhotoCapture): Long

    @Query("SELECT * FROM photo_capture WHERE id = :id")
    suspend fun get(id: Long): PhotoCapture?

    @Query("SELECT * FROM photo_capture WHERE originalSha256 = :sha LIMIT 1")
    suspend fun findBySha(sha: String): PhotoCapture?

    @Query("SELECT originalSha256 FROM photo_capture")
    suspend fun allShas(): List<String>

    @Query("SELECT * FROM photo_capture ORDER BY createdAt DESC")
    suspend fun all(): List<PhotoCapture>

    @Query(
        "SELECT c.id AS id, c.createdAt AS createdAt, c.originalFile AS originalFile, e.specId AS specId, " +
            "(SELECT COUNT(*) FROM export_record x WHERE x.captureId = c.id) AS exportCount " +
            "FROM photo_capture c LEFT JOIN photo_edit e ON e.captureId = c.id ORDER BY c.createdAt DESC",
    )
    fun observeHistory(): Flow<List<CaptureWithEdit>>

    @Query("UPDATE photo_capture SET faceJson = :faceJson, matteFile = :matteFile, updatedAt = :now WHERE id = :id")
    suspend fun updateAnalysis(id: Long, faceJson: String?, matteFile: String?, now: Long)

    @Query("DELETE FROM photo_capture WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM photo_capture")
    suspend fun deleteAll()

    @Query("SELECT * FROM photo_edit WHERE captureId = :captureId")
    suspend fun edit(captureId: Long): PhotoEdit?

    @Upsert
    suspend fun upsertEdit(edit: PhotoEdit)

    @Query("SELECT * FROM photo_edit")
    suspend fun allEdits(): List<PhotoEdit>
}

@Dao
interface ExportDao {
    @Insert
    suspend fun insert(record: ExportRecord): Long

    @Query("SELECT * FROM export_record WHERE captureId = :captureId ORDER BY createdAt DESC")
    fun observeFor(captureId: Long): Flow<List<ExportRecord>>

    @Query("SELECT * FROM export_record")
    suspend fun all(): List<ExportRecord>
}

@Dao
interface CustomSpecDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(spec: CustomSpec): Long

    @Query("SELECT * FROM custom_spec ORDER BY createdAt")
    fun observeAll(): Flow<List<CustomSpec>>

    @Query("SELECT * FROM custom_spec ORDER BY createdAt")
    suspend fun all(): List<CustomSpec>

    @Query("DELETE FROM custom_spec WHERE id = :id")
    suspend fun delete(id: Long)
}

/** Version 1 until the app has shipped; after that turn on exportSchema and write a Migration. */
@Database(
    entities = [PhotoCapture::class, PhotoEdit::class, ExportRecord::class, CustomSpec::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun photoDao(): PhotoDao
    abstract fun exportDao(): ExportDao
    abstract fun customSpecDao(): CustomSpecDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "cropmark.db",
                ).build().also { instance = it }
            }
    }
}
