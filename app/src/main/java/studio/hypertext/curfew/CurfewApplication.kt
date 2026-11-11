package studio.hypertext.curfew

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import studio.hypertext.curfew.sync.WakeSyncScheduler

@HiltAndroidApp
class CurfewApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        WakeSyncScheduler.install(this)
    }
}
