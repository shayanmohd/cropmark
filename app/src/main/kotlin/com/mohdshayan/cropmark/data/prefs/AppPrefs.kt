package com.mohdshayan.cropmark.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_prefs")

enum class ThemeMode(val key: String) { System("system"), Light("light"), Dark("dark") }

enum class LocalCount(val key: String, val label: String) {
    PhotosTaken("count_photos_taken", "Photos taken"),
    FormFiles("count_form_files", "Form files saved"),
    Sheets("count_sheets", "Sheets saved"),
    Backups("count_backups", "Backups written"),
}

data class Settings(
    val volumeShutter: Boolean = true,
    val timerSeconds: Int = 0,
    val counterMode: Boolean = false,
    val theme: ThemeMode = ThemeMode.System,
    val defaultBackground: String = "spec",
    val sheetPaper: String = "4x6",
    val cutLines: Boolean = true,
    val lensFacing: String = "back",
    val localCountsEnabled: Boolean = false,
    val counts: Map<LocalCount, Int> = emptyMap(),
)

/** Settings and small flags. Lists live in Room. */
class AppPrefs(private val context: Context) {

    private object Keys {
        val CAMERA_RATIONALE_SHOWN = booleanPreferencesKey("camera_rationale_shown")
        val FIRST_FRAME_MOMENT_SHOWN = booleanPreferencesKey("first_frame_moment_shown")
        val LAST_SPEC_ID = stringPreferencesKey("last_spec_id")
        val DEFAULT_BACKGROUND = stringPreferencesKey("default_background")
        val LENS_FACING = stringPreferencesKey("lens_facing")
        val VOLUME_SHUTTER = booleanPreferencesKey("volume_shutter")
        val TIMER_SECONDS = intPreferencesKey("timer_seconds")
        val COUNTER_MODE = booleanPreferencesKey("counter_mode")
        val SHEET_PAPER = stringPreferencesKey("sheet_paper")
        val CUT_LINES = booleanPreferencesKey("cut_lines")
        val THEME = stringPreferencesKey("theme")
        val SUCCESSFUL_EXPORTS = intPreferencesKey("successful_exports")
        val LAST_EXPORT_DAY = longPreferencesKey("last_export_day")
        val REVIEW_PROMPTED = booleanPreferencesKey("review_prompted")
        val LOCAL_COUNTS_ENABLED = booleanPreferencesKey("local_counts_enabled")
        fun count(c: LocalCount) = intPreferencesKey(c.key)
    }

    private val data = context.dataStore.data

    val settings: Flow<Settings> = data.map { p ->
        Settings(
            volumeShutter = p[Keys.VOLUME_SHUTTER] ?: true,
            timerSeconds = p[Keys.TIMER_SECONDS] ?: 0,
            counterMode = p[Keys.COUNTER_MODE] ?: false,
            theme = ThemeMode.entries.firstOrNull { it.key == p[Keys.THEME] } ?: ThemeMode.System,
            defaultBackground = p[Keys.DEFAULT_BACKGROUND] ?: "spec",
            sheetPaper = p[Keys.SHEET_PAPER] ?: "4x6",
            cutLines = p[Keys.CUT_LINES] ?: true,
            lensFacing = p[Keys.LENS_FACING] ?: "back",
            localCountsEnabled = p[Keys.LOCAL_COUNTS_ENABLED] ?: false,
            counts = LocalCount.entries.associateWith { p[Keys.count(it)] ?: 0 },
        )
    }

    val cameraRationaleShown: Flow<Boolean> = data.map { it[Keys.CAMERA_RATIONALE_SHOWN] ?: false }
    val firstFrameMomentShown: Flow<Boolean> = data.map { it[Keys.FIRST_FRAME_MOMENT_SHOWN] ?: false }
    val lastSpecId: Flow<String?> = data.map { it[Keys.LAST_SPEC_ID] }

    suspend fun setCameraRationaleShown() = context.dataStore.edit { it[Keys.CAMERA_RATIONALE_SHOWN] = true }
    suspend fun setFirstFrameMomentShown() = context.dataStore.edit { it[Keys.FIRST_FRAME_MOMENT_SHOWN] = true }
    suspend fun setLastSpecId(id: String) = context.dataStore.edit { it[Keys.LAST_SPEC_ID] = id }
    suspend fun setVolumeShutter(v: Boolean) = context.dataStore.edit { it[Keys.VOLUME_SHUTTER] = v }
    suspend fun setTimerSeconds(v: Int) = context.dataStore.edit { it[Keys.TIMER_SECONDS] = v }
    suspend fun setCounterMode(v: Boolean) = context.dataStore.edit { it[Keys.COUNTER_MODE] = v }
    suspend fun setTheme(v: ThemeMode) = context.dataStore.edit { it[Keys.THEME] = v.key }
    suspend fun setDefaultBackground(v: String) = context.dataStore.edit { it[Keys.DEFAULT_BACKGROUND] = v }
    suspend fun setSheetPaper(v: String) = context.dataStore.edit { it[Keys.SHEET_PAPER] = v }
    suspend fun setCutLines(v: Boolean) = context.dataStore.edit { it[Keys.CUT_LINES] = v }
    suspend fun setLensFacing(v: String) = context.dataStore.edit { it[Keys.LENS_FACING] = v }
    suspend fun setLocalCountsEnabled(v: Boolean) = context.dataStore.edit { it[Keys.LOCAL_COUNTS_ENABLED] = v }

    suspend fun increment(count: LocalCount) {
        context.dataStore.edit { p ->
            if (p[Keys.LOCAL_COUNTS_ENABLED] == true) p[Keys.count(count)] = (p[Keys.count(count)] ?: 0) + 1
        }
    }

    suspend fun reviewState(): com.mohdshayan.cropmark.core.review.ReviewPolicy.State {
        val p = data.first()
        return com.mohdshayan.cropmark.core.review.ReviewPolicy.State(
            p[Keys.SUCCESSFUL_EXPORTS] ?: 0, p[Keys.LAST_EXPORT_DAY] ?: -1L, p[Keys.REVIEW_PROMPTED] ?: false,
        )
    }

    suspend fun saveReviewState(s: com.mohdshayan.cropmark.core.review.ReviewPolicy.State) {
        context.dataStore.edit {
            it[Keys.SUCCESSFUL_EXPORTS] = s.saveDays
            it[Keys.LAST_EXPORT_DAY] = s.lastSaveDay
            it[Keys.REVIEW_PROMPTED] = s.prompted
        }
    }

    /** The user-facing settings that travel in a backup. */
    suspend fun exportable(): Map<String, String> {
        val s = settings.first()
        return mapOf(
            "volume_shutter" to s.volumeShutter.toString(),
            "timer_seconds" to s.timerSeconds.toString(),
            "counter_mode" to s.counterMode.toString(),
            "theme" to s.theme.key,
            "default_background" to s.defaultBackground,
            "sheet_paper" to s.sheetPaper,
            "cut_lines" to s.cutLines.toString(),
            "lens_facing" to s.lensFacing,
        )
    }

    suspend fun restore(map: Map<String, String>) {
        context.dataStore.edit { p ->
            map["volume_shutter"]?.toBooleanStrictOrNull()?.let { p[Keys.VOLUME_SHUTTER] = it }
            map["timer_seconds"]?.toIntOrNull()?.takeIf { it in listOf(0, 3, 10) }?.let { p[Keys.TIMER_SECONDS] = it }
            map["counter_mode"]?.toBooleanStrictOrNull()?.let { p[Keys.COUNTER_MODE] = it }
            map["theme"]?.takeIf { v -> ThemeMode.entries.any { it.key == v } }?.let { p[Keys.THEME] = it }
            map["default_background"]?.let { p[Keys.DEFAULT_BACKGROUND] = it }
            map["sheet_paper"]?.let { p[Keys.SHEET_PAPER] = it }
            map["cut_lines"]?.toBooleanStrictOrNull()?.let { p[Keys.CUT_LINES] = it }
            map["lens_facing"]?.takeIf { it == "front" || it == "back" }?.let { p[Keys.LENS_FACING] = it }
        }
    }
}
