package studio.hypertext.curfew.enforcement

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.enforcementDataStore by preferencesDataStore(name = "curfew_enforcement")

class EnforcementPreferences(
    private val dataStore: DataStore<Preferences>,
) {
    constructor(context: Context) : this(context.applicationContext.enforcementDataStore)
    val sessionFlow: Flow<EnforcementSession?> = dataStore.data.map(::decodeSession)
    val chosenAppsFlow: Flow<Set<String>> = dataStore.data.map { it[CONFIG_CHOSEN_APPS] ?: emptySet() }
    val consentRevisionFlow: Flow<Int> = dataStore.data.map { it[CONFIG_CONSENT_REVISION] ?: 0 }

    suspend fun readSession(): EnforcementSession? = sessionFlow.first()

    suspend fun saveSession(session: EnforcementSession) {
        dataStore.edit { prefs ->
            prefs[SESSION_ID] = session.sessionId
            prefs[PURPOSE] = session.purpose.name
            prefs[START_EPOCH_MS] = session.startEpochMs
            prefs[ELAPSED_START_MS] = session.elapsedRealtimeStartMs
            prefs[DURATION_MINUTES] = session.durationMinutes
            prefs[WALL_CLOCK_EXPIRY_MS] = session.wallClockExpiryEpochMs
            prefs[CHOSEN_APPS] = session.chosenAppPackages
            prefs[SESSION_CONSENT_REVISION] = session.consentRevision
            prefs[SESSION_STATE] = session.state.name
        }
    }

    suspend fun emergencyStop(reason: String? = null) {
        dataStore.edit { prefs ->
            val currentState = prefs[SESSION_STATE]
            if (currentState == EnforcementSessionState.ACTIVE.name) {
                prefs[SESSION_STATE] = EnforcementSessionState.CANCELLED_EMERGENCY.name
            }
        }
    }

    suspend fun disarmOnReboot() {
        dataStore.edit { prefs ->
            val currentState = prefs[SESSION_STATE]
            if (currentState == EnforcementSessionState.ACTIVE.name) {
                prefs[SESSION_STATE] = EnforcementSessionState.DISARMED_AFTER_REBOOT.name
            }
        }
    }

    suspend fun readConsentRevision(): Int = consentRevisionFlow.first()

    suspend fun setConsentRevision(revision: Int) {
        dataStore.edit { it[CONFIG_CONSENT_REVISION] = revision }
    }

    suspend fun readChosenApps(): Set<String> = chosenAppsFlow.first()

    suspend fun setChosenApps(apps: Set<String>) {
        dataStore.edit { it[CONFIG_CHOSEN_APPS] = apps }
    }

    private fun decodeSession(prefs: Preferences): EnforcementSession? {
        val id = prefs[SESSION_ID] ?: return null
        val purposeStr = prefs[PURPOSE] ?: return null
        val startEpoch = prefs[START_EPOCH_MS] ?: return null
        val elapsedStart = prefs[ELAPSED_START_MS] ?: return null
        val duration = prefs[DURATION_MINUTES] ?: return null
        val expiry = prefs[WALL_CLOCK_EXPIRY_MS] ?: return null
        val apps = prefs[CHOSEN_APPS] ?: emptySet()
        val consent = prefs[SESSION_CONSENT_REVISION] ?: 0
        val stateStr = prefs[SESSION_STATE] ?: return null

        val purpose = runCatching { EnforcementPurpose.valueOf(purposeStr) }.getOrNull() ?: return null
        val state = runCatching { EnforcementSessionState.valueOf(stateStr) }.getOrNull() ?: return null

        return EnforcementSession(
            sessionId = id,
            purpose = purpose,
            startEpochMs = startEpoch,
            elapsedRealtimeStartMs = elapsedStart,
            durationMinutes = duration,
            wallClockExpiryEpochMs = expiry,
            chosenAppPackages = apps,
            consentRevision = consent,
            state = state,
        )
    }

    companion object {
        private val SESSION_ID = stringPreferencesKey("session_id")
        private val PURPOSE = stringPreferencesKey("purpose")
        private val START_EPOCH_MS = longPreferencesKey("start_epoch_ms")
        private val ELAPSED_START_MS = longPreferencesKey("elapsed_start_ms")
        private val DURATION_MINUTES = intPreferencesKey("duration_minutes")
        private val WALL_CLOCK_EXPIRY_MS = longPreferencesKey("wall_clock_expiry_ms")
        private val CHOSEN_APPS = stringSetPreferencesKey("chosen_apps")
        private val SESSION_CONSENT_REVISION = intPreferencesKey("session_consent_revision")
        private val SESSION_STATE = stringPreferencesKey("session_state")

        private val CONFIG_CHOSEN_APPS = stringSetPreferencesKey("config_chosen_apps")
        private val CONFIG_CONSENT_REVISION = intPreferencesKey("config_consent_revision")
    }
}
