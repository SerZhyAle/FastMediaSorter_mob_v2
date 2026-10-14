package com.sza.fastmediasorter.data.remote.sftp.anywhere

import com.google.gson.JsonParser
import com.sza.fastmediasorter.domain.model.SftpRendezvousResource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResourceProtectionTest {

    private fun decode(protection: String, access: String = ACCESS): SftpRendezvousResource {
        val body = JsonParser.parseString(
            """{"resourceId":"resource","deviceId":"device","kind":"sftp-share","name":"Private",
                $access"shareId":"capability","publicPort":2222,"protection":$protection}"""
        ).asJsonObject
        return SftpRendezvousCodec.decodeBody(SftpRendezvousCodec.TYPE_RESOURCE, body) as SftpRendezvousResource
    }

    @Test
    fun `unknown future and malformed locks keep the row but remove every access path`() {
        listOf("null", "[]", "{}", "{\"scheme\":\"future\"}", "{\"scheme\":7}").forEach { protection ->
            val record = decode(protection)
            assertTrue(protection, record.pinProtected)
            assertNull(protection, record.descriptor)
            assertNull(protection, record.shareId)
            assertNull(protection, record.publicPort)
            assertNull(protection, record.proofRoot)
            val encoded = SftpRendezvousCodec.encode(record)
            assertTrue(encoded.contains("\"schemaVersion\":2"))
            assertFalse(encoded.contains("synthetic"))
            assertTrue((SftpRendezvousCodec.decode(encoded) as SftpRendezvousResource).pinProtected)
        }
    }

    @Test
    fun `a pin-v1 lock keeps its access and names the root the proof presents`() {
        val record = decode("""{"scheme":"pin-v1"}""")

        assertTrue(record.pinProtected)
        assertEquals("synthetic", record.descriptor)
        assertEquals("capability", record.shareId)
        assertEquals(2222, record.publicPort)
        assertEquals("Private", record.proofRoot)
    }

    @Test
    fun `a pin-v1 record survives a Drive round trip with its lock and its access`() {
        val record = decode("""{"scheme":"pin-v1"}""")

        val encoded = SftpRendezvousCodec.encode(record)
        val back = SftpRendezvousCodec.decode(encoded) as SftpRendezvousResource

        assertTrue(encoded.contains("\"schemaVersion\":2"))
        assertTrue(encoded.contains("\"protection\":{\"scheme\":\"pin-v1\"}"))
        assertEquals(record.copy(writtenAtMs = back.writtenAtMs, ttlSeconds = back.ttlSeconds), back)
    }

    @Test
    fun `a pin-v1 lock without a usable root cannot be proven`() {
        val noRoot = decode("""{"scheme":"pin-v1"}""", access = """"access":{"descriptor":"synthetic"},""")
        val slash = decode("""{"scheme":"pin-v1"}""", access = """"access":{"descriptor":"synthetic","root":"/"},""")

        assertNull(noRoot.proofRoot)
        assertNull(slash.proofRoot)
        assertEquals("synthetic", noRoot.descriptor)
    }

    @Test
    fun `a pin-v1 lock without access has no proof root`() {
        val record = decode("""{"scheme":"pin-v1"}""", access = "")

        assertTrue(record.pinProtected)
        assertNull(record.descriptor)
        assertNull(record.proofRoot)
    }

    @Test
    fun `an unprotected record is untouched`() {
        val body = JsonParser.parseString(
            """{"resourceId":"r","deviceId":"d","kind":"sftp-share","name":"Open",
                "access":{"descriptor":"synthetic","root":"/Open"}}"""
        ).asJsonObject
        val record = SftpRendezvousCodec.decodeBody(SftpRendezvousCodec.TYPE_RESOURCE, body) as SftpRendezvousResource

        assertFalse(record.pinProtected)
        assertEquals("synthetic", record.descriptor)
        assertNull(record.proofRoot)
        assertTrue(SftpRendezvousCodec.encode(record).contains("\"schemaVersion\":1"))
    }

    private companion object {
        const val ACCESS = """"access":{"descriptor":"synthetic","root":"/Private"},"""
    }
}
