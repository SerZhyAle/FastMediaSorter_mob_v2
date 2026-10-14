package com.sza.fastmediasorter.wear.data.exchange

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/** S4158: the watch's entry lifecycle - items AA, AB and AD as the watch applies them, and the outgoing mark. */
class WearExchangeAccountRepositoryTest {

    private val store = MemoryStore()
    private val client = mockk<WearExchangeAccountClient>()
    private val profile = WearExchangeDeviceProfile("Galaxy Watch", "2.6.0")
    private val repository = WearExchangeAccountRepository(client, store, profile)

    @Test
    fun `the first entry is enrolled with a fresh id, stored with its token and made outgoing`() = runBlocking {
        val sent = slot<WearExchangeEntry>()
        every { client.enroll(capture(sent), CODE, profile) } returns WearExchangeEnrollResult.Enrolled(TOKEN)

        val result = repository.enroll(join())

        assertTrue(result is WearExchangeEnrollResult.Enrolled)
        val held = store.all().single()
        assertEquals(sent.captured.deviceId, held.deviceId)
        assertEquals(PIN, held.pinnedCertificate)
        assertTrue(held.outgoing)
        assertEquals(TOKEN, store.token(held.identity))
    }

    @Test
    fun `the same identity re-enrolls with its id and keeps its mark`() = runBlocking {
        store.put(WearExchangeEntry(HOST, PORT, "OWNER", HELD_ID, null, outgoing = false))
        store.put(WearExchangeEntry("other.example.net", PORT, LOGIN, "BBBB", null, outgoing = true))
        val sent = slot<WearExchangeEntry>()
        every { client.enroll(capture(sent), CODE, profile) } returns WearExchangeEnrollResult.Enrolled(TOKEN)

        repository.enroll(join())

        assertEquals(HELD_ID, sent.captured.deviceId)
        assertEquals(2, store.all().size)
        val renewed = store.all().first { it.deviceId == HELD_ID }
        assertFalse(renewed.outgoing)
        assertEquals(PIN, renewed.pinnedCertificate)
    }

    @Test
    fun `a second entry joins the list without taking the mark`() = runBlocking {
        store.put(WearExchangeEntry("other.example.net", PORT, LOGIN, "BBBB", null, outgoing = true))
        every { client.enroll(any(), CODE, profile) } returns WearExchangeEnrollResult.Enrolled(TOKEN)

        repository.enroll(join())

        assertEquals(1, store.all().count { it.outgoing })
        assertEquals("BBBB", store.all().single { it.outgoing }.deviceId)
    }

    @Test
    fun `an id collision regenerates the fresh id once`() = runBlocking {
        val ids = mutableListOf<String>()
        every { client.enroll(any(), CODE, profile) } answers {
            ids += firstArg<WearExchangeEntry>().deviceId
            if (ids.size == 1) {
                WearExchangeEnrollResult.Refused(WearExchangeEnvelope.REASON_ID_COLLISION)
            } else {
                WearExchangeEnrollResult.Enrolled(TOKEN)
            }
        }

        repository.enroll(join())

        assertEquals(2, ids.size)
        assertNotEquals(ids[0], ids[1])
        assertEquals(ids[1], store.all().single().deviceId)
    }

    @Test
    fun `a refusal stores nothing`() = runBlocking {
        every { client.enroll(any(), CODE, profile) } returns WearExchangeEnrollResult.Refused("bad-credentials")

        assertEquals(WearExchangeEnrollResult.Refused("bad-credentials"), repository.enroll(join()))
        assertTrue(store.all().isEmpty())
    }

    @Test
    fun `a token the store refused leaves no entry`() = runBlocking {
        store.acceptTokens = false
        every { client.enroll(any(), CODE, profile) } returns WearExchangeEnrollResult.Enrolled(TOKEN)

        val result = repository.enroll(join())

        assertEquals(WearExchangeEnrollResult.Refused(WearExchangeEnrollResult.REASON_TOKEN_NOT_STORED), result)
        assertTrue(store.all().isEmpty())
    }

    @Test
    fun `removing the outgoing entry revokes it and moves the mark`() = runBlocking {
        val outgoing = WearExchangeEntry(HOST, PORT, LOGIN, HELD_ID, null, outgoing = true)
        store.put(outgoing)
        store.put(WearExchangeEntry("other.example.net", PORT, LOGIN, "BBBB", null))
        store.saveToken(outgoing.identity, TOKEN)
        every { client.revokeOwnDevice(outgoing, TOKEN, "2.6.0") } returns Unit

        assertEquals(WearExchangeRemoval.REVOKED, repository.remove(outgoing))
        assertNull(store.token(outgoing.identity))
        assertEquals("BBBB", store.all().single { it.outgoing }.deviceId)
    }

    @Test
    fun `an unreachable server still forgets the entry`() = runBlocking {
        val entry = WearExchangeEntry(HOST, PORT, LOGIN, HELD_ID, null, outgoing = true)
        store.put(entry)
        store.saveToken(entry.identity, TOKEN)
        every { client.revokeOwnDevice(any(), any(), any()) } throws IOException("down")

        assertEquals(WearExchangeRemoval.FORGOTTEN_LOCALLY, repository.remove(entry))
        assertTrue(store.all().isEmpty())
        verify(exactly = 1) { client.revokeOwnDevice(any(), any(), any()) }
    }

    @Test
    fun `setting the outgoing entry clears the others`() = runBlocking {
        val first = WearExchangeEntry(HOST, PORT, LOGIN, HELD_ID, null, outgoing = true)
        val second = WearExchangeEntry("other.example.net", PORT, LOGIN, "BBBB", null)
        store.put(first)
        store.put(second)

        repository.setOutgoing(second)

        assertEquals(listOf("BBBB"), store.all().filter { it.outgoing }.map { it.deviceId })
    }

    private fun join() = WearExchangeJoin(HOST, PORT, LOGIN, PIN, CODE)

    private class MemoryStore : WearExchangeEntryStore {
        private val held = MutableStateFlow<List<WearExchangeEntry>>(emptyList())
        private val tokens = mutableMapOf<String, String>()
        var acceptTokens = true

        override val entries: Flow<List<WearExchangeEntry>> = held

        override suspend fun all(): List<WearExchangeEntry> = held.value

        override suspend fun put(entry: WearExchangeEntry) {
            val index = held.value.indexOfFirst { it.identity == entry.identity }
            held.value = if (index < 0) held.value + entry else held.value.toMutableList().apply { set(index, entry) }
        }

        override suspend fun remove(identity: String) {
            held.value = held.value.filterNot { it.identity == identity }
        }

        override suspend fun setOutgoing(identity: String) {
            held.value = held.value.map { it.copy(outgoing = it.identity == identity) }
        }

        override suspend fun token(identity: String): String? = tokens[identity]

        override suspend fun saveToken(identity: String, token: String): Boolean {
            if (acceptTokens) tokens[identity] = token
            return acceptTokens
        }

        override suspend fun forgetToken(identity: String) {
            tokens.remove(identity)
        }
    }

    private companion object {
        const val HOST = "exchange.example.net"
        const val PORT = 44_022
        const val LOGIN = "owner"
        const val HELD_ID = "AAAAAAAAAAAAAAAAAAAAAA"
        const val TOKEN = "tok3n"
        const val CODE = "7KQ2M9XA"
        const val PIN = "SHA256:pinned"
    }
}
