package studio.hypertext.curfew.persistence

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FirstUseLoadingTest {
    @Test fun `loading precedes a successful fresh install read`() = runBlocking {
        val values = FirstUsePreferences(FakeStore { emit(emptyPreferences()) }).loadState.take(2).toList()
        assertEquals(FirstUseLoadState.Loading, values[0])
        assertEquals(FirstUseLoadState.Ready(FirstUseState()), values[1])
    }

    @Test fun `read failure never becomes fresh installation`() = runBlocking {
        val values = FirstUsePreferences(FakeStore { throw IOException("private storage details") }).loadState.toList()
        assertEquals(listOf(FirstUseLoadState.Loading, FirstUseLoadState.Error), values)
    }

    @Test fun `corrupt storage releases loading into explicit error`() = runBlocking {
        val values = FirstUsePreferences(FakeStore { throw CorruptionException("private corruption details") }).loadState.toList()
        assertEquals(FirstUseLoadState.Error, values.last())
    }

    @Test fun `retry reads the same store without wiping saved setup`() = runBlocking {
        var reads = 0
        var writes = 0
        val store = object : DataStore<Preferences> {
            override val data: Flow<Preferences> = flow {
                reads++
                if (reads == 1) throw IOException("temporarily unavailable")
                emit(emptyPreferences())
            }
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
                writes++
                return transform(emptyPreferences())
            }
        }
        val preferences = FirstUsePreferences(store)
        assertEquals(FirstUseLoadState.Error, preferences.loadState.toList().last())
        assertTrue(preferences.loadState.take(2).toList().last() is FirstUseLoadState.Ready)
        assertEquals(0, writes)
    }

    private class FakeStore(val source: suspend kotlinx.coroutines.flow.FlowCollector<Preferences>.() -> Unit) : DataStore<Preferences> {
        override val data: Flow<Preferences> = flow(source)
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = error("Read failure must not write")
    }
}
