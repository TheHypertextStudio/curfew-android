package studio.hypertext.curfew.alarm

import java.io.IOException
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import studio.hypertext.curfew.persistence.FirstUseChoice
import studio.hypertext.curfew.persistence.FirstUseLoadState
import studio.hypertext.curfew.persistence.FirstUseState

/**
 * Covers the cold-launch decision when the saved alarm or the first-use record cannot be read,
 * which must show the retry screen instead of starting setup over an existing alarm.
 */
class SavedAlarmReadTest {
    private val draft = AlarmSetupDraft("draft", LocalTime.of(7, 0), LocalDate.of(2030, 10, 23), emptySet())
    private val alarm = SavedSunriseAlarm(draft.toSchedule("check-in"), null)
    private val startedWithDraft = FirstUseLoadState.Ready(FirstUseState(FirstUseChoice.STARTED, draft))

    @Test
    fun aFailedAlarmReadIsNotTheSameAsNoAlarm(): Unit = runBlocking {
        val reads = flow<SavedSunriseAlarm?> { throw IOException("unreadable") }.asSavedAlarmRead().toList()

        assertEquals(listOf(SavedAlarmRead.Failed), reads)
    }

    @Test
    fun readableAlarmsAndEmptyStorageStayLoaded(): Unit = runBlocking {
        val reads = flowOf(alarm, null).asSavedAlarmRead().toList()

        assertEquals(listOf(SavedAlarmRead.Loaded(alarm), SavedAlarmRead.Loaded(null)), reads)
    }

    @Test
    fun aFailedAlarmReadShowsRetryInsteadOfResumingSetup() {
        val entry = alarmSetupEntry(SavedAlarmRead.Failed, startedWithDraft)

        assertTrue(entry.loaded)
        assertTrue(entry.loadError)
        assertFalse(entry.startSetup)
        assertFalse(entry.showWelcome)
        assertNull(entry.draft)
    }

    @Test
    fun aMissingAlarmResumesTheSavedDraft() {
        val entry = alarmSetupEntry(SavedAlarmRead.Loaded(null), startedWithDraft)

        assertFalse(entry.loadError)
        assertTrue(entry.startSetup)
        assertEquals(draft, entry.draft)
    }

    @Test
    fun aReadableAlarmOutranksABrokenFirstUseRecord() {
        val entry = alarmSetupEntry(SavedAlarmRead.Loaded(alarm), FirstUseLoadState.Error)

        assertFalse(entry.loadError)
        assertFalse(entry.startSetup)
        assertNull(entry.draft)
    }

    @Test
    fun aBrokenFirstUseRecordWithoutAnAlarmShowsRetry() {
        assertTrue(alarmSetupEntry(SavedAlarmRead.Loaded(null), FirstUseLoadState.Error).loadError)
    }

    @Test
    fun entryWaitsForFirstUseBeforeItCountsAsLoaded() {
        assertFalse(alarmSetupEntry(SavedAlarmRead.Failed, FirstUseLoadState.Loading).loaded)
    }
}
