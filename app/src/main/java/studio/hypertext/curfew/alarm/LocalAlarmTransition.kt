package studio.hypertext.curfew.alarm

import kotlinx.coroutines.CancellationException

internal class LocalAlarmTransition(
    private val applyLocal: suspend () -> Unit,
    private val finishLocal: () -> Unit = {},
    private val publish: suspend () -> Unit,
    private val onPublicationFailure: (Exception) -> Unit,
) {
    suspend fun deliver() {
        try {
            applyLocal()
        } finally {
            finishLocal()
        }
        try {
            publish()
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: Exception) {
            onPublicationFailure(failure)
        }
    }
}
