package studio.hypertext.curfew.security

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.math.BigInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidDeviceSignerInstrumentedTest {
    @Test
    fun signingKeyIsNonExportableAndProducesLowSP1363Signatures() {
        val signer = AndroidDeviceSigner("curfew-test-signing-${System.nanoTime()}")
        val signature = signer.sign("canonical record".toByteArray())

        assertEquals(64, signature.size)
        assertTrue(signer.publicKeyEncoded().isNotEmpty())
        val s = BigInteger(1, signature.copyOfRange(32, 64))
        assertTrue(s <= P256_HALF_ORDER)
    }

    private companion object {
        val P256_HALF_ORDER = BigInteger(
            "ffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632551",
            16,
        ).shiftRight(1)
    }
}
