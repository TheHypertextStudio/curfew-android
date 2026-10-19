package studio.hypertext.curfew.persistence

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CurfewDatabaseInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val database = Room.inMemoryDatabaseBuilder(
        context,
        CurfewDatabase::class.java,
    ).allowMainThreadQueries().build()

    @After
    fun close() = database.close()

    @Test
    fun campaignAndOutboxSurviveRepositoryRoundTrip() = runBlocking {
        val campaign = AlarmCampaignEntity(
            campaignId = "campaign-1",
            protocolJson = "{\"campaignId\":\"campaign-1\"}",
            state = "ringing_attempt",
            finalDeadlineAt = Instant.parse("2026-08-10T14:16:00Z").toEpochMilli(),
            persistedAtWall = Instant.parse("2026-08-10T14:00:00Z").toEpochMilli(),
            persistedAtElapsed = 100L,
            bootId = "boot-a",
            writerCounter = 4,
        )
        database.alarmCampaignDao().upsert(campaign)

        assertEquals(campaign, database.alarmCampaignDao().find("campaign-1"))

        val outbox = SyncOutboxEntity(
            operationId = "operation-1",
            namespace = "wake-campaigns",
            encryptedPayload = byteArrayOf(1, 2, 3),
            createdAt = 10L,
            attemptCount = 0,
        )
        database.syncOutboxDao().enqueue(outbox)
        assertEquals("operation-1", database.syncOutboxDao().oldest(1).single().operationId)
    }

    @Test
    fun cancellingFutureScheduleDoesNotDeleteRingingOrReleasedState() = runBlocking {
        val dao = database.alarmCampaignDao()
        val scheduled = AlarmCampaignEntity(
            "future", "{}", "scheduled", Long.MAX_VALUE, 0, 0, "boot", 0, "condition",
        )
        dao.upsert(scheduled)
        dao.upsert(scheduled.copy(campaignId = "active", state = "ringing"))
        dao.upsert(scheduled.copy(campaignId = "released", state = "satisfied"))

        dao.deleteScheduled("future")
        dao.deleteScheduled("active")
        dao.deleteScheduled("released")

        assertNull(dao.find("future"))
        assertNotNull(dao.find("active"))
        assertNotNull(dao.find("released"))
    }

    @Test
    fun observedCampaignReportsItsPersistedRelease() = runBlocking {
        val dao = database.alarmCampaignDao()
        val ringing = AlarmCampaignEntity(
            "active", "{}", "ringing", Long.MAX_VALUE, 0, 0, "boot", 0, "condition",
        )
        dao.upsert(ringing)
        assertEquals("ringing", dao.observe("active").first()?.state)

        dao.upsert(ringing.copy(state = "satisfied"))

        assertEquals("satisfied", dao.observe("active").first()?.state)
    }
}
