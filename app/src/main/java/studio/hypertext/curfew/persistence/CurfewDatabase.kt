package studio.hypertext.curfew.persistence

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

@Entity(tableName = "alarm_campaigns", primaryKeys = ["campaignId"])
data class AlarmCampaignEntity(
    val campaignId: String,
    val protocolJson: String,
    val state: String,
    val finalDeadlineAt: Long,
    val persistedAtWall: Long,
    val persistedAtElapsed: Long,
    val bootId: String,
    val writerCounter: Long,
    val callbackId: String? = null,
)

@Entity(tableName = "sync_outbox", primaryKeys = ["operationId"])
data class SyncOutboxEntity(
    val operationId: String,
    val namespace: String,
    val encryptedPayload: ByteArray,
    val createdAt: Long,
    val attemptCount: Int,
)

@Entity(tableName = "callback_definitions", primaryKeys = ["callbackId"])
data class CallbackDefinitionEntity(
    val callbackId: String,
    val displayLabel: String,
    val endpoint: String,
    val actionUrl: String?,
    val intervalSeconds: Long,
    val requestTimeoutSeconds: Long,
    val maximumBackoffSeconds: Long,
)

@Dao
interface AlarmCampaignDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(campaign: AlarmCampaignEntity)

    @Query("SELECT * FROM alarm_campaigns WHERE campaignId = :campaignId LIMIT 1")
    suspend fun find(campaignId: String): AlarmCampaignEntity?

    @Query("SELECT * FROM alarm_campaigns WHERE state NOT IN ('satisfied', 'overridden') ORDER BY persistedAtWall")
    suspend fun pending(): List<AlarmCampaignEntity>

    @Query("DELETE FROM alarm_campaigns WHERE campaignId = :campaignId")
    suspend fun delete(campaignId: String)
}

@Dao
interface SyncOutboxDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun enqueue(operation: SyncOutboxEntity)

    @Query("SELECT * FROM sync_outbox ORDER BY createdAt LIMIT :limit")
    suspend fun oldest(limit: Int): List<SyncOutboxEntity>

    @Query("UPDATE sync_outbox SET attemptCount = attemptCount + 1 WHERE operationId = :operationId")
    suspend fun recordFailure(operationId: String)

    @Query("DELETE FROM sync_outbox WHERE operationId = :operationId")
    suspend fun acknowledge(operationId: String)
}

@Dao
interface CallbackDefinitionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(definition: CallbackDefinitionEntity)

    @Query("SELECT * FROM callback_definitions WHERE callbackId = :callbackId LIMIT 1")
    suspend fun find(callbackId: String): CallbackDefinitionEntity?

    @Query("SELECT * FROM callback_definitions ORDER BY displayLabel")
    suspend fun all(): List<CallbackDefinitionEntity>

    @Query("DELETE FROM callback_definitions WHERE callbackId = :callbackId")
    suspend fun delete(callbackId: String)
}

@Database(
    entities = [
        AlarmCampaignEntity::class,
        SyncOutboxEntity::class,
        CallbackDefinitionEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class CurfewDatabase : RoomDatabase() {
    abstract fun alarmCampaignDao(): AlarmCampaignDao
    abstract fun syncOutboxDao(): SyncOutboxDao
    abstract fun callbackDefinitionDao(): CallbackDefinitionDao

    companion object {
        @Volatile
        private var instance: CurfewDatabase? = null

        fun open(context: Context): CurfewDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                CurfewDatabase::class.java,
                "curfew-private.db",
            ).build().also { instance = it }
        }
    }
}
