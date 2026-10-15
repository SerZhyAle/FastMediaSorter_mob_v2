package com.sza.fastmediasorter.data.repository

import com.google.gson.JsonObject
import com.sza.fastmediasorter.data.security.e2e.E2eDeviceKey
import com.sza.fastmediasorter.data.security.e2e.E2eRecipient
import com.sza.fastmediasorter.data.security.e2e.E2eSealedBox
import com.sza.fastmediasorter.data.security.e2e.E2eSelf
import com.sza.fastmediasorter.domain.model.ExchangeSealIssue
import com.sza.fastmediasorter.domain.model.SftpRendezvousResource
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** DEVICE-EXCHANGE 27 items DI and DP: a folder through the server is used only from its opened `sealedAccess`. */
class ExchangeSealedContentOpenerTest {

    private val phone = E2eSelf(PRODUCER, E2eDeviceKey.generate())
    private val tablet = E2eSelf(CONSUMER, E2eDeviceKey.generate())
    private val opener = ExchangeSealedContentOpener(mockk(relaxed = true))
    private val sealing = ExchangeSealing(tablet, emptyList(), emptyMap(), mapOf(PRODUCER to phone.key.kem))

    private fun record(sealedAccess: String? = null, descriptor: String? = null, fingerprint: String? = FINGERPRINT) =
        SftpRendezvousResource(
            resourceId = RESOURCE,
            deviceId = PRODUCER,
            kind = SftpRendezvousResource.KIND_SFTP_SHARE,
            name = "Phone",
            descriptor = descriptor,
            updatedAtMs = 0L,
            writtenAtMs = 0L,
            ttlSeconds = 0L,
            hostKeyFingerprint = fingerprint,
            sealedAccess = sealedAccess,
        )

    private fun box(context: String = E2eSealedBox.accessContext(RESOURCE), sender: E2eSelf = phone): String {
        val access = JsonObject().apply { addProperty("descriptor", CODE) }
        val to = listOf(E2eRecipient(CONSUMER, tablet.key.publicKey))
        return E2eSealedBox.seal(access, context, sender, to).toString()
    }

    @Test
    fun `an opened box gives the descriptor and the planner sees a clear record`() {
        val opened = opener.openAccess(sealing, record(sealedAccess = box()))

        assertEquals(CODE, opened.descriptor)
        assertNull(opened.sealedAccess)
        assertNull(opened.sealIssue)
    }

    @Test
    fun `a clear access through the server is dropped as e2e-missing`() {
        val opened = opener.openAccess(sealing, record(descriptor = CODE))

        assertNull(opened.descriptor)
        assertEquals(ExchangeSealIssue.E2E_MISSING, opened.sealIssue)
    }

    @Test
    fun `a moved box, an untrusted sender and a changed host key are sealed-failed`() {
        val stranger = E2eSelf(PRODUCER, E2eDeviceKey.generate())
        val cases = listOf(
            record(sealedAccess = box(context = E2eSealedBox.accessContext("another"))),
            record(sealedAccess = box(sender = stranger)),
            record(sealedAccess = box(), fingerprint = "SHA256:BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB"),
        )

        cases.forEach { case ->
            val opened = opener.openAccess(sealing, case)
            assertNull(opened.descriptor)
            assertEquals(ExchangeSealIssue.SEALED_FAILED, opened.sealIssue)
        }
    }

    @Test
    fun `a guest opens a grant from its first sender and refuses another key later`() {
        val trust = ExchangeSealedContentOpener.GrantTrust(emptyMap())
        val guest = ExchangeSealing(tablet, emptyList(), emptyMap(), emptyMap())

        val first = opener.openAccess(guest, grantRecord(grantBox(phone)), trust)
        val forged = opener.openAccess(guest, grantRecord(grantBox(E2eSelf(PRODUCER, E2eDeviceKey.generate()))), trust)

        assertEquals(CODE, first.descriptor)
        assertEquals(mapOf(GRANT to phone.key.kem), trust.learned)
        assertNull(forged.descriptor)
        assertEquals(ExchangeSealIssue.KEY_CHANGED, forged.sealIssue)
    }

    private fun grantRecord(box: String) = record(sealedAccess = box).copy(grantId = GRANT)

    private fun grantBox(sender: E2eSelf): String {
        val access = JsonObject().apply { add("access", JsonObject().apply { addProperty("descriptor", CODE) }) }
        val to = listOf(E2eRecipient(CONSUMER, tablet.key.publicKey))
        return E2eSealedBox.seal(access, E2eSealedBox.grantContext(GRANT), sender, to).toString()
    }

    @Test
    fun `an entry without its own key uses nothing that came sealed`() {
        val keyless = ExchangeSealing(null, emptyList(), emptyMap(), mapOf(PRODUCER to phone.key.kem))

        val opened = opener.openAccess(keyless, record(sealedAccess = box()))

        assertNull(opened.descriptor)
        assertEquals(ExchangeSealIssue.E2E_MISSING, opened.sealIssue)
    }

    private companion object {
        const val PRODUCER = "phone-1"
        const val CONSUMER = "tablet-1"
        const val RESOURCE = "r-1"
        const val GRANT = "g-1"
        const val KEY_HASH = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
        const val FINGERPRINT = "SHA256:" + KEY_HASH
        const val CODE = "FMSSFTP1:h=192.168.1.20&p=2222&u=fms&pw=secret&fp=SHA256%3A" + KEY_HASH
    }
}
