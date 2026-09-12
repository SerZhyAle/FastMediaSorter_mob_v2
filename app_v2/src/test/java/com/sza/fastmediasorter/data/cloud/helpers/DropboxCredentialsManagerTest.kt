package com.sza.fastmediasorter.data.cloud.helpers

import android.content.Context
import com.sza.fastmediasorter.data.identity.transfer.TransferableSignInWriter
import com.sza.fastmediasorter.domain.identity.transfer.TransferableSignInProviderKeys
import com.sza.fastmediasorter.domain.identity.transfer.TransferableSignInRecord
import com.sza.fastmediasorter.domain.identity.transfer.TransferableSignInStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * S2115: covers the purge of credential entries left by the removed plaintext fallback.
 * S2101: covers the transferable secret entry the manager publishes beside each credential write.
 *
 * AndroidKeyStore is unavailable in Robolectric's JVM, so `EncryptedSharedPreferences.create(..)`
 * throws here - which is the very condition the purge must survive. The accessors below therefore
 * exercise the real broken-Keystore path: the purge runs, and the encrypted access that follows is
 * caught and degrades to "no credentials". The transferable record is unaffected by that, since it
 * is written through a store of its own.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34]) // Robolectric 4.16.1 maxSdkVersion=34; targetSdkVersion=36 would fail without this.
class DropboxCredentialsManagerTest {

    private lateinit var context: Context
    private lateinit var transferStore: FakeTransferableSignInStore

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        transferStore = FakeTransferableSignInStore()
        plainPrefs().edit().clear().commit()
    }

    @After
    fun tearDown() {
        plainPrefs().edit().clear().commit()
    }

    private fun plainPrefs() = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * A real [TransferableSignInWriter] over a fake store, so the read-merge-write the manager
     * relies on is genuinely exercised rather than stubbed away.
     */
    private fun buildManager(scope: CoroutineScope): DropboxCredentialsManager {
        val writer = TransferableSignInWriter(transferStore)
        return DropboxCredentialsManager(context, scope) { writer }
    }

    private fun dropboxEntries(): List<TransferableSignInRecord.Entry> =
        transferStore.record?.entries.orEmpty()
            .filter { it.providerKey == TransferableSignInProviderKeys.DROPBOX }

    @Test
    fun `purge removes plaintext credential entries left by the fallback`() = runTest(UnconfinedTestDispatcher()) {
        plainPrefs().edit()
            .putString(LEGACY_KEY, LEAKED_JSON)
            .putString("${PER_ACCOUNT_PREFIX}user@example.com", LEAKED_JSON)
            .commit()

        buildManager(this).loadStoredCredentials()

        val keys = plainPrefs().all.keys
        assertFalse("legacy plaintext key must be erased", keys.contains(LEGACY_KEY))
        assertFalse(
            "per-account plaintext key must be erased",
            keys.contains("${PER_ACCOUNT_PREFIX}user@example.com")
        )
    }

    @Test
    fun `purge keeps entries it does not own`() = runTest(UnconfinedTestDispatcher()) {
        plainPrefs().edit()
            .putString(LEGACY_KEY, LEAKED_JSON)
            .putString(UNRELATED_KEY, "keep me")
            .commit()

        buildManager(this).loadStoredCredentials()

        assertTrue("unrelated key must survive", plainPrefs().all.keys.contains(UNRELATED_KEY))
        assertEquals("keep me", plainPrefs().getString(UNRELATED_KEY, null))
    }

    @Test
    fun `purge runs once per manager instance`() = runTest(UnconfinedTestDispatcher()) {
        plainPrefs().edit().putString(LEGACY_KEY, LEAKED_JSON).commit()
        val manager = buildManager(this)
        manager.loadStoredCredentials()

        // A second plaintext entry appearing after the one-shot purge is not re-scanned.
        plainPrefs().edit().putString(LEGACY_KEY, LEAKED_JSON).commit()
        manager.clearStoredCredentials()

        assertTrue(plainPrefs().all.keys.contains(LEGACY_KEY))
    }

    // region - S2101 transferable secret

    @Test
    fun `saving credentials adds one secret entry under the Dropbox key`() = runTest(UnconfinedTestDispatcher()) {
        buildManager(this).saveCredentials(CREDENTIALS_A, ACCOUNT_A)

        val entries = dropboxEntries()
        assertEquals(1, entries.size)
        assertEquals(TransferableSignInRecord.Kind.SECRET, entries.single().kind)
        assertEquals(ACCOUNT_A, entries.single().payload[TransferredCredentialPayload.EMAIL])
        assertEquals(CREDENTIALS_A, entries.single().payload[TransferredCredentialPayload.CREDENTIALS])
    }

    @Test
    fun `a second account's save keeps the neighbouring entry`() = runTest(UnconfinedTestDispatcher()) {
        transferStore.record = seededRecordWithEnvelope()
        val manager = buildManager(this)

        manager.saveCredentials(CREDENTIALS_A, ACCOUNT_A)
        manager.saveCredentials(CREDENTIALS_B, ACCOUNT_B)

        assertNotNull("the identity envelope written before must survive", envelopeEntry())
        // One Dropbox entry, not two: the second save replaces its own provider's entry only.
        assertEquals(1, dropboxEntries().size)
        assertEquals(ACCOUNT_B, dropboxEntries().single().payload[TransferredCredentialPayload.EMAIL])
    }

    @Test
    fun `clearing removes only the Dropbox entry`() = runTest(UnconfinedTestDispatcher()) {
        transferStore.record = seededRecordWithEnvelope()
        val manager = buildManager(this)
        manager.saveCredentials(CREDENTIALS_A, ACCOUNT_A)

        manager.clearStoredCredentials()

        assertTrue("the Dropbox entry must be gone", dropboxEntries().isEmpty())
        assertNotNull("the identity envelope must stay", envelopeEntry())
        assertEquals("the record still has an entry, so it is not erased", 0, transferStore.clearCount)
    }

    @Test
    fun `clearing with specified account removes per-account and legacy keys`() = runTest(UnconfinedTestDispatcher()) {
        plainPrefs().edit()
            .putString(LEGACY_KEY, LEAKED_JSON)
            .putString("${PER_ACCOUNT_PREFIX}user@example.com", LEAKED_JSON)
            .putString("${PER_ACCOUNT_PREFIX}other@example.com", LEAKED_JSON)
            .commit()

        val manager = buildManager(this)
        manager.clearStoredCredentials("user@example.com")

        val keys = plainPrefs().all.keys
        assertFalse("legacy plaintext key must be erased", keys.contains(LEGACY_KEY))
        assertFalse("target per-account key must be erased", keys.contains("${PER_ACCOUNT_PREFIX}user@example.com"))
    }

    @Test
    fun `clearing without account removes all per-account keys`() = runTest(UnconfinedTestDispatcher()) {
        plainPrefs().edit()
            .putString(LEGACY_KEY, LEAKED_JSON)
            .putString("${PER_ACCOUNT_PREFIX}user1@example.com", LEAKED_JSON)
            .putString("${PER_ACCOUNT_PREFIX}user2@example.com", LEAKED_JSON)
            .commit()

        val manager = buildManager(this)
        manager.clearStoredCredentials()

        val keys = plainPrefs().all.keys
        assertFalse("legacy key must be erased", keys.contains(LEGACY_KEY))
        assertFalse("per-account key 1 must be erased", keys.contains("${PER_ACCOUNT_PREFIX}user1@example.com"))
        assertFalse("per-account key 2 must be erased", keys.contains("${PER_ACCOUNT_PREFIX}user2@example.com"))
    }

    @Test
    fun `a writer failure leaves the credential save successful`() = runTest(UnconfinedTestDispatcher()) {
        transferStore.failSave = true

        buildManager(this).saveCredentials(CREDENTIALS_A, ACCOUNT_A)

        // The store refused, so nothing was recorded for the migration - and the caller saw no
        // failure, which is the whole contract of the fire-and-forget mirror.
        assertNull(transferStore.record)
        assertTrue("the purge still ran, so the save path completed", plainPrefs().all.keys.isEmpty())
    }

    // endregion

    private fun seededRecordWithEnvelope(): TransferableSignInRecord =
        TransferableSignInRecord.empty(SEED_WRITTEN_AT).withEntry(
            TransferableSignInRecord.Entry(
                TransferableSignInProviderKeys.GOOGLE_PRIMARY,
                TransferableSignInRecord.Kind.IDENTITY_ENVELOPE,
                mapOf(TransferredCredentialPayload.EMAIL to ACCOUNT_A)
            )
        )

    private fun envelopeEntry(): TransferableSignInRecord.Entry? =
        transferStore.record?.entries?.firstOrNull {
            it.providerKey == TransferableSignInProviderKeys.GOOGLE_PRIMARY
        }

    /** In-memory stand-in for the platform-backed store, with a refusing-save mode. */
    private class FakeTransferableSignInStore : TransferableSignInStore {
        var record: TransferableSignInRecord? = null
        var failSave = false
        var clearCount = 0

        override suspend fun isAvailable(): Boolean = true

        override suspend fun save(record: TransferableSignInRecord): Boolean {
            if (failSave) {
                return false
            }
            this.record = record
            return true
        }

        override suspend fun readOnce(): TransferableSignInRecord? = record

        override suspend fun clear() {
            record = null
            clearCount++
        }
    }

    private companion object {
        const val PREFS_NAME = "dropbox_credentials"
        const val LEGACY_KEY = "credentials_json"
        const val PER_ACCOUNT_PREFIX = "credentials_json_"
        const val UNRELATED_KEY = "some_other_setting"
        const val LEAKED_JSON = """{"refresh_token":"leaked"}"""
        const val ACCOUNT_A = "first@example.com"
        const val ACCOUNT_B = "second@example.com"
        const val CREDENTIALS_A = """{"refresh_token":"first"}"""
        const val CREDENTIALS_B = """{"refresh_token":"second"}"""
        const val SEED_WRITTEN_AT = 1_700_000_000_000L
    }
}
