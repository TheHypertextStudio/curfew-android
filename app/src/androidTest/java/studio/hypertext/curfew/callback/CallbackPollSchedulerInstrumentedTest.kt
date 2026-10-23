package studio.hypertext.curfew.callback

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkInfo
import androidx.work.WorkManager
import java.time.Duration
import java.util.UUID
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CallbackPollSchedulerInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val manager = WorkManager.getInstance(context)
    private val campaignId = UUID.randomUUID().toString()
    private val name = "curfew-callback-$campaignId"
    private val delay = Duration.ofMinutes(10)

    @After
    fun cancelFixture() { manager.cancelUniqueWork(name).result.get(10, TimeUnit.SECONDS) }

    private fun work(): List<WorkInfo> = manager.getWorkInfosForUniqueWork(name).get(10, TimeUnit.SECONDS)

    private fun awaitCount(count: Int): List<WorkInfo> {
        repeat(100) {
            val current = work()
            if (current.size == count) return current
            Thread.sleep(20)
        }
        error("Expected $count poll jobs, found ${work()}")
    }

    @Test
    fun repeatedNudgesPreserveTheExistingPollInsteadOfCancellingIt() {
        CallbackPollScheduler.schedule(context, campaignId, delay).result.get(10, TimeUnit.SECONDS)
        val first = awaitCount(1).single()
        CallbackPollScheduler.schedule(context, campaignId, delay).result.get(10, TimeUnit.SECONDS)
        val pending = work().filter { !it.state.isFinished }
        assertEquals(listOf(first.id), pending.map { it.id })
        assertEquals(WorkInfo.State.ENQUEUED, pending.single().state)
    }

    @Test
    fun continuationWaitsForItsPredecessorWithoutCancellingIt() {
        CallbackPollScheduler.schedule(context, campaignId, delay).result.get(10, TimeUnit.SECONDS)
        val first = awaitCount(1).single()
        CallbackPollScheduler.continuePolling(context, campaignId, delay).result.get(10, TimeUnit.SECONDS)
        val chain = awaitCount(2)
        assertEquals(WorkInfo.State.ENQUEUED, chain.single { it.id == first.id }.state)
        assertEquals(WorkInfo.State.BLOCKED, chain.single { it.id != first.id }.state)
        manager.cancelUniqueWork(name).result.get(10, TimeUnit.SECONDS)
        CallbackPollScheduler.continuePolling(context, campaignId, delay).result.get(10, TimeUnit.SECONDS)
        repeat(100) {
            if (work().any { !it.state.isFinished }) return@repeat
            Thread.sleep(20)
        }
        assertTrue(work().any { it.state == WorkInfo.State.ENQUEUED })
    }
}
