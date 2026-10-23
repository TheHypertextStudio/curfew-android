package studio.hypertext.curfew.persistence

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.File
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import studio.hypertext.curfew.alarm.SavedSunriseAlarm
import studio.hypertext.curfew.alarm.SunriseOccurrence
import studio.hypertext.curfew.alarm.SunriseSchedule

class SunriseAlarmPreferencesTest {
    @get:Rule
    val directory = TemporaryFolder()
    private val schedule = SunriseSchedule(
        "sunrise-1", LocalTime.of(7, 0), null, setOf(DayOfWeek.MONDAY), true, "condition-1",
    )
    private val occurrence = SunriseOccurrence(
        "occurrence-1", LocalDate.of(2026, 10, 19), Instant.parse("2026-10-19T14:00:00Z"),
    )

    @Test
    fun `a fresh installation has no saved alarm`() = runBlocking {
        withStore(File(directory.root, "empty.preferences_pb")) { store ->
            assertNull(store.read())
        }
    }

    @Test
    fun `reopening storage preserves an armed one night alarm without making it recurring`() = runBlocking {
        val file = File(directory.root, "once.preferences_pb")
        val alarm = SavedSunriseAlarm(
            schedule.copy(onceDate = occurrence.localDate, weekdays = emptySet()), occurrence,
        )
        withStore(file) { it.write(alarm) }
        withStore(file) { assertEquals(alarm, it.read()) }
    }

    @Test
    fun `reopening storage preserves the completed date after the next occurrence is selected`() = runBlocking {
        val file = File(directory.root, "completed.preferences_pb")
        val next = requireNotNull(schedule.copy(weekdays = DayOfWeek.entries.toSet()).nextOccurrence(
            occurrence.startsAt, ZoneId.of("America/Los_Angeles"),
        ))
        val alarm = SavedSunriseAlarm(schedule, next, occurrence.localDate)

        withStore(file) { it.write(alarm) }
        withStore(file) { assertEquals(alarm, it.read()) }
    }

    @Test
    fun `disabling future alarms does not discard the active occurrence`() = runBlocking {
        val file = File(directory.root, "disabled.preferences_pb")
        val alarm = SavedSunriseAlarm(schedule.copy(enabled = false), occurrence)

        withStore(file) { it.write(alarm) }
        withStore(file) { assertEquals(alarm, it.read()) }
    }

    @Test
    fun `saving a completed one night clears the previous pending occurrence`() = runBlocking {
        val file = File(directory.root, "cleared.preferences_pb")
        val once = schedule.copy(onceDate = occurrence.localDate, weekdays = emptySet())
        withStore(file) {
            it.write(SavedSunriseAlarm(once, occurrence))
            it.write(SavedSunriseAlarm(once, null, occurrence.localDate))
        }

        withStore(file) {
            assertNull(it.read()?.occurrence)
            assertEquals(occurrence.localDate, it.read()?.completedThrough)
        }
    }

    @Test
    fun `incomplete saved state reports an error instead of pretending no alarm was saved`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val data = PreferenceDataStoreFactory.create(scope = scope) {
                File(directory.root, "incomplete.preferences_pb")
            }
            data.edit { it[stringPreferencesKey("schedule_id")] = "sunrise-1" }

            assertNotNull(runCatching { SunriseAlarmPreferences(data).read() }.exceptionOrNull())
        } finally {
            requireNotNull(scope.coroutineContext[Job]).cancelAndJoin()
        }
    }

    private suspend fun withStore(file: File, action: suspend (SunriseAlarmPreferences) -> Unit) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            action(SunriseAlarmPreferences(PreferenceDataStoreFactory.create(scope = scope) { file }))
        } finally {
            requireNotNull(scope.coroutineContext[Job]).cancelAndJoin()
        }
    }
}
