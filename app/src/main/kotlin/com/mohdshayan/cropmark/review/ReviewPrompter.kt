package com.mohdshayan.cropmark.review

import android.app.Activity
import com.google.android.play.core.ktx.launchReview
import com.google.android.play.core.ktx.requestReview
import com.google.android.play.core.review.ReviewManagerFactory
import com.mohdshayan.cropmark.core.review.ReviewPolicy
import com.mohdshayan.cropmark.di.ServiceLocator

/** Records a successful save and asks Google Play for its review sheet once, after saves on three separate days. */
object ReviewPrompter {
    suspend fun onSaved(activity: Activity?) {
        val prefs = ServiceLocator.appPrefs
        val day = ReviewPolicy.localDay(System.currentTimeMillis(), java.time.ZoneId.systemDefault())
        val state = ReviewPolicy.recordSave(prefs.reviewState(), day)
        prefs.saveReviewState(state)
        if (activity == null || !ReviewPolicy.shouldPrompt(state)) return
        try {
            val manager = ReviewManagerFactory.create(activity)
            val info = manager.requestReview()
            prefs.saveReviewState(state.copy(prompted = true))
            manager.launchReview(activity, info)
        } catch (_: Exception) {
            // Without the Play Store there is no sheet to show; nothing else changes.
        }
    }
}
