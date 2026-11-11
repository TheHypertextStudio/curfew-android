package studio.hypertext.curfew

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import studio.hypertext.curfew.sync.WakeSyncScheduler

/**
 * Starts process-wide Curfew services after Android creates the application. Wake sync uses
 * unique periodic work; alarm notification channels are deferred until alarm setup or
 * playback.
 */
@HiltAndroidApp
class CurfewApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        WakeSyncScheduler.install(this)
    }
}
