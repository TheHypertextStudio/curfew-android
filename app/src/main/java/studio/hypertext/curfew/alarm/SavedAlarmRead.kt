package studio.hypertext.curfew.alarm

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import studio.hypertext.curfew.persistence.FirstUseChoice
import studio.hypertext.curfew.persistence.FirstUseLoadState

/**
 * Outcome of reading the saved Sunrise alarm at launch. A failed read stays distinct from "no
 * alarm" so setup never starts again on top of an alarm Curfew could not read.
 */
internal sealed interface SavedAlarmRead {
    /** The read finished; [alarm] is null only when nothing is saved. */
    data class Loaded(val alarm: SavedSunriseAlarm?) : SavedAlarmRead

    /** The saved alarm could not be read, so its presence is unknown. */
    data object Failed : SavedAlarmRead
}

/**
 * Maps saved-alarm emissions to [SavedAlarmRead] and turns a read failure into one
 * [SavedAlarmRead.Failed] emission that ends the flow. Re-collecting retries the read.
 */
internal fun Flow<SavedSunriseAlarm?>.asSavedAlarmRead(): Flow<SavedAlarmRead> =
    map<SavedSunriseAlarm?, SavedAlarmRead> { SavedAlarmRead.Loaded(it) }
        .catch { emit(SavedAlarmRead.Failed) }

/**
 * Chooses the cold-launch entry from the saved alarm read and the first-use preferences. Either
 * read failing shows the retry screen, except that a readable saved alarm outranks a broken
 * first-use record because the alarm alone decides that setup is finished.
 */
internal fun alarmSetupEntry(read: SavedAlarmRead, load: FirstUseLoadState): AlarmSetupEntry {
    val known = read as? SavedAlarmRead.Loaded
    val alarm = known?.alarm
    val firstUse = (load as? FirstUseLoadState.Ready)?.state
    return AlarmSetupEntry(
        loaded = load !is FirstUseLoadState.Loading,
        loadError = known == null || (load is FirstUseLoadState.Error && alarm == null),
        showWelcome = known != null && firstUse?.needsWelcome(alarm != null) == true,
        startSetup = known != null && alarm == null && firstUse?.choice == FirstUseChoice.STARTED,
        draft = firstUse?.draft.takeIf { known != null && alarm == null },
    )
}
