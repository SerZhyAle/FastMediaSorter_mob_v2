package com.sza.fastmediasorter.data.broadcast.auth

import com.sza.fastmediasorter.data.security.e2e.E2eBytes
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class BroadcastVerifiersTest {

    @Test
    fun `PBKDF2-HMAC-SHA256 matches the published vectors`() {
        val salt = "salt".toByteArray()
        assertEquals(
            "120fb6cffcf8b32c43e7225256c4f837a86548c92ccc35480805987cb70be17b",
            E2eBytes.hex(BroadcastVerifiers.derive("password", salt, 1)),
        )
        assertEquals(
            "c5e478d59288c841aa530db6845c4c8d962893a001ce4e11a4963873aa98134a",
            E2eBytes.hex(BroadcastVerifiers.derive("password", salt, 4096)),
        )
    }

    @Test
    fun `a verifier carries the user, a 16-byte salt and the derived hash`() {
        val pair = BroadcastCredentialPair("listener", "abc123")
        val verifier = BroadcastVerifiers.create(pair)
        val salt = E2eBytes.fromB64url(verifier.salt)
        assertEquals("listener", verifier.user)
        assertEquals(BroadcastVerifiers.SALT_BYTES, salt.size)
        assertArrayEquals(
            BroadcastVerifiers.derive("abc123", salt, BroadcastVerifiers.ITERATIONS),
            E2eBytes.fromB64url(verifier.hash),
        )
    }
}
