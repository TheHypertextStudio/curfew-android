package studio.hypertext.curfew.persistence

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import studio.hypertext.curfew.alarm.SavedSunriseAlarm
import studio.hypertext.curfew.alarm.SunriseOccurrence
import studio.hypertext.curfew.alarm.SunriseSchedule
import studio.hypertext.curfew.alarm.SunriseScheduleStore

class SunriseAlarmPreferences(private val dataStore: DataStore<Preferences>) : SunriseScheduleStore {
    val state: Flow<SavedSunriseAlarm?> = dataStore.data.map(::decode)

    override suspend fun read(): SavedSunriseAlarm? = state.first()

    override suspend fun write(alarm: SavedSunriseAlarm) {
        dataStore.edit { preferences ->
            // This file belongs only to Sunrise; one transaction replaces the complete journal.
            preferences.clear()
            preferences[SCHEDULE_ID] = alarm.schedule.id
            preferences[WAKE_TIME] = alarm.schedule.wakeTime.toString()
            preferences[ENABLED] = alarm.schedule.enabled
            preferences[CALLBACK_ID] = alarm.schedule.callbackId
            preferences[WEEKDAYS] = alarm.schedule.weekdays.map { it.name }.toSet()
            alarm.schedule.onceDate?.let { preferences[ONCE_DATE] = it.toString() }
            alarm.occurrence?.let {
                preferences[CAMPAIGN_ID] = it.campaignId
                preferences[OCCURRENCE_DATE] = it.localDate.toString()
                preferences[STARTS_AT] = it.startsAt.toString()
            }
            alarm.completedThrough?.let { preferences[COMPLETED_THROUGH] = it.toString() }
        }
    }

    private fun decode(preferences: Preferences): SavedSunriseAlarm? {
        val id = preferences[SCHEDULE_ID] ?: return null
        val schedule = SunriseSchedule(
            id = id,
            wakeTime = LocalTime.parse(requireNotNull(preferences[WAKE_TIME])),
            onceDate = preferences[ONCE_DATE]?.let(LocalDate::parse),
            weekdays = requireNotNull(preferences[WEEKDAYS]).map(DayOfWeek::valueOf).toSet(),
            enabled = requireNotNull(preferences[ENABLED]),
            callbackId = requireNotNull(preferences[CALLBACK_ID]),
        )
        val occurrence = preferences[CAMPAIGN_ID]?.let {
            SunriseOccurrence(
                campaignId = it,
                localDate = LocalDate.parse(requireNotNull(preferences[OCCURRENCE_DATE])),
                startsAt = Instant.parse(requireNotNull(preferences[STARTS_AT])),
            )
        }
        return SavedSunriseAlarm(
            schedule, occurrence, preferences[COMPLETED_THROUGH]?.let(LocalDate::parse),
        )
    }

    private companion object {
        val SCHEDULE_ID = stringPreferencesKey("schedule_id")
        val WAKE_TIME = stringPreferencesKey("wake_time")
        val ONCE_DATE = stringPreferencesKey("once_date")
        val WEEKDAYS = stringSetPreferencesKey("weekdays")
        val ENABLED = booleanPreferencesKey("enabled")
        val CALLBACK_ID = stringPreferencesKey("callback_id")
        val CAMPAIGN_ID = stringPreferencesKey("campaign_id")
        val OCCURRENCE_DATE = stringPreferencesKey("occurrence_date")
        val STARTS_AT = stringPreferencesKey("starts_at")
        val COMPLETED_THROUGH = stringPreferencesKey("completed_through")
    }
}
