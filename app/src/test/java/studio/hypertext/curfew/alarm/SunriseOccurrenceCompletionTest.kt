package studio.hypertext.curfew.alarm

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SunriseOccurrenceCompletionTest {
    @Test
    fun lastRecoveryTriggersStayInstalledUntilTheNextScheduleIsDurable() = runBlocking {
        val events = mutableListOf<String>()
        SunriseOccurrenceCompletion(
            consume = { events += "consume:$it" },
            scheduleNext = { events += "next" },
            cancelTransition = { events += "cancel-clock:$it" },
            cancelPolling = { events += "cancel-poll:$it" },
        ).complete("morning")
        assertEquals(listOf("consume:morning", "next", "cancel-poll:morning", "cancel-clock:morning"), events)
    }

    @Test
    fun failureToConsumeKeepsTheLastRecoveryTriggers() = runBlocking {
        val events = mutableListOf<String>()
        val failure = runCatching {
            SunriseOccurrenceCompletion(
                consume = { error("storage unavailable") },
                scheduleNext = { events += "next" },
                cancelTransition = { events += "cancel-clock" },
                cancelPolling = { events += "cancel-poll" },
            ).complete("morning")
        }
        assertTrue(failure.isFailure)
        assertTrue(events.isEmpty())
    }

    @Test
    fun failureToScheduleTomorrowKeepsTheLastRecoveryTriggers() = runBlocking {
        val events = mutableListOf<String>()
        val failure = runCatching {
            SunriseOccurrenceCompletion(
                consume = { events += "consume" },
                scheduleNext = { error("next schedule unavailable") },
                cancelTransition = { events += "cancel-clock" },
                cancelPolling = { events += "cancel-poll" },
            ).complete("morning")
        }
        assertTrue(failure.isFailure)
        assertEquals(listOf("consume"), events)
    }
}
