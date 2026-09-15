package com.mohdshayan.cropmark

import android.app.Application
import android.content.ComponentCallbacks2
import com.mohdshayan.cropmark.di.ServiceLocator

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
    }

    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            // The models are rebuilt lazily on the next photo.
            ServiceLocator.photos.trimMemory()
        }
    }
}
