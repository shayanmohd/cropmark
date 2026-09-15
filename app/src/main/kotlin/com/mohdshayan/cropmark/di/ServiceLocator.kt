package com.mohdshayan.cropmark.di

import android.content.Context
import android.net.Uri
import com.mohdshayan.cropmark.camera.ShutterBus
import com.mohdshayan.cropmark.data.db.AppDatabase
import com.mohdshayan.cropmark.data.prefs.AppPrefs
import com.mohdshayan.cropmark.data.repo.BackupRepository
import com.mohdshayan.cropmark.data.repo.ExportRepository
import com.mohdshayan.cropmark.data.repo.PhotoRepository
import com.mohdshayan.cropmark.data.repo.SpecRepository
import com.mohdshayan.cropmark.ml.FaceAnalyzer
import com.mohdshayan.cropmark.ml.PersonSegmenter
import kotlinx.coroutines.flow.MutableStateFlow

/** Manual dependency container, initialised in App.onCreate. */
object ServiceLocator {

    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        if (appContext == null) {
            synchronized(this) {
                if (appContext == null) appContext = context.applicationContext
            }
        }
    }

    private fun ctx(): Context = appContext ?: error("ServiceLocator.init() must be called before use")

    val appPrefs: AppPrefs by lazy { AppPrefs(ctx()) }
    val database: AppDatabase by lazy { AppDatabase.get(ctx()) }
    val faceAnalyzer: FaceAnalyzer by lazy { FaceAnalyzer(ctx()) }
    val segmenter: PersonSegmenter by lazy { PersonSegmenter(ctx()) }
    val specs: SpecRepository by lazy { SpecRepository(ctx(), database.customSpecDao()) }
    val photos: PhotoRepository by lazy { PhotoRepository(ctx(), database.photoDao(), faceAnalyzer, segmenter) }
    val exports: ExportRepository by lazy { ExportRepository(ctx(), database.exportDao()) }
    val backups: BackupRepository by lazy { BackupRepository(ctx(), database, appPrefs, photos) }
    val shutter = ShutterBus()

    /** A photo shared into the app, waiting for the user to pick what it is for. */
    val sharedPhoto = MutableStateFlow<Uri?>(null)
}
