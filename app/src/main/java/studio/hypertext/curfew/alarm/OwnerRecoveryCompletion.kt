package studio.hypertext.curfew.alarm

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

sealed interface OwnerRecoveryResult {
    data object Recovered : OwnerRecoveryResult
    data object RecoveredNextAlarmUnavailable : OwnerRecoveryResult
    data object NotActive : OwnerRecoveryResult
    data object NotSaved : OwnerRecoveryResult
}

class OwnerRecoveryCompletion(
    private val save: suspend (AuthorizedOwnerRecovery) -> Boolean,
    private val finishOccurrence: suspend (String) -> Unit,
) {
    suspend fun complete(approval: AuthorizedOwnerRecovery): OwnerRecoveryResult = withContext(NonCancellable) {
        // Authentication already authorized this local transaction. Closing its
        // Activity must not abandon the saved outcome or tomorrow's scheduling.
        val saved = try {
            save(approval)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return@withContext OwnerRecoveryResult.NotSaved
        }
        if (!saved) return@withContext OwnerRecoveryResult.NotActive
        try {
            finishOccurrence(approval.campaignId)
            OwnerRecoveryResult.Recovered
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            OwnerRecoveryResult.RecoveredNextAlarmUnavailable
        }
    }
}
