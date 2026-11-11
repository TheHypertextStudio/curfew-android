package studio.hypertext.curfew.checkin

/**
 * Functional interface abstracting camera hardware and permission availability.
 * Decouples check-in components from Android platform permission APIs.
 */
fun interface CameraAvailabilityChecker {
    fun isCameraReady(): Boolean
}
