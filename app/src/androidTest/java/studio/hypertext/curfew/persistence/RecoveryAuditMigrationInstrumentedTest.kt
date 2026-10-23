package studio.hypertext.curfew.persistence

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class RecoveryAuditMigrationInstrumentedTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), CurfewDatabase::class.java)

    @Test
    fun existingAlarmAndCallbackSurviveAddingRecoveryHistory() {
        val name = "recovery-migration-fixture"
        helper.createDatabase(name, 1).use { database ->
            database.execSQL("INSERT INTO alarm_campaigns VALUES ('morning','saved-payload','ringing',9223372036854775807,1,1,'boot-1',4,'check-in')")
            database.execSQL("INSERT INTO callback_definitions VALUES ('check-in','Morning check-in','https://example.test/wake',NULL,15,5,60)")
        }
        helper.runMigrationsAndValidate(name, 2, true).use { database ->
            database.query("SELECT protocolJson,callbackId,writerCounter FROM alarm_campaigns WHERE campaignId='morning'").use {
                check(it.moveToFirst())
                assertEquals("saved-payload", it.getString(0))
                assertEquals("check-in", it.getString(1))
                assertEquals(4, it.getInt(2))
            }
            database.query("SELECT displayLabel FROM callback_definitions WHERE callbackId='check-in'").use {
                check(it.moveToFirst())
                assertEquals("Morning check-in", it.getString(0))
            }
            database.execSQL("INSERT INTO alarm_recovery_audit VALUES ('recovery','morning','Fixture recovery',123)")
            database.query("SELECT COUNT(*) FROM alarm_recovery_audit").use {
                check(it.moveToFirst())
                assertEquals(1, it.getInt(0))
            }
        }
    }
}
