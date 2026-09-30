package com.sza.fastmediasorter.data.identity.transfer

import com.sza.fastmediasorter.domain.identity.transfer.TransferableSignInRecord
import com.sza.fastmediasorter.domain.identity.transfer.TransferableSignInStore
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TransferableSignInWriterTest {

    /** Suspends inside every read so that an unlocked read-merge-write interleaves deterministically. */
    private class SlowStore : TransferableSignInStore {
        var record: TransferableSignInRecord? = null

        override suspend fun isAvailable(): Boolean = true

        override suspend fun save(record: TransferableSignInRecord): Boolean {
            this.record = record
            return true
        }

        override suspend fun readOnce(): TransferableSignInRecord? {
            val snapshot = record
            delay(READ_DELAY_MS)
            return snapshot
        }

        override suspend fun clear() {
            record = null
        }
    }

    @Test
    fun `concurrent puts of two providers keep both entries`() = runTest {
        val store = SlowStore()
        val writer = TransferableSignInWriter(store)

        listOf(
            async { writer.putEntry("dropbox", TransferableSignInRecord.Kind.SECRET, mapOf("t" to "1")) },
            async { writer.putEntry("gdrive", TransferableSignInRecord.Kind.SECRET, mapOf("t" to "2")) }
        ).awaitAll()

        val keys = store.record?.entries?.map { it.providerKey }?.toSet()
        assertEquals(setOf("dropbox", "gdrive"), keys)
    }

    @Test
    fun `concurrent put and remove of different providers do not lose the put`() = runTest {
        val store = SlowStore()
        val writer = TransferableSignInWriter(store)
        writer.putEntry("dropbox", TransferableSignInRecord.Kind.SECRET, mapOf("t" to "1"))

        listOf(
            async { writer.putEntry("gdrive", TransferableSignInRecord.Kind.SECRET, mapOf("t" to "2")) },
            async { writer.removeEntry("dropbox") }
        ).awaitAll()

        val keys = store.record?.entries?.map { it.providerKey }?.toSet()
        assertEquals(setOf("gdrive"), keys)
    }

    @Test
    fun `removing the last entry clears the record`() = runTest {
        val store = SlowStore()
        val writer = TransferableSignInWriter(store)
        writer.putEntry("dropbox", TransferableSignInRecord.Kind.SECRET, mapOf("t" to "1"))

        writer.removeEntry("dropbox")

        assertNull(store.record)
    }

    private companion object {
        const val READ_DELAY_MS = 50L
    }
}
