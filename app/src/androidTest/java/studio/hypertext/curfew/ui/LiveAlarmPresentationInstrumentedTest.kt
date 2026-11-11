package studio.hypertext.curfew.ui

import android.content.Intent
import android.view.WindowManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import studio.hypertext.curfew.MainActivity
import studio.hypertext.curfew.alarm.AlarmCampaignState
import studio.hypertext.curfew.alarm.AlarmRecurrencePolicy
import studio.hypertext.curfew.callback.CallbackConfigurationRepository
import studio.hypertext.curfew.platform.AlarmRuntimeRepository
import studio.hypertext.curfew.platform.AndroidAlarmClockGateway
import studio.hypertext.curfew.platform.cleanupAlarmFixture

class LiveAlarmPresentationInstrumentedTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test
    fun showsTheOccurrencesCheckInAndTracksQuietAndCompletion() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val runtime = AlarmRuntimeRepository(context)
        check(runtime.pending().isEmpty()) { "Do not run the fixture with an active alarm" }
        val id = UUID.randomUUID().toString()
        val configurations = CallbackConfigurationRepository(context)
        val connection = configurations.save(
            "Fixture check-in",
            "https://example.invalid/verify",
            "https://example.invalid/check-in",
        )
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_SHOW_ALARM, true)
            .putExtra(AndroidAlarmClockGateway.EXTRA_CAMPAIGN_ID, id)
        val now = Instant.now()
        val policy = AlarmRecurrencePolicy.default()
        val devices = setOf("fixture-device")
        try {
            // Launch before saving the occurrence. This keeps the foreground restore
            // path from starting actual playback for the presentation fixture.
            ActivityScenario.launch<MainActivity>(intent).use { scenario ->
                runtime.save(AlarmCampaignState.Ringing(id, 1, now, now.plusSeconds(120), policy, devices), callbackId = connection.callbackId)
                awaitText("Waiting for Fixture check-in")
                compose.onNodeWithText("Open check-in").assertIsDisplayed()

                runtime.save(AlarmCampaignState.Quiet(id, 1, now.plusSeconds(180), policy, devices))
                awaitText("Next ring at", substring = true)
                compose.onNodeWithText("Open check-in").assertIsDisplayed()

                runtime.save(
                    AlarmCampaignState.Satisfied(
                        id, now, "Fixture check-in", devicesToRelease = devices, policy = policy, selectedDeviceIds = devices,
                    ),
                )
                compose.waitUntil(5_000) { compose.onAllNodesWithText("Next ring at", substring = true).fetchSemanticsNodes().isEmpty() }
                scenario.onActivity {
                    assertEquals(0, it.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
        } finally {
            cleanupAlarmFixture(context, id)
            configurations.delete(connection.callbackId)
        }
    }

    private fun awaitText(text: String, substring: Boolean = false) {
        compose.waitUntil(5_000) { compose.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty() }
    }
}
