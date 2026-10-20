package studio.hypertext.curfew.ui

import android.os.SystemClock
import android.app.Activity
import androidx.activity.compose.LocalActivity
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import studio.hypertext.curfew.ui.theme.CurfewTheme

class AlarmRecoveryScreenInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var activity: Activity
    private lateinit var inputMode: InputModeManager
    private val startedAt = AtomicLong()
    private val confirmedAt = AtomicLong()
    private val starts = AtomicInteger()
    private val confirmations = AtomicInteger()
    private val cancellations = AtomicInteger()
    private val reason = "My morning check-in is unavailable and I have to leave for work."

    private fun show(credentialAvailable: Boolean = true, owner: LifecycleOwner? = null) {
        compose.setContent {
            activity = requireNotNull(LocalActivity.current)
            inputMode = LocalInputModeManager.current
            CompositionLocalProvider(LocalLifecycleOwner provides (owner ?: LocalLifecycleOwner.current)) {
            CurfewTheme {
                AlarmRecoveryScreen(
                    conditionLabel = "Morning check-in",
                    credentialAvailable = credentialAvailable,
                    busy = false,
                    error = null,
                    onBack = {},
                    onBeginHold = { startedAt.set(SystemClock.elapsedRealtime()); starts.incrementAndGet() },
                    onCancelHold = { cancellations.incrementAndGet() },
                    onConfirm = { confirmedAt.set(SystemClock.elapsedRealtime()); confirmations.incrementAndGet() },
                    onSecuritySettings = {},
                )
            }
            }
        }
    }

    @Test
    fun recoveryRequiresAReasonBeforeHolding() {
        show()
        compose.onNodeWithText("Hold for 3 seconds").assertIsNotEnabled()
        compose.onNodeWithText("What happened?").performTextInput(reason)
        compose.onNodeWithText("Hold for 3 seconds").assertIsEnabled()
    }

    @Test
    fun releasingEarlyCancelsTheHold() {
        show()
        compose.onNodeWithText("What happened?").performTextInput(reason)
        val previousCancellations = cancellations.get()
        compose.onNodeWithText("Hold for 3 seconds").assertIsEnabled().assertIsDisplayed()
            .performTouchInput { down(center); up() }
        compose.waitUntil(2_000) { cancellations.get() > previousCancellations }
        assertEquals(1, starts.get())
        assertEquals(0, confirmations.get())
    }

    @Test
    fun accessibleActivationKeepsTheThreeSecondConfirmation() {
        show()
        compose.onNodeWithText("What happened?").performTextInput(reason)
        compose.onNodeWithText("Hold for 3 seconds").performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.waitUntil(5_000) { confirmations.get() == 1 }
        assertEquals(1, starts.get())
        assertTrue("Accessible confirmation must wait three real seconds",
            confirmedAt.get() - startedAt.get() >= 3_000)
    }

    @Test
    fun leavingTheForegroundCancelsAnAccessibleHold() {
        val owner = object : LifecycleOwner {
            override val lifecycle = LifecycleRegistry(this)
        }
        compose.runOnUiThread { owner.lifecycle.currentState = Lifecycle.State.RESUMED }
        show(owner = owner)
        compose.onNodeWithText("What happened?").performTextInput(reason)
        compose.waitForIdle()
        val previousCancellations = cancellations.get()
        compose.onNodeWithText("Hold for 3 seconds").performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnUiThread { owner.lifecycle.currentState = Lifecycle.State.CREATED }
        compose.waitUntil(1_000) { cancellations.get() > previousCancellations }
        compose.runOnUiThread { owner.lifecycle.currentState = Lifecycle.State.RESUMED }
        try {
            compose.waitUntil(4_000) { confirmations.get() > 0 }
        } catch (_: ComposeTimeoutException) {
            // Keep pumping the test dispatcher beyond the full real-time hold.
        }
        assertEquals(1, starts.get())
        assertEquals(0, confirmations.get())
    }

    @Test
    fun aHeldKeyboardKeyCannotRestartConfirmationThroughAutoRepeat() {
        show()
        compose.onNodeWithText("What happened?").performTextInput(reason)
        compose.runOnUiThread {
            inputMode.requestInputMode(InputMode.Touch)
            assertTrue("The keyboard fixture must leave touch mode", inputMode.requestInputMode(InputMode.Keyboard))
        }
        compose.onNodeWithText("Hold for 3 seconds").performSemanticsAction(SemanticsActions.RequestFocus) {
            assertTrue("The confirmation button must accept keyboard focus", it())
        }
        compose.onNodeWithText("Hold for 3 seconds").assertIsFocused()
        compose.waitForIdle()
        val downAt = SystemClock.uptimeMillis()
        try {
            compose.runOnUiThread {
                activity.window.decorView.dispatchKeyEvent(android.view.KeyEvent(downAt, downAt,
                    android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_ENTER, 0))
            }
            assertEquals("The focused button must receive the initial key down", 1, starts.get())
            compose.waitUntil(8_000) { confirmations.get() == 1 }
            compose.runOnUiThread {
                activity.window.decorView.dispatchKeyEvent(android.view.KeyEvent(downAt, SystemClock.uptimeMillis(),
                    android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_ENTER, 1))
            }
            assertEquals("One physical press must start only one hold", 1, starts.get())
        } finally {
            compose.runOnUiThread {
                activity.window.decorView.dispatchKeyEvent(android.view.KeyEvent(downAt, SystemClock.uptimeMillis(),
                    android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_ENTER, 0))
            }
        }
    }

    @Test
    fun aMissingScreenLockDoesNotAskForAReasonYet() {
        show(credentialAvailable = false)
        compose.onNodeWithText("What happened?").assertDoesNotExist()
    }

    @Test
    fun aMissingScreenLockOffersSetupInsteadOfRecovery() {
        show(credentialAvailable = false)
        compose.onNodeWithText("Open security settings").assertIsEnabled()
        compose.onNodeWithText("Hold for 3 seconds").assertDoesNotExist()
    }
}
