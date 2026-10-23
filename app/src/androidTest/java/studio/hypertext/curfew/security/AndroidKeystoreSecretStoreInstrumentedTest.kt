package studio.hypertext.curfew.security

import android.content.Context
import android.content.ContextWrapper
import java.security.KeyStore
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import studio.hypertext.curfew.account.NativeDeviceProofAuthenticator
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

    @Test
    fun simultaneousStoresKeepAllSecretsReadableWithOneFreshKeyAlias() {
        val workers = Executors.newFixedThreadPool(2)
        try {
            repeat(12) { round ->
                val alias = "curfew-test-concurrent-${UUID.randomUUID()}"
                val stores = List(2) { AndroidKeystoreSecretStore(context, alias) }
                val ids = List(2) { "concurrent-$round-${UUID.randomUUID()}" }
                val start = CountDownLatch(1)
                try {
                    val writes = stores.mapIndexed { index, store -> workers.submit {
                        check(start.await(10, TimeUnit.SECONDS))
                        store.put(ids[index], byteArrayOf(index.toByte(), 42))
                    } }
                    start.countDown()
                    writes.forEach { it.get(20, TimeUnit.SECONDS) }
                    stores.indices.forEach { index ->
                        assertArrayEquals(byteArrayOf(index.toByte(), 42), stores[index].get(ids[index]))
                    }
                } finally {
                    ids.forEach { stores.first().delete(it) }
                    KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias)
                }
            }
        } finally { workers.shutdownNow() }
    }

    @Test
    fun simultaneousAuthenticatorsReturnTheSamePersistedDeviceIdentity() {
        val directory = context.noBackupFilesDir.resolve("identity-race-${UUID.randomUUID()}").apply { mkdirs() }
        val isolated = object : ContextWrapper(context) {
            override fun getNoBackupFilesDir() = directory
        }
        val workers = Executors.newFixedThreadPool(2)
        try {
            val start = CountDownLatch(1)
            val reads = List(2) { workers.submit<String> {
                check(start.await(10, TimeUnit.SECONDS))
                NativeDeviceProofAuthenticator(isolated).deviceId()
            } }
            start.countDown()
            val ids = reads.map { it.get(20, TimeUnit.SECONDS) }
            assertEquals("Concurrent startup must agree on the local device identity", ids[0], ids[1])
            assertEquals(ids[0], NativeDeviceProofAuthenticator(isolated).deviceId())
        } finally {
            workers.shutdownNow()
            directory.deleteRecursively()
        }
    }

}
