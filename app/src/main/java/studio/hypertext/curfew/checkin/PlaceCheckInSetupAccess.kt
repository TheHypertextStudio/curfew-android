package studio.hypertext.curfew.checkin

/**
 * Operations needed by the place-code setup screens: read, create, export, enroll, and revoke
 * a code. It contains no alarm-release operation or Android UI handle.
 */
interface PlaceCheckInSetupAccess {
    fun cameraReady(): Boolean
    suspend fun draft(): PlaceDraft?
    suspend fun begin(label: String): PlaceDraft
    suspend fun find(id: String): PlaceEnrollment?
    suspend fun markExported(id: String)
    suspend fun cancelDraft()
    suspend fun enroll(draftId: String, scanned: String): PlaceEnrollment
    suspend fun remove(id: String)
}

/**
 * Approves one scanned place code against one campaign. Keeping this separate from setup
 * prevents the ringing UI from changing the saved place-code configuration.
 */
internal fun interface PlaceCheckInApprover {
    suspend fun approve(campaignId: String, code: String): PlaceApprovalResult
}
