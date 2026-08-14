package studio.hypertext.curfew.security

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidKeystoreSecretStoreInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun callbackAndAccountSecretsAreEncryptedOutsideBackupStorage() {
        val store = AndroidKeystoreSecretStore(
            context = context,
            keyAlias = "curfew-test-${System.nanoTime()}",
        )
        val secret = ByteArray(32) { it.toByte() }

        store.put("callback-1", secret)

        assertArrayEquals(secret, store.get("callback-1"))
        store.delete("callback-1")
        assertNull(store.get("callback-1"))
    }
}
