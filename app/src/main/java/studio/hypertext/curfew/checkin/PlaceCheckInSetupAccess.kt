package studio.hypertext.curfew.checkin

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
