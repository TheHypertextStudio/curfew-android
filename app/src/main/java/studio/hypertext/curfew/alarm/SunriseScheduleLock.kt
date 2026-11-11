package studio.hypertext.curfew.alarm

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal object SunriseScheduleLock {
    val mutex = Mutex()

    suspend fun <T> serialized(action: suspend () -> T): T = mutex.withLock { action() }
}
