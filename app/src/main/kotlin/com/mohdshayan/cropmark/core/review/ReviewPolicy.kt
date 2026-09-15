package com.mohdshayan.cropmark.core.review

/** Counts saves on separate days and allows the Play review prompt once, after the third such day. */
object ReviewPolicy {
    const val DAYS_NEEDED = 3

    data class State(val saveDays: Int, val lastSaveDay: Long, val prompted: Boolean)

    fun recordSave(state: State, epochDay: Long): State =
        if (epochDay == state.lastSaveDay) state else state.copy(saveDays = state.saveDays + 1, lastSaveDay = epochDay)

    /** The calendar day in the user's own time zone, so two saves on one local evening count once. */
    fun localDay(epochMillis: Long, zone: java.time.ZoneId): Long =
        java.time.Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate().toEpochDay()

    fun shouldPrompt(state: State): Boolean = !state.prompted && state.saveDays >= DAYS_NEEDED
}
