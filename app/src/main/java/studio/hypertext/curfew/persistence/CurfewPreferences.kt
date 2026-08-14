package studio.hypertext.curfew.persistence

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.curfewPreferences by preferencesDataStore(name = "curfew_preferences")

data class CurfewPreferenceState(
    val primaryAlarmDeviceId: String?,
    val readinessAcknowledged: Boolean,
    val testSoundCompleted: Boolean,
    val accountEnrolled: Boolean,
    val selectedCallbackId: String?,
    val accountSignedIn: Boolean,
)

class CurfewPreferences(private val context: Context) {
    val state: Flow<CurfewPreferenceState> = context.curfewPreferences.data.map { preferences ->
        CurfewPreferenceState(
            primaryAlarmDeviceId = preferences[PRIMARY_DEVICE],
            readinessAcknowledged = preferences[READINESS_ACKNOWLEDGED] ?: false,
            testSoundCompleted = preferences[TEST_SOUND_COMPLETED] ?: false,
            accountEnrolled = preferences[ACCOUNT_ENROLLED] ?: false,
            selectedCallbackId = preferences[SELECTED_CALLBACK],
            accountSignedIn = preferences[ACCOUNT_SIGNED_IN] ?: false,
        )
    }

    suspend fun setPrimaryAlarmDevice(deviceId: String) {
        require(deviceId.isNotBlank())
        context.curfewPreferences.edit { it[PRIMARY_DEVICE] = deviceId }
    }

    suspend fun acknowledgeLimitations() {
        context.curfewPreferences.edit { it[READINESS_ACKNOWLEDGED] = true }
    }

    suspend fun recordTestSound() {
        context.curfewPreferences.edit { it[TEST_SOUND_COMPLETED] = true }
    }

    suspend fun setAccountEnrolled(enrolled: Boolean) {
        context.curfewPreferences.edit { it[ACCOUNT_ENROLLED] = enrolled }
    }

    suspend fun setAccountSignedIn(signedIn: Boolean) {
        context.curfewPreferences.edit { it[ACCOUNT_SIGNED_IN] = signedIn }
    }

    suspend fun selectCallback(callbackId: String) {
        require(callbackId.isNotBlank())
        context.curfewPreferences.edit { it[SELECTED_CALLBACK] = callbackId }
    }

    suspend fun nextWriterCounter(): Long {
        var next = 0L
        context.curfewPreferences.edit { preferences ->
            next = (preferences[WRITER_COUNTER] ?: 0L) + 1L
            preferences[WRITER_COUNTER] = next
        }
        return next
    }

    private companion object {
        val PRIMARY_DEVICE = stringPreferencesKey("primary_alarm_device")
        val READINESS_ACKNOWLEDGED = booleanPreferencesKey("readiness_acknowledged")
        val TEST_SOUND_COMPLETED = booleanPreferencesKey("test_sound_completed")
        val ACCOUNT_ENROLLED = booleanPreferencesKey("account_enrolled")
        val SELECTED_CALLBACK = stringPreferencesKey("selected_callback")
        val WRITER_COUNTER = longPreferencesKey("device_writer_counter")
        val ACCOUNT_SIGNED_IN = booleanPreferencesKey("account_signed_in")
    }
}
