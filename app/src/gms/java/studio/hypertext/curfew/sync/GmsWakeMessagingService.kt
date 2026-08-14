package studio.hypertext.curfew.sync

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class GmsWakeMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        if (message.data["kind"] == "wake-record-available") {
            WakeSyncScheduler.accelerate(this)
            message.data["campaignId"]?.let {
                WakeStatusPollScheduler.schedule(this, it)
            }
        }
    }
}
