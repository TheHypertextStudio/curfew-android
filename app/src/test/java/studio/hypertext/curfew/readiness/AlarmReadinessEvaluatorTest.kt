package studio.hypertext.curfew.readiness

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmReadinessEvaluatorTest {
    private val evaluator = AlarmReadinessEvaluator()

    @Test
    fun `exact alarm access is required before arming`() {
        val result = evaluator.evaluate(readyCapabilities().copy(canScheduleExactAlarms = false))

        assertFalse(result.canArm)
        assertTrue(result.requiredActions.contains(ReadinessAction.GRANT_EXACT_ALARM_ACCESS))
    }

    @Test
    fun `notification and audible channel failures block arming`() {
        val result = evaluator.evaluate(
            readyCapabilities().copy(
                notificationsGranted = false,
                alarmChannelAudible = false,
            ),
        )

        assertFalse(result.canArm)
        assertTrue(result.requiredActions.contains(ReadinessAction.GRANT_NOTIFICATIONS))
        assertTrue(result.requiredActions.contains(ReadinessAction.REPAIR_ALARM_CHANNEL))
    }

    @Test
    fun `full screen denial uses a high priority lock screen fallback`() {
        val result = evaluator.evaluate(readyCapabilities().copy(fullScreenIntentAllowed = false))

        assertTrue(result.canArm)
        assertEquals(AlarmPresentation.LOCK_SCREEN_NOTIFICATION, result.presentation)
        assertTrue(result.disclosures.contains(ReadinessDisclosure.FULL_SCREEN_FALLBACK))
    }

    @Test
    fun `test sound and limitation acknowledgement are required`() {
        val result = evaluator.evaluate(
            readyCapabilities().copy(
                testSoundCompleted = false,
                limitationsAcknowledged = false,
            ),
        )

        assertFalse(result.canArm)
        assertTrue(result.requiredActions.contains(ReadinessAction.PLAY_TEST_SOUND))
        assertTrue(result.requiredActions.contains(ReadinessAction.ACKNOWLEDGE_LIMITATIONS))
    }

    @Test
    fun `readiness never authorizes a system volume change`() {
        val result = evaluator.evaluate(readyCapabilities())

        assertTrue(result.canArm)
        assertFalse(result.mayChangeSystemVolume)
        assertTrue(result.disclosures.contains(ReadinessDisclosure.DO_NOT_DISTURB_BEHAVIOR))
        assertTrue(result.disclosures.contains(ReadinessDisclosure.BATTERY_AND_OEM_LIMITATIONS))
        assertTrue(result.disclosures.contains(ReadinessDisclosure.OS_POWER_CONTROLS))
    }

    private fun readyCapabilities() = AlarmCapabilities(
        canScheduleExactAlarms = true,
        notificationsGranted = true,
        fullScreenIntentAllowed = true,
        alarmChannelAudible = true,
        testSoundCompleted = true,
        limitationsAcknowledged = true,
    )
}
