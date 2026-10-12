package com.sza.fastmediasorter.data.remote.sftp.anywhere

import com.google.gson.JsonParser
import com.sza.fastmediasorter.domain.model.SftpRendezvousResource
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResourceProtectionTest {
    @Test
    fun `known future and malformed locks keep the row but remove every access path`() {
        listOf("null", "[]", "{}", "{\"scheme\":\"pin-v1\"}", "{\"scheme\":\"future\"}").forEach { protection ->
            val body = JsonParser.parseString(
                """{"resourceId":"resource","deviceId":"device","kind":"sftp-share","name":"Private",
                    "access":{"descriptor":"synthetic","root":"/Private"},"shareId":"capability",
                    "publicPort":2222,"protection":$protection}"""
            ).asJsonObject
            val record = SftpRendezvousCodec.decodeBody(SftpRendezvousCodec.TYPE_RESOURCE, body)
                as SftpRendezvousResource
            assertTrue(record.pinProtected)
            assertNull(record.descriptor)
            assertNull(record.shareId)
            assertNull(record.publicPort)
            val encoded = SftpRendezvousCodec.encode(record)
            assertTrue(encoded.contains("\"schemaVersion\":2"))
            assertFalse(encoded.contains("synthetic"))
            assertTrue((SftpRendezvousCodec.decode(encoded) as SftpRendezvousResource).pinProtected)
        }
    }
}
