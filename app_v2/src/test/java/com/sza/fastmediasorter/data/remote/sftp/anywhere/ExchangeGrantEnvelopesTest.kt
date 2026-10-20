package com.sza.fastmediasorter.data.remote.sftp.anywhere

import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExchangeGrantEnvelopesTest {

    private val credentials = JsonObject().apply {
        addProperty("user", "guest-abc")
        addProperty("password", SECRET)
    }

    private val payload = JsonObject().apply {
        add("credentials", credentials)
        addProperty("relayKey", "key")
    }

    @Test
    fun `a broadcast grant carries the target and only the box`() {
        val envelope = ExchangeGrantEnvelopes.broadcastGrant("g1", "bob", "b1", payload) {
            JsonObject().apply { addProperty("ct", "opaque") }
        }!!
        val json = envelope.encode()
        assertTrue(json.contains("\"broadcastId\":\"b1\""))
        assertTrue(json.contains("\"guest\":\"bob\""))
        assertTrue(json.contains("\"sealed\""))
        assertFalse(json.contains("resourceId"))
        assertFalse(json.contains("\"access\""))
        assertFalse(json.contains("\"credentials\""))
        assertFalse(json.contains(SECRET))
    }

    @Test
    fun `no box means no grant`() {
        assertNull(ExchangeGrantEnvelopes.broadcastGrant("g1", "bob", "b1", payload) { null })
    }

    @Test
    fun `the payload reaches the sealer whole`() {
        var sealed: JsonObject? = null
        ExchangeGrantEnvelopes.broadcastGrant("g1", "bob", "b1", payload) {
            sealed = it
            JsonObject()
        }
        assertEquals(SECRET, sealed!!.getAsJsonObject("credentials").get("password").asString)
    }

    private companion object {
        const val SECRET = "s3cretPassw0rd"
    }
}
