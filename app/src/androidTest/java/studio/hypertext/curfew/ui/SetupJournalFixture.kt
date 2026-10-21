package studio.hypertext.curfew.ui

import android.content.Context
import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import studio.hypertext.curfew.persistence.CurfewPreferences
import studio.hypertext.curfew.persistence.FirstUsePreferences
import studio.hypertext.curfew.platform.SunriseScheduler
import studio.hypertext.curfew.platform.AlarmRuntimeRepository

/** Keep a cold-start test independent of completed schedules and prior introduction choices. */
internal class SetupJournalFixture(private val context: Context) {
    private var snapshots = emptyList<Pair<DataStore<Preferences>, Preferences>>()

    suspend fun isolate() {
        assumeTrue("Setup journal isolation requires a disposable emulator or isolated acceptance app",
            Build.HARDWARE in setOf("ranchu", "goldfish") || context.packageName.endsWith(".acceptance"))
        val scheduler = SunriseScheduler(context)
        assertNull("Refuse to remove an outstanding alarm occurrence", scheduler.state.first()?.occurrence)
        assertTrue("Refuse to reset setup while a campaign remains pending", AlarmRuntimeRepository(context).pending().isEmpty())
        val alarmPreferences = field(scheduler, "preferences")
        val stores = listOf(
            store(alarmPreferences, "dataStore"),
            store(FirstUsePreferences(context), "store"),
            store(CurfewPreferences(context), "store"),
        )
        snapshots = stores.map { it to it.data.first() }
        stores.forEach { it.updateData { emptyPreferences() } }
        assertNull("Cold setup requires no saved alarm", scheduler.state.first())
    }

    suspend fun restore() {
        snapshots.asReversed().forEach { (store, snapshot) -> store.updateData { snapshot } }
        snapshots = emptyList()
    }

    // Use the existing singleton journals; a second DataStore for the same file is invalid.
    // Keep reset access in instrumentation instead of adding a destructive production API.
    @Suppress("UNCHECKED_CAST")
    private fun store(owner: Any, name: String) = field(owner, name) as DataStore<Preferences>

    private fun field(owner: Any, name: String): Any = owner.javaClass.getDeclaredField(name)
        .apply { isAccessible = true }.get(owner)
}
