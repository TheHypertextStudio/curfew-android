package studio.hypertext.curfew.persistence

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.map

data class CallbackSelectionState(val selectedCallbackId: String?, val verifiedCallbackId: String?) {
    val ready: Boolean get() = selectedCallbackId != null && selectedCallbackId == verifiedCallbackId
}

class CallbackSelectionPreferences(private val dataStore: DataStore<Preferences>) {
    val state = dataStore.data.map(::decode)

    suspend fun select(callbackId: String) {
        require(callbackId.isNotBlank())
        dataStore.edit {
            it[SELECTED_CALLBACK] = callbackId
            it.remove(VERIFIED_CALLBACK)
        }
    }

    suspend fun markVerifiedIfSelected(callbackId: String): Boolean {
        var accepted = false
        dataStore.edit {
            if (it[SELECTED_CALLBACK] == callbackId) {
                it[VERIFIED_CALLBACK] = callbackId
                accepted = true
            }
        }
        return accepted
    }

    suspend fun clearIfSelected(callbackId: String) {
        dataStore.edit {
            if (it[SELECTED_CALLBACK] == callbackId) {
                it.remove(SELECTED_CALLBACK)
                it.remove(VERIFIED_CALLBACK)
            }
        }
    }

    companion object {
        private val SELECTED_CALLBACK = stringPreferencesKey("selected_callback")
        private val VERIFIED_CALLBACK = stringPreferencesKey("verified_callback")
        fun decode(preferences: Preferences) = CallbackSelectionState(
            preferences[SELECTED_CALLBACK], preferences[VERIFIED_CALLBACK],
        )
    }
}
