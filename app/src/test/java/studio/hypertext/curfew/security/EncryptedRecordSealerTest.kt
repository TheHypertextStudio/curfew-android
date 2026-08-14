package studio.hypertext.curfew.security

import java.time.Instant
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Test
import studio.hypertext.curfew.protocols.EncryptedRecordNamespace

class EncryptedRecordSealerTest {
    @Test
    fun `AES GCM record fields match the shared protocol golden vector`() {
        val rootKey = Base64.getUrlDecoder().decode(
            "ICEiIyQlJicoKSorLC0uLzAxMjM0NTY3ODk6Ozw9Pj8",
        )
        val nonce = Base64.getUrlDecoder().decode("oKGio6Slpqeoqaqr")
        val record = EncryptedRecordSealer { ByteArray(64) }.seal(
            plaintextCanonical = "{\"enabled\":true,\"maximumAttempts\":3}".toByteArray(),
            rootKey = rootKey,
            namespace = EncryptedRecordNamespace.Alarms,
            keyEpoch = 1,
            recordId = "018f4f45-4d34-7d98-a6c5-4de1bd63a21c",
            updatedAt = Instant.parse("2026-08-10T14:00:00Z"),
            version = 2,
            writerCounter = 5,
            writerDeviceId = "018f4f45-a055-7502-8b0c-7276bfe16c8f",
            nonce = nonce,
        )

        assertEquals("lb4wTNW2IMZvw-Edpk5hkvOTIBjDQZPhMguDRw5wO_8", record.aadDigest)
        assertEquals(
            "rAFROPRUKHtul1u9zY01tEnLqlGiA69cDs3fqY1F-VBomvh6u3283OdbNmbueEy0vWu6lg",
            record.ciphertext,
        )
        assertEquals("oKGio6Slpqeoqaqr", record.nonce)
        assertEquals(EncryptedRecordNamespace.Alarms, record.namespace)
    }
}
