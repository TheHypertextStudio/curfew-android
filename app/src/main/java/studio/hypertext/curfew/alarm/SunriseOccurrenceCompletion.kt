package studio.hypertext.curfew.alarm

class SunriseOccurrenceCompletion(
    private val consume: suspend (String) -> Unit,
    private val scheduleNext: suspend () -> Unit,
    private val cancelTransition: (String) -> Unit,
    private val cancelPolling: (String) -> Unit,
) {
    suspend fun complete(campaignId: String) {
        // A process can die after the terminal outcome commits. Retain its old
        // clock and polling triggers until the next schedule is durable so a
        // later transition can finish that interrupted operation.
        consume(campaignId)
        scheduleNext()
        cancelPolling(campaignId)
        cancelTransition(campaignId)
    }
}
