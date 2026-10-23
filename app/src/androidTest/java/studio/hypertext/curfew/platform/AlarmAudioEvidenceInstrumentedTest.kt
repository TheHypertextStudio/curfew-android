package studio.hypertext.curfew.platform

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmAudioEvidenceInstrumentedTest {
    private fun dump(player: String) = "  players:\n$player\n  ducked players:\n"

    @Test fun systemNotificationCannotStandInForCurfewPlayback() {
        assertFalse(hasStartedAlarmPlayer(dump("piid:1 u/pid:1000/20 state:started usage=USAGE_ALARM"), 10123))
    }

    @Test fun pausedOrNonAlarmCurfewPlayersCannotPass() {
        assertFalse(hasStartedAlarmPlayer(dump("piid:1 u/pid:10123/20 state:paused usage=USAGE_ALARM"), 10123))
        assertFalse(hasStartedAlarmPlayer(dump("piid:1 u/pid:10123/20 state:started usage=USAGE_MEDIA"), 10123))
        assertFalse(hasStartedAlarmPlayer("focus u/pid:10123/20 state:started usage=USAGE_ALARM", 10123))
    }

    @Test fun activeCurfewAlarmIsAcceptedWithoutMatchingAnotherUidPrefix() {
        val player = "piid:1 u/pid:10123/20 state:started usage=USAGE_ALARM"
        assertTrue(hasStartedAlarmPlayer(dump(player), 10123))
        assertFalse(hasStartedAlarmPlayer(dump(player), 1012))
    }
}
