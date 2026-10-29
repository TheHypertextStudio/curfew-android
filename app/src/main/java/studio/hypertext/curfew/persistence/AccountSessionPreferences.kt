package studio.hypertext.curfew.persistence

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.map
import studio.hypertext.curfew.config.ProductionServices

data class AccountSessionState(
    val signedIn: Boolean,
    val enrolled: Boolean,
) {
    val needsEnrollment: Boolean get() = !signedIn || !enrolled
}

/** A retired issuer's flags stay stored but never authorize the current issuer. */
class AccountSessionPreferences(
    private val store: DataStore<Preferences>,
) {
    val state = store.data.map(::decode)

    suspend fun setSignedIn(signedIn: Boolean) {
        store.edit {
            it[SIGNED_IN] = signedIn
            it[ENROLLED] = false
        }
    }

    suspend fun <T> completeSignIn(recover: suspend () -> T): T {
        setSignedIn(false)
        val recovered = recover()
        setSignedIn(true)
        return recovered
    }

    suspend fun setEnrolled(enrolled: Boolean) {
        store.edit { it[ENROLLED] = enrolled && it[SIGNED_IN] == true }
    }

    companion object {
        private val SIGNED_IN = booleanPreferencesKey("account_signed_in:${ProductionServices.ACCOUNT_ORIGIN}")
        private val ENROLLED = booleanPreferencesKey("account_enrolled:${ProductionServices.ACCOUNT_ORIGIN}")

        fun decode(preferences: Preferences): AccountSessionState {
            val signedIn = preferences[SIGNED_IN] == true
            return AccountSessionState(signedIn, signedIn && preferences[ENROLLED] == true)
        }
    }
}
