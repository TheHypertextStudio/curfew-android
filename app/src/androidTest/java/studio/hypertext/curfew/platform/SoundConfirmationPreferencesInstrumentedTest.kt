package studio.hypertext.curfew.platform

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import studio.hypertext.curfew.persistence.CurfewPreferences

class SoundConfirmationPreferencesInstrumentedTest {
    @Test fun legacyAutomaticPlaybackFlagDoesNotCountAsHearing() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = context.cacheDir.resolve("sound-${UUID.randomUUID()}.preferences_pb")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
        try {
            store.edit { it[booleanPreferencesKey("test_sound_completed")] = true }
            val preferences = CurfewPreferences(context, store)
            assertFalse(preferences.state.first().testSoundCompleted)
            preferences.setTestSoundConfirmed(true)
            assertTrue(preferences.state.first().testSoundCompleted)
        } finally {
            scope.cancel()
            file.delete()
        }
    }
}
