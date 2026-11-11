package studio.hypertext.curfew.ui

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Test
import studio.hypertext.curfew.alarm.AlarmSaveFailure
import studio.hypertext.curfew.alarm.FutureAlarmTimeRequired
import studio.hypertext.curfew.alarm.alarmSaveFailure

class AlarmSaveErrorTest {
    @Test fun knownDateValidationKeepsItsUsefulCorrection() {
        assertEquals(AlarmSaveFailure.FUTURE_TIME_REQUIRED, alarmSaveFailure(FutureAlarmTimeRequired()))
    }

    @Test fun internalValidationNeverReachesTheOwner() {
        for (detail in listOf("Choose a future date and time.", "private database detail", "")) {
            assertEquals(AlarmSaveFailure.SAVE_FAILED, alarmSaveFailure(IllegalArgumentException(detail)))
        }
    }

    @Test fun storageFailureDoesNotBlamePermissionsOrExposeItsPath() {
        assertEquals(AlarmSaveFailure.SAVE_FAILED, alarmSaveFailure(IOException("/private/app/database")))
    }
}
