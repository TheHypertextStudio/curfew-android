package studio.hypertext.curfew.platform

/**
 * Interface abstracting the device boot identifier.
 * Decouples runtime repositories from android.provider.Settings.
 */
fun interface BootIdProvider {
    fun currentBootId(): String
}
