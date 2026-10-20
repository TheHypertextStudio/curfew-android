package studio.hypertext.curfew.persistence

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import studio.hypertext.curfew.alarm.AlarmSetupDraft
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onStart

private val Context.firstUsePreferences by preferencesDataStore(name = "curfew_first_use")

enum class FirstUseChoice { STARTED, DEFERRED }

data class FirstUseState(val choice: FirstUseChoice? = null, val draft: AlarmSetupDraft? = null) {
    fun needsWelcome(hasSavedAlarm: Boolean): Boolean = !hasSavedAlarm && choice == null && draft == null
}

class FirstUsePreferences(private val store: DataStore<Preferences>) {
    constructor(context: Context) : this(context.applicationContext.firstUsePreferences)

    val state: Flow<FirstUseState> = store.data.map { preferences ->
        FirstUseState(FirstUseChoice.entries.firstOrNull { it.name == preferences[INTRODUCTION_CHOICE] }, decodeDraft(preferences))
    }

    val loadState: Flow<FirstUseLoadState> = state
        .map<FirstUseState, FirstUseLoadState> { FirstUseLoadState.Ready(it) }
        .onStart { emit(FirstUseLoadState.Loading) }
        .catch { failure ->
            if (failure is IOException) emit(FirstUseLoadState.Error) else throw failure
        }

    suspend fun chooseContinue() = choose(FirstUseChoice.STARTED)
    suspend fun chooseNotNow() = choose(FirstUseChoice.DEFERRED)

    suspend fun saveDraft(draft: AlarmSetupDraft) {
        store.edit {
            it[INTRODUCTION_CHOICE] = FirstUseChoice.STARTED.name
            it[DRAFT_ID] = draft.id
            it[DRAFT_TIME] = draft.wakeTime.toString()
            if (draft.onceDate != null) it[DRAFT_DATE] = draft.onceDate.toString() else it.remove(DRAFT_DATE)
            it[DRAFT_DAYS] = draft.weekdays.sortedBy { day -> day.value }.joinToString(",") { day -> day.value.toString() }
        }
    }

    suspend fun clearDraft() {
        store.edit {
            it.remove(DRAFT_ID)
            it.remove(DRAFT_TIME)
            it.remove(DRAFT_DATE)
            it.remove(DRAFT_DAYS)
        }
    }

    private fun decodeDraft(preferences: Preferences): AlarmSetupDraft? = runCatching {
        val id = preferences[DRAFT_ID] ?: return null
        val time = preferences[DRAFT_TIME] ?: return null
        val days = preferences[DRAFT_DAYS] ?: return null
        AlarmSetupDraft(
            id,
            LocalTime.parse(time),
            preferences[DRAFT_DATE]?.let(LocalDate::parse),
            if (days.isEmpty()) emptySet() else days.split(",").map { DayOfWeek.of(it.toInt()) }.toSet(),
        )
    }.getOrNull()

    private suspend fun choose(choice: FirstUseChoice) {
        store.edit { it[INTRODUCTION_CHOICE] = choice.name }
    }

    private companion object {
        // An introduction choice cannot stand in for live access, hearing, or alarm activation.
        val INTRODUCTION_CHOICE = stringPreferencesKey("introduction_choice_v1")
        val DRAFT_ID = stringPreferencesKey("setup_draft_id")
        val DRAFT_TIME = stringPreferencesKey("setup_draft_time")
        val DRAFT_DATE = stringPreferencesKey("setup_draft_date")
        val DRAFT_DAYS = stringPreferencesKey("setup_draft_days")
    }
}
