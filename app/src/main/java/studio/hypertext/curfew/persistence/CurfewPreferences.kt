package studio.hypertext.curfew.persistence

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
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
    val verifiedCallbackId: String? = null,
) {
    fun isCallbackReady(callbackId: String): Boolean =
        selectedCallbackId == callbackId && verifiedCallbackId == callbackId
}

class CurfewPreferences(context: Context, private val store: DataStore<Preferences> = context.curfewPreferences) {
    private val callbackSelection = CallbackSelectionPreferences(store)

    val state: Flow<CurfewPreferenceState> = store.data.map { preferences ->
        CurfewPreferenceState(
            primaryAlarmDeviceId = preferences[PRIMARY_DEVICE],
            readinessAcknowledged = preferences[READINESS_ACKNOWLEDGED] ?: false,
            testSoundCompleted = preferences[TEST_SOUND_COMPLETED] ?: false,
            accountEnrolled = preferences[ACCOUNT_ENROLLED] ?: false,
            selectedCallbackId = CallbackSelectionPreferences.decode(preferences).selectedCallbackId,
            verifiedCallbackId = CallbackSelectionPreferences.decode(preferences).verifiedCallbackId,
            accountSignedIn = preferences[ACCOUNT_SIGNED_IN] ?: false,
        )
    }

    suspend fun setPrimaryAlarmDevice(deviceId: String) {
        require(deviceId.isNotBlank())
        store.edit { it[PRIMARY_DEVICE] = deviceId }
    }

    suspend fun acknowledgeLimitations() {
        store.edit { it[READINESS_ACKNOWLEDGED] = true }
    }

    suspend fun setTestSoundConfirmed(confirmed: Boolean) {
        store.edit { it[TEST_SOUND_COMPLETED] = confirmed }
    }

    suspend fun setAccountEnrolled(enrolled: Boolean) {
        store.edit { it[ACCOUNT_ENROLLED] = enrolled }
    }

    suspend fun setAccountSignedIn(signedIn: Boolean) {
        store.edit { it[ACCOUNT_SIGNED_IN] = signedIn }
    }

    suspend fun selectCallback(callbackId: String) = callbackSelection.select(callbackId)

    suspend fun markCallbackVerifiedIfSelected(callbackId: String): Boolean =
        callbackSelection.markVerifiedIfSelected(callbackId)

    suspend fun clearCallbackIfSelected(callbackId: String) = callbackSelection.clearIfSelected(callbackId)

    suspend fun nextWriterCounter(): Long {
        var next = 0L
        store.edit { preferences ->
            next = (preferences[WRITER_COUNTER] ?: 0L) + 1L
            preferences[WRITER_COUNTER] = next
        }
        return next
    }

    private companion object {
        val PRIMARY_DEVICE = stringPreferencesKey("primary_alarm_device")
        val READINESS_ACKNOWLEDGED = booleanPreferencesKey("readiness_acknowledged")
        val TEST_SOUND_COMPLETED = booleanPreferencesKey("alarm_sound_confirmed")
        val ACCOUNT_ENROLLED = booleanPreferencesKey("account_enrolled")
        val WRITER_COUNTER = longPreferencesKey("device_writer_counter")
        val ACCOUNT_SIGNED_IN = booleanPreferencesKey("account_signed_in")
    }
}
