package studio.hypertext.curfew.alarm

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Serializes process-wide changes to the Sunrise schedule and campaign bindings. Both Sunrise
 * and place check-ins use the same mutex so revocation or approval cannot race a schedule
 * write.
 */
internal object SunriseScheduleLock {
    val mutex = Mutex()

    suspend fun <T> serialized(action: suspend () -> T): T = mutex.withLock { action() }
}
