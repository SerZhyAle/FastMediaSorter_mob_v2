package com.sza.fastmediasorter.data.remote.sftp.anywhere

import com.google.gson.JsonParser
import com.sza.fastmediasorter.data.remote.exchange.ExchangeGrantAudiences
import com.sza.fastmediasorter.data.security.e2e.E2eDeviceKey
import com.sza.fastmediasorter.domain.model.ExchangeGrant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** DEVICE-EXCHANGE 27 item DJ: a guest grant goes sealed to the guest's devices, or not at all. */
class ExchangeGrantSealingTest {

    private val share = ExchangeGrantEnvelopes.Share(listOf("192.168.1.5"), PORT, "SHA256:abc", "r-1")
    private val guestGrant = ExchangeGrant(GRANT_ID, ExchangeGrant.Kind.GUEST, "content://tree", "grant-login", "bob")
    private val link =
        ExchangeGrant("link-1", ExchangeGrant.Kind.LINK, "content://tree", "link-login", linkShareId = "s")

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
        val key = E2eDeviceKey.generate()
        val members = JsonParser.parseString(
            """{"grants":[{"grantId":"$GRANT_ID","guest":"bob","devices":[""" +
                """{"deviceId":"bob-phone","e2eKey":{"v":1,"kem":"${key.kem}"}},{"deviceId":"bob-old"}]}]}"""
        ).asJsonObject

        val audience = requireNotNull(ExchangeGrantAudiences.of(members))

        assertEquals(listOf("bob-phone"), audience.getValue(GRANT_ID).map { it.deviceId })
        assertNull(ExchangeGrantAudiences.of(JsonParser.parseString("{}").asJsonObject))
    }

    private companion object {
        const val GRANT_ID = "g-1"
        const val PORT = 2222
    }
}
