package studio.hypertext.curfew.enforcement

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EnforcementSafetyPolicyTest {

    private val curfewPackage = "studio.hypertext.curfew"
    private val chosenApps = setOf("com.zhiliaoapp.musically", "com.instagram.android", "com.twitter.android")

    private val safetyContext = SafetyContext(
        curfewPackage = curfewPackage,
        defaultDialerPackage = "com.google.android.dialer",
        defaultSmsPackage = "com.google.android.apps.messaging",
        homePackages = setOf("com.google.android.apps.nexuslauncher"),
        activeNavigationPackages = setOf("com.google.android.apps.maps"),
        autofillServicePackages = setOf("com.1password.android"),
        isInCall = false,
    )

    private val activeSession = EnforcementSession(
        sessionId = "session-1",
        purpose = EnforcementPurpose.BEDTIME,
        startEpochMs = 1_000_000L,
        elapsedRealtimeStartMs = 10_000L,
        durationMinutes = 60,
        wallClockExpiryEpochMs = 1_000_000L + (60 * 60 * 1000L),
        chosenAppPackages = chosenApps,
        consentRevision = 1,
        state = EnforcementSessionState.ACTIVE,
    )

    @Test
    fun `intercepts chosen entertainment app during active session`() {
        val decision = EnforcementSafetyPolicy.evaluate(
            packageName = "com.zhiliaoapp.musically",
            session = activeSession,
            safetyContext = safetyContext,
            currentEpochMs = 1_100_000L,
            currentElapsedRealtimeMs = 110_000L,
        )
        assertEquals(InterceptionDecision.INTERCEPT, decision)
    }

    @Test
    fun `allows unknown package not in chosen list`() {
        val decision = EnforcementSafetyPolicy.evaluate(
            packageName = "com.example.unknownapp",
            session = activeSession,
            safetyContext = safetyContext,
            currentEpochMs = 1_100_000L,
            currentElapsedRealtimeMs = 110_000L,
        )
        assertEquals(InterceptionDecision.ALLOW, decision)
    }

    @Test
    fun `always allows curfew itself`() {
        val decision = EnforcementSafetyPolicy.evaluate(
            packageName = curfewPackage,
            session = activeSession,
            safetyContext = safetyContext,
            currentEpochMs = 1_100_000L,
            currentElapsedRealtimeMs = 110_000L,
        )
        assertEquals(InterceptionDecision.ALLOW, decision)
    }

    @Test
    fun `always allows dialer and emergency call packages`() {
        val dialerDecision = EnforcementSafetyPolicy.evaluate(
            packageName = "com.google.android.dialer",
            session = activeSession,
            safetyContext = safetyContext,
            currentEpochMs = 1_100_000L,
            currentElapsedRealtimeMs = 110_000L,
        )
        assertEquals(InterceptionDecision.ALLOW, dialerDecision)

        val phoneDecision = EnforcementSafetyPolicy.evaluate(
            packageName = "com.android.phone",
            session = activeSession,
            safetyContext = safetyContext,
            currentEpochMs = 1_100_000L,
            currentElapsedRealtimeMs = 110_000L,
        )
        assertEquals(InterceptionDecision.ALLOW, phoneDecision)
    }

    @Test
    fun `always allows messaging and navigation packages`() {
        val smsDecision = EnforcementSafetyPolicy.evaluate(
            packageName = "com.google.android.apps.messaging",
            session = activeSession,
            safetyContext = safetyContext,
            currentEpochMs = 1_100_000L,
            currentElapsedRealtimeMs = 110_000L,
        )
        assertEquals(InterceptionDecision.ALLOW, smsDecision)

        val mapsDecision = EnforcementSafetyPolicy.evaluate(
            packageName = "com.google.android.apps.maps",
            session = activeSession,
            safetyContext = safetyContext,
            currentEpochMs = 1_100_000L,
            currentElapsedRealtimeMs = 110_000L,
        )
        assertEquals(InterceptionDecision.ALLOW, mapsDecision)
    }

    @Test
    fun `always allows settings, system UI, and home launcher`() {
        val settingsDecision = EnforcementSafetyPolicy.evaluate(
            packageName = "com.android.settings",
            session = activeSession,
            safetyContext = safetyContext,
            currentEpochMs = 1_100_000L,
            currentElapsedRealtimeMs = 110_000L,
        )
        assertEquals(InterceptionDecision.ALLOW, settingsDecision)

        val systemUiDecision = EnforcementSafetyPolicy.evaluate(
            packageName = "com.android.systemui",
            session = activeSession,
            safetyContext = safetyContext,
            currentEpochMs = 1_100_000L,
            currentElapsedRealtimeMs = 110_000L,
        )
        assertEquals(InterceptionDecision.ALLOW, systemUiDecision)

        val launcherDecision = EnforcementSafetyPolicy.evaluate(
            packageName = "com.google.android.apps.nexuslauncher",
            session = activeSession,
            safetyContext = safetyContext,
            currentEpochMs = 1_100_000L,
            currentElapsedRealtimeMs = 110_000L,
        )
        assertEquals(InterceptionDecision.ALLOW, launcherDecision)
    }

    @Test
    fun `allows all apps when in active phone call`() {
        val inCallSafety = safetyContext.copy(isInCall = true)
        val decision = EnforcementSafetyPolicy.evaluate(
            packageName = "com.zhiliaoapp.musically",
            session = activeSession,
            safetyContext = inCallSafety,
            currentEpochMs = 1_100_000L,
            currentElapsedRealtimeMs = 110_000L,
        )
        assertEquals(InterceptionDecision.ALLOW, decision)
    }

    @Test
    fun `allows apps when session is cancelled via emergency stop`() {
        val cancelledSession = activeSession.copy(state = EnforcementSessionState.CANCELLED_EMERGENCY)
        val decision = EnforcementSafetyPolicy.evaluate(
            packageName = "com.zhiliaoapp.musically",
            session = cancelledSession,
            safetyContext = safetyContext,
            currentEpochMs = 1_100_000L,
            currentElapsedRealtimeMs = 110_000L,
        )
        assertEquals(InterceptionDecision.ALLOW, decision)
    }

    @Test
    fun `allows apps when elapsed realtime exceeds session duration`() {
        val expiredElapsedMs = activeSession.elapsedRealtimeStartMs + (61 * 60 * 1000L)
        val decision = EnforcementSafetyPolicy.evaluate(
            packageName = "com.zhiliaoapp.musically",
            session = activeSession,
            safetyContext = safetyContext,
            currentEpochMs = 1_100_000L,
            currentElapsedRealtimeMs = expiredElapsedMs,
        )
        assertEquals(InterceptionDecision.ALLOW, decision)
    }

    @Test
    fun `detects clock rollback and does not prolong session`() {
        // Wall clock moved backwards before start
        val rolledBackEpoch = activeSession.startEpochMs - 1000L
        val decision = EnforcementSafetyPolicy.evaluate(
            packageName = "com.zhiliaoapp.musically",
            session = activeSession,
            safetyContext = safetyContext,
            currentEpochMs = rolledBackEpoch,
            currentElapsedRealtimeMs = 110_000L,
        )
        assertEquals(InterceptionDecision.ALLOW, decision)
    }

    @Test
    fun `disarmed after reboot resumes disarmed`() {
        val disarmedSession = activeSession.copy(state = EnforcementSessionState.DISARMED_AFTER_REBOOT)
        val decision = EnforcementSafetyPolicy.evaluate(
            packageName = "com.zhiliaoapp.musically",
            session = disarmedSession,
            safetyContext = safetyContext,
            currentEpochMs = 1_100_000L,
            currentElapsedRealtimeMs = 110_000L,
        )
        assertEquals(InterceptionDecision.ALLOW, decision)
    }
}
