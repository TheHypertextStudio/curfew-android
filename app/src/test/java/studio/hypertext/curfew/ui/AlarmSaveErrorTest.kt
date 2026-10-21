package studio.hypertext.curfew.ui

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Test

class AlarmSaveErrorTest {
    @Test fun knownDateValidationKeepsItsUsefulCorrection() {
        assertEquals("Choose a future date and time.", alarmSaveError(IllegalArgumentException("Choose a future date and time.")))
    }

    @Test fun internalValidationNeverReachesTheOwner() {
        for (detail in listOf("a schedule needs an identity", "Sunrise requires a release condition", "private database detail", "")) {
            assertEquals("Couldn’t save the alarm. Try again.", alarmSaveError(IllegalArgumentException(detail)))
        }
    }

    @Test fun storageFailureDoesNotBlamePermissionsOrExposeItsPath() {
        assertEquals("Couldn’t save the alarm. Try again.", alarmSaveError(IOException("/private/app/database")))
    }
}
