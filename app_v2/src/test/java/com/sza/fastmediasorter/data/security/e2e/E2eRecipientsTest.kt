package com.sza.fastmediasorter.data.security.e2e

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class E2eRecipientsTest {

    private val keys = (0 until PEERS).map { E2eDeviceKey.generate(FixedRandom("peer $it")) }

    private fun peer(index: Int, kem: String? = keys[index].kem, capable: Boolean = true) =
        E2ePeer("dev-$index", kem, capable, updatedAtMs = index.toLong())

    @Test
    fun `a first-seen key is learned and the device becomes a recipient`() {
        val peers = listOf(peer(0), peer(1))
        val learned = E2eRecipients.learned(SELF, peers, emptyMap())

        assertEquals(setOf("dev-0", "dev-1"), learned.keys)
        assertEquals(listOf("dev-1", "dev-0"), E2eRecipients.recipients(SELF, peers, learned).map { it.deviceId })
    }

    @Test
    fun `a changed key is key-changed, never learned over, and no recipient`() {
        val trusted = mapOf("dev-0" to keys[1].kem)
        val peers = listOf(peer(0))

        assertEquals(E2ePeerState.KEY_CHANGED, E2eRecipients.state(peers[0], trusted["dev-0"]))
        assertTrue(E2eRecipients.learned(SELF, peers, trusted).isEmpty())
        assertTrue(E2eRecipients.recipients(SELF, peers, trusted).isEmpty())
    }

    @Test
    fun `a device without the capability or a key is e2e-missing`() {
        assertEquals(E2ePeerState.MISSING, E2eRecipients.state(peer(0, capable = false), null))
        assertEquals(E2ePeerState.MISSING, E2eRecipients.state(peer(0, kem = null), null))
    }

    @Test
    fun `this device is never its own recipient`() {
        val peers = listOf(E2ePeer(SELF, keys[0].kem, true, 1L))

        assertTrue(E2eRecipients.recipients(SELF, peers, mapOf(SELF to keys[0].kem)).isEmpty())
        assertTrue(E2eRecipients.learned(SELF, peers, emptyMap()).isEmpty())
    }

    @Test
    fun `more than forty capable devices give the forty seen most recently`() {
        val peers = (0 until PEERS).map { peer(it) }
        val trusted = E2eRecipients.learned(SELF, peers, emptyMap())
        val recipients = E2eRecipients.recipients(SELF, peers, trusted)

        assertEquals(E2eSealedBox.MAX_RECIPIENTS, recipients.size)
        assertFalse(recipients.any { it.deviceId == "dev-0" })
    }

    @Test
    fun `trust compares the box's fromKey with the kept key`() {
        val trusted = mapOf("dev-0" to keys[0].kem)

        assertTrue(E2eRecipients.trusts(trusted, "dev-0", keys[0].publicKey))
        assertFalse(E2eRecipients.trusts(trusted, "dev-0", keys[1].publicKey))
        assertFalse(E2eRecipients.trusts(trusted, "dev-1", keys[1].publicKey))
    }

    @Test
    fun `keyOf changes when a recipient or sender key rotates, and ignores recipient order`() {
        val dev0Old = E2eRecipient("dev-0", keys[0].publicKey)
        val dev0New = E2eRecipient("dev-0", keys[1].publicKey)
        val dev1 = E2eRecipient("dev-1", keys[2].publicKey)
        val selfOld = E2eSelf(SELF, keys[3])
        val selfNew = E2eSelf(SELF, keys[4])

        assertNotEquals(E2eRecipients.keyOf(listOf(dev0Old, dev1)), E2eRecipients.keyOf(listOf(dev0New, dev1)))
        assertEquals(E2eRecipients.keyOf(listOf(dev0Old, dev1)), E2eRecipients.keyOf(listOf(dev1, dev0Old)))
        assertEquals(E2eRecipients.keyOf(listOf(dev0Old, dev0New)), E2eRecipients.keyOf(listOf(dev0New, dev0Old)))
        assertNotEquals(
            E2eRecipients.keyOf(selfOld, listOf(dev0Old, dev1)),
            E2eRecipients.keyOf(selfNew, listOf(dev0Old, dev1)),
        )
        assertEquals(
            E2eRecipients.keyOf(selfOld, listOf(dev0Old, dev1)),
            E2eRecipients.keyOf(selfOld, listOf(dev1, dev0Old)),
        )
        assertEquals("", E2eRecipients.keyOf(null, emptyList()))
    }

    private companion object {
        const val SELF = "dev-self"
        const val PEERS = 41
    }
}
