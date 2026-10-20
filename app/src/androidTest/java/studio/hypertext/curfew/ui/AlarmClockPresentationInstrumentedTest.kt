package studio.hypertext.curfew.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import android.text.format.DateFormat
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import java.time.Instant
import java.util.Date
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import studio.hypertext.curfew.MainActivity
import studio.hypertext.curfew.alarm.AlarmCampaignState
import studio.hypertext.curfew.alarm.AlarmRecurrencePolicy
import studio.hypertext.curfew.persistence.CurfewDatabase
import studio.hypertext.curfew.platform.AlarmRuntimeRepository
import studio.hypertext.curfew.platform.AndroidAlarmClockGateway

class AlarmClockPresentationInstrumentedTest {
    @Test
    fun quietScreenShowsTheMappedWallClockTime(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = AlarmRuntimeRepository(context)
        assertTrue("The fixture must not replace a pending alarm", repository.pending().isEmpty())
        val now = Instant.now()
        val nextWall = Instant.ofEpochSecond(((now.epochSecond - 15) / 30) * 30 + 75)
        val logicalNow = now.minusSeconds(300)
        val id = UUID.randomUUID().toString()
        val dao = CurfewDatabase.open(context).alarmCampaignDao()
        try {
            repository.save(AlarmCampaignState.Quiet(id, 1, nextWall.minusSeconds(300), AlarmRecurrencePolicy.default(), setOf("fixture")))
            // This is the persisted anchor left by a forward wall-clock change during an active alarm.
            dao.upsert(requireNotNull(dao.find(id)).copy(persistedAtWall = logicalNow.toEpochMilli(), persistedAtElapsed = SystemClock.elapsedRealtime()))
            val expected = "Next ring at ${DateFormat.getTimeFormat(context).format(Date.from(nextWall))}"
            val intent = Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_SHOW_ALARM, true)
                .putExtra(AndroidAlarmClockGateway.EXTRA_CAMPAIGN_ID, id)
            ActivityScenario.launch<MainActivity>(intent).use {
                val instrumentation = InstrumentationRegistry.getInstrumentation()
                fun labels(node: AccessibilityNodeInfo): List<String> = buildList {
                    node.text?.toString()?.let(::add)
                    for (index in 0 until node.childCount) node.getChild(index)?.let { addAll(labels(it)) }
                }
                fun currentLabels() = instrumentation.uiAutomation.rootInActiveWindow?.let(::labels).orEmpty()
                fun present() = expected in currentLabels()
                val deadline = SystemClock.elapsedRealtime() + 5_000
                while (!present() && SystemClock.elapsedRealtime() < deadline) Thread.sleep(100)
                context.cacheDir.resolve("clock-quiet-presentation.txt").writeText("Expected: $expected\n" + currentLabels().joinToString("\n"))
                instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
                    context.cacheDir.resolve("clock-quiet-presentation.png").outputStream().use { output -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, output) }
                }
                assertTrue("Quiet presentation must use the mapped next ring time: $expected; actual=${currentLabels()}", present())
            }
        } finally {
            AndroidAlarmClockGateway(context).cancel(id)
            dao.delete(id)
        }
    }
}
