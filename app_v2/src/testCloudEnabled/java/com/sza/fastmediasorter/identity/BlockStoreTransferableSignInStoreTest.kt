package com.sza.fastmediasorter.identity

import com.sza.fastmediasorter.data.identity.transfer.TransferableSignInRecordCodec
import com.sza.fastmediasorter.domain.identity.transfer.TransferableSignInProviderKeys
import com.sza.fastmediasorter.domain.identity.transfer.TransferableSignInRecord
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract of the Block Store backed store (S2101).
 *
 * The fault-injection cases carry the weight: the port promises that nothing throws, because a
 * restoration that surfaces an error on the first launch after a migration is worse than one that
 * quietly does not happen.
 */
class BlockStoreTransferableSignInStoreTest {

    private val codec = TransferableSignInRecordCodec()

    @Test
    fun `save then readOnce returns an equal record`() = runTest {
        val gateway = FakeBlockStoreGateway()
        val store = storeWith(gateway)
        val record = recordWith(envelopeEntry())

        assertTrue(store.save(record))
        assertEquals(record, store.readOnce())
    }

    @Test
    fun `readOnce on an empty store returns null`() = runTest {
        assertNull(storeWith(FakeBlockStoreGateway()).readOnce())
    }

    @Test
    fun `a gateway failure degrades to the contract failure value on every operation`() = runTest {
        val gateway = FakeBlockStoreGateway(failEverything = true)
        val store = storeWith(gateway)

        assertFalse(store.isAvailable())
        assertFalse(store.save(recordWith(envelopeEntry())))
        assertNull(store.readOnce())
        store.clear()
    }

    @Test
    fun `an oversized record is refused rather than truncated`() = runTest {
        val gateway = FakeBlockStoreGateway()
        val store = storeWith(gateway)
        val oversized = recordWith(envelopeEntry().copy(payload = mapOf("blob" to "x".repeat(OVERSIZED_PAYLOAD))))

        assertFalse(store.save(oversized))
        assertNull(gateway.stored)
    }

    @Test
    fun `cloud backup is requested only when end-to-end encryption is available`() = runTest {
        val encrypted = FakeBlockStoreGateway(endToEndEncryption = true)
        storeWith(encrypted).save(recordWith(envelopeEntry()))
        assertTrue(encrypted.lastBackupToCloud == true)

        val plain = FakeBlockStoreGateway(endToEndEncryption = false)
        storeWith(plain).save(recordWith(envelopeEntry()))
        assertFalse(plain.lastBackupToCloud == true)
    }

    @Test
    fun `clear removes the stored record`() = runTest {
        val gateway = FakeBlockStoreGateway()
        val store = storeWith(gateway)
        store.save(recordWith(envelopeEntry()))

        store.clear()

        assertNull(gateway.stored)
        assertNull(store.readOnce())
    }

    private fun storeWith(gateway: BlockStoreGateway) =
        BlockStoreTransferableSignInStore(gateway, codec, UnconfinedTestDispatcher())

    private fun recordWith(entry: TransferableSignInRecord.Entry) = TransferableSignInRecord(
        schemaVersion = TransferableSignInRecord.SCHEMA_VERSION,
        writtenAt = WRITTEN_AT,
        entries = listOf(entry)
    )

    private fun envelopeEntry() = TransferableSignInRecord.Entry(
        providerKey = TransferableSignInProviderKeys.GOOGLE_PRIMARY,
        kind = TransferableSignInRecord.Kind.IDENTITY_ENVELOPE,
        payload = mapOf("email" to "user@example.com")
    )

    private class FakeBlockStoreGateway(
        private val failEverything: Boolean = false,
        private val endToEndEncryption: Boolean = true
    ) : BlockStoreGateway {

        var stored: ByteArray? = null
        var lastBackupToCloud: Boolean? = null

        override suspend fun isClientAvailable(): Boolean = guard { true }

        override suspend fun isEndToEndEncryptionAvailable(): Boolean = guard { endToEndEncryption }

        override suspend fun storeBytes(key: String, bytes: ByteArray, backupToCloud: Boolean) = guard {
            stored = bytes
            lastBackupToCloud = backupToCloud
        }

        override suspend fun retrieveBytes(key: String): ByteArray? = guard { stored }

        override suspend fun deleteBytes(key: String) = guard { stored = null }

        private fun <T> guard(block: () -> T): T {
            check(!failEverything) { "Block Store unavailable" }
            return block()
        }
    }

    private companion object {
        const val WRITTEN_AT = 1_756_800_000_000L

        /** Comfortably past the 4 KB Block Store entry limit the store refuses at. */
        const val OVERSIZED_PAYLOAD = 5000
    }
}
