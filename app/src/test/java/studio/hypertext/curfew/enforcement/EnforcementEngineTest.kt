package studio.hypertext.curfew.enforcement

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EnforcementEngineTest {

    private val safetyContext = SafetyContext(
        curfewPackage = "studio.hypertext.curfew",
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
        chosenAppPackages = setOf("com.zhiliaoapp.musically", "com.instagram.android"),
        consentRevision = 1,
        state = EnforcementSessionState.ACTIVE,
    )

    @Test
    fun `invokes onIntercept when chosen package opened`() {
        val engine = EnforcementEngine()
        var intercepted = false
        val decision = engine.processEvent(
            packageName = "com.zhiliaoapp.musically",
            session = activeSession,
            safetyContext = safetyContext,
            currentEpochMs = 1_100_000L,
            currentElapsedRealtimeMs = 20_000L,
            onIntercept = { intercepted = true },
        )

        assertEquals(InterceptionDecision.INTERCEPT, decision)
        assertTrue(intercepted)
    }

    @Test
    fun `debounces rapid repeated events within cooldown period`() {
        val engine = EnforcementEngine(cooldownMs = 1500L)
        var interceptCount = 0

        // First event at t=20_000
        engine.processEvent(
            packageName = "com.zhiliaoapp.musically",
            session = activeSession,
            safetyContext = safetyContext,
            currentEpochMs = 1_100_000L,
            currentElapsedRealtimeMs = 20_000L,
            onIntercept = { interceptCount++ },
        )
        assertEquals(1, interceptCount)

        // Second event within cooldown at t=20_500 (500ms later)
        val secondDecision = engine.processEvent(
            packageName = "com.zhiliaoapp.musically",
            session = activeSession,
            safetyContext = safetyContext,
            currentEpochMs = 1_100_500L,
            currentElapsedRealtimeMs = 20_500L,
            onIntercept = { interceptCount++ },
        )
        // Should be ignored / suppressed by debounce cooldown
        assertEquals(InterceptionDecision.ALLOW, secondDecision)
        assertEquals(1, interceptCount)

        // Third event after cooldown at t=21_600 (1600ms later)
        val thirdDecision = engine.processEvent(
            packageName = "com.zhiliaoapp.musically",
            session = activeSession,
            safetyContext = safetyContext,
            currentEpochMs = 1_101_600L,
            currentElapsedRealtimeMs = 21_600L,
            onIntercept = { interceptCount++ },
        )
        assertEquals(InterceptionDecision.INTERCEPT, thirdDecision)
        assertEquals(2, interceptCount)
    }

    @Test
    fun `in-memory emergency disarm immediately stops interception`() {
        val engine = EnforcementEngine()
        var interceptCount = 0

        engine.processEvent(
            packageName = "com.zhiliaoapp.musically",
            session = activeSession,
            safetyContext = safetyContext,
            currentEpochMs = 1_100_000L,
            currentElapsedRealtimeMs = 20_000L,
            onIntercept = { interceptCount++ },
        )
        assertEquals(1, interceptCount)

        // Emergency disarm in memory
        engine.emergencyDisarmInMemory()
        assertTrue(engine.isDisarmedInMemory)

        // Next event after cooldown must not intercept
        val decision = engine.processEvent(
            packageName = "com.zhiliaoapp.musically",
            session = activeSession,
            safetyContext = safetyContext,
            currentEpochMs = 1_200_000L,
            currentElapsedRealtimeMs = 30_000L,
            onIntercept = { interceptCount++ },
        )
        assertEquals(InterceptionDecision.ALLOW, decision)
        assertEquals(1, interceptCount)
    }

    @Test
    fun `safe packages are never intercepted`() {
        val engine = EnforcementEngine()
        var intercepted = false

        val decision = engine.processEvent(
            packageName = "com.android.settings",
            session = activeSession,
            safetyContext = safetyContext,
            currentEpochMs = 1_100_000L,
            currentElapsedRealtimeMs = 20_000L,
            onIntercept = { intercepted = true },
        )

        assertEquals(InterceptionDecision.ALLOW, decision)
        assertFalse(intercepted)
    }
}
