package com.sza.fastmediasorter.data.remote.sftp.anywhere

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.sza.fastmediasorter.data.broadcast.auth.BroadcastCredentialRegistry
import com.sza.fastmediasorter.data.broadcast.exchange.BroadcastSealMaterial
import com.sza.fastmediasorter.data.broadcast.exchange.BroadcastTunnelIdentities
import com.sza.fastmediasorter.data.broadcast.exchange.ExchangeBroadcastGrants
import com.sza.fastmediasorter.data.broadcast.exchange.ExchangeRelayKeys
import com.sza.fastmediasorter.data.remote.exchange.ExchangeGrantAudiences
import com.sza.fastmediasorter.data.repository.ExchangeE2eRepository
import com.sza.fastmediasorter.data.repository.ExchangeSealing
import com.sza.fastmediasorter.data.security.e2e.E2eDeviceKey
import com.sza.fastmediasorter.data.security.e2e.E2eOpenResult
import com.sza.fastmediasorter.data.security.e2e.E2eRecipient
import com.sza.fastmediasorter.data.security.e2e.E2eSealedBox
import com.sza.fastmediasorter.data.security.e2e.E2eSelf
import com.sza.fastmediasorter.domain.model.DeviceExchangeAccountEntry
import com.sza.fastmediasorter.domain.model.ExchangeGrant
import com.sza.fastmediasorter.domain.model.SftpRendezvousBroadcast
import com.sza.fastmediasorter.domain.model.SftpRendezvousResource
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** DEVICE-EXCHANGE 27 item DJ: a guest grant goes sealed to the guest's devices, or not at all. */
class ExchangeGrantSealingTest {

    private val share = ExchangeGrantEnvelopes.Share(listOf("192.168.1.5"), PORT, "SHA256:abc", "r-1")
    private val guestGrant = ExchangeGrant(GRANT_ID, ExchangeGrant.Kind.GUEST, "content://tree", "grant-login", "bob")
    private val link =
        ExchangeGrant("link-1", ExchangeGrant.Kind.LINK, "content://tree", "link-login", linkShareId = "s")
    private val owner = E2eSelf("owner-phone", E2eDeviceKey.generate())
    private val guest = E2eSelf("bob-phone", E2eDeviceKey.generate())
    private val entry = DeviceExchangeAccountEntry("127.0.0.1", PORT, "owner", "owner-phone", null)
    private val secret = JsonObject().apply { addProperty("access", "secret") }

    @Test
    fun `a guest grant carries the box and never a clear access`() {
        val envelope = ExchangeGrantEnvelopes.grant(guestGrant, "secret", share) { payload ->
            assertTrue(payload.has("access"))
            JsonParser.parseString("""{"v":1}""").asJsonObject
        }

        val members = requireNotNull(envelope).members
        assertTrue(members.has("sealed"))
        assertFalse(members.has("access"))
    }

    @Test
    fun `a guest grant with nobody to seal for is not sent`() {
        assertNull(ExchangeGrantEnvelopes.grant(guestGrant, "secret", share) { null })
    }

    @Test
    fun `a link needs no box`() {
        val envelope = ExchangeGrantEnvelopes.grant(link, "secret", share) { error("a link is never sealed") }

        assertEquals("link", requireNotNull(envelope).string("kind"))
    }

    @Test
    fun `the guest devices of each grant are read from grants, keyless ones dropped`() {
        val members = parse(
            """{"grants":[{"grantId":"$GRANT_ID","guest":"bob","devices":[""" +
                """{"deviceId":"bob-phone","e2eKey":{"v":1,"kem":"${guest.key.kem}"}},{"deviceId":"bob-old"}]}]}""",
        )

        val audience = ExchangeGrantAudiences.whole(members)

        assertEquals(listOf("bob-phone"), audience.getValue(GRANT_ID).map { it.deviceId })
        assertEquals(emptyMap<String, List<E2eRecipient>>(), ExchangeGrantAudiences.whole(parse("{}")))
    }

    @Test
    fun `a changed adds its grants, takes out its removals and keeps the rest`() {
        val held = mapOf(GRANT_ID to listOf(recipient()), "g-2" to listOf(recipient()))
        val members = parse(
            """{"revision":2,"upserts":{"grants":[{"grantId":"g-3","devices":[]}]},""" +
                """"removals":{"grantIds":["g-2"]}}""",
        )

        val next = requireNotNull(ExchangeGrantAudiences.changed(held, members))

        assertEquals(setOf(GRANT_ID, "g-3"), next.keys)
        assertEquals(held, ExchangeGrantAudiences.changed(held, parse("""{"upserts":{"devices":[]}}""")))
        assertNull(ExchangeGrantAudiences.changed(null, members))
    }

    @Test
    fun `no grant goes before the member's grants are known`() {
        assertNull(ExchangeGrantSealing(entry, true, null).box(GRANT_ID, owner, secret))
    }

    @Test
    fun `a grant the server does not list goes with the registering box`() {
        val box = requireNotNull(ExchangeGrantSealing(entry, true, emptyMap()).box(GRANT_ID, owner, secret))

        assertTrue(E2eSealedBox.addressesNobody(box))
        assertEquals(0, box.getAsJsonArray("keys").size())
        assertFalse(box.toString().contains("secret"))
    }

    @Test
    fun `a listed grant is sealed to its guest devices, and with none gets no box`() {
        val sealing = ExchangeGrantSealing(entry, true, mapOf(GRANT_ID to listOf(recipient()), "g-2" to emptyList()))

        val box = requireNotNull(sealing.box(GRANT_ID, owner, secret))
        val opened = E2eSealedBox.open(box, E2eSealedBox.grantContext(GRANT_ID), guest) { _, _ -> true }

        assertFalse(E2eSealedBox.addressesNobody(box))
        assertEquals(secret, (opened as E2eOpenResult.Opened).payload)
        assertNull(sealing.box("g-2", owner, secret))
    }

    @Test
    fun `grant audience key changes when a guest device rotates its key and ignores recipient order`() {
        val rotated = E2eSelf(guest.deviceId, E2eDeviceKey.generate())
        val tablet = E2eRecipient("bob-tablet", E2eDeviceKey.generate().publicKey)

        val first = ExchangeGrantAudiences.keyOf(mapOf(GRANT_ID to listOf(recipient(), tablet)))
        val reKeyed = ExchangeGrantAudiences.keyOf(
            mapOf(GRANT_ID to listOf(E2eRecipient(rotated.deviceId, rotated.key.publicKey), tablet)),
        )
        val reordered = ExchangeGrantAudiences.keyOf(mapOf(GRANT_ID to listOf(tablet, recipient())))

        assertNotEquals(first, reKeyed)
        assertEquals(first, reordered)
    }

    @Test
    fun `sealedBroadcast and sealedRecord keys change on key rotation and ignore recipient order`() =
        runBlocking {
            val e2e = mockk<ExchangeE2eRepository>()
            val credentials = BroadcastCredentialRegistry(Dispatchers.Unconfined)
            val relayKeys = ExchangeRelayKeys().apply { begin(BROADCAST_ID) }
            val seals = BroadcastSealMaterial(relayKeys, BroadcastTunnelIdentities(), credentials)
            val provider = SftpExchangeProducerProvider(
                settings = mockk(relaxed = true),
                accounts = mockk(relaxed = true),
                identityStore = mockk(relaxed = true),
                ids = mockk(relaxed = true),
                grants = mockk(relaxed = true),
                accountRepository = dagger.Lazy { mockk(relaxed = true) },
                e2e = e2e,
                seals = seals,
                broadcastGrants = ExchangeBroadcastGrants(credentials, relayKeys),
            )
            val broadcast = SftpRendezvousBroadcast(
                broadcastId = BROADCAST_ID,
                deviceId = owner.deviceId,
                title = "Phone",
                mode = "AUDIO_ONLY",
                startedAtMs = 0L,
                updatedAtMs = 0L,
                descriptorJson = "{}",
                writtenAtMs = 0L,
                ttlSeconds = 0L,
            )
            val resource = SftpRendezvousResource(
                resourceId = "r-1",
                shareId = "s-1",
                deviceId = owner.deviceId,
                kind = SftpRendezvousResource.KIND_SFTP_SHARE,
                name = "Phone",
                descriptor = "FMSSFTP1:127.0.0.1:$PORT",
                root = null,
                publicPort = 0,
                updatedAtMs = 0L,
                writtenAtMs = 0L,
                ttlSeconds = 0L,
            )
            val peerA1 = E2eRecipient("peer-a", E2eDeviceKey.generate().publicKey)
            val peerA2 = E2eRecipient("peer-a", E2eDeviceKey.generate().publicKey)
            val peerB = E2eRecipient("peer-b", E2eDeviceKey.generate().publicKey)
            val ownerRotated = E2eSelf(owner.deviceId, E2eDeviceKey.generate())

            coEvery { e2e.sealing(entry, any()) } returnsMany listOf(
                ExchangeSealing(owner, listOf(peerA1, peerB), emptyMap(), emptyMap()),
                ExchangeSealing(owner, listOf(peerB, peerA1), emptyMap(), emptyMap()),
                ExchangeSealing(owner, listOf(peerA2, peerB), emptyMap(), emptyMap()),
                ExchangeSealing(ownerRotated, listOf(peerA2, peerB), emptyMap(), emptyMap()),
            )

            val bFirst = provider.sealedBroadcast(entry, broadcast, emptyList())
            val bReordered = provider.sealedBroadcast(entry, broadcast, emptyList())
            val bRecipientRotated = provider.sealedBroadcast(entry, broadcast, emptyList())
            val bSenderRotated = provider.sealedBroadcast(entry, broadcast, emptyList())

            assertEquals(bFirst.recipientsKey, bReordered.recipientsKey)
            assertNotEquals(bFirst.recipientsKey, bRecipientRotated.recipientsKey)
            assertNotEquals(bRecipientRotated.recipientsKey, bSenderRotated.recipientsKey)

            coEvery { e2e.sealing(entry, any()) } returnsMany listOf(
                ExchangeSealing(owner, listOf(peerA1, peerB), emptyMap(), emptyMap()),
                ExchangeSealing(owner, listOf(peerB, peerA1), emptyMap(), emptyMap()),
                ExchangeSealing(owner, listOf(peerA2, peerB), emptyMap(), emptyMap()),
                ExchangeSealing(ownerRotated, listOf(peerA2, peerB), emptyMap(), emptyMap()),
            )

            val rFirst = provider.sealedRecord(entry, resource, true, emptyList())
            val rReordered = provider.sealedRecord(entry, resource, true, emptyList())
            val rRecipientRotated = provider.sealedRecord(entry, resource, true, emptyList())
            val rSenderRotated = provider.sealedRecord(entry, resource, true, emptyList())

            assertEquals(rFirst.recipientsKey, rReordered.recipientsKey)
            assertNotEquals(rFirst.recipientsKey, rRecipientRotated.recipientsKey)
            assertNotEquals(rRecipientRotated.recipientsKey, rSenderRotated.recipientsKey)
        }

    private fun recipient() = E2eRecipient(guest.deviceId, guest.key.publicKey)

    private fun parse(json: String) = JsonParser.parseString(json).asJsonObject

    private companion object {
        const val GRANT_ID = "g-1"
        const val BROADCAST_ID = "b-1"
        const val PORT = 2222
    }
}
