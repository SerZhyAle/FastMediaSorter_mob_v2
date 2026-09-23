package com.sza.fastmediasorter.data.security.fdsec

import com.sza.fastmediasorter.data.security.fdsec.FdSecTestSupport.hex
import com.sza.fastmediasorter.data.security.fdsec.FdSecTestSupport.toHex
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The hand-built XChaCha20 layer is checked against the published HChaCha20 vector of
 * draft-irtf-cfrg-xchacha, independently of the FD-SEC container vectors.
 *
 * A wrong sub-key would otherwise surface only as the contract's indistinguishable
 * "wrong credential, not a container, or tampering" outcome, which is exactly the symptom a test is
 * least able to attribute.
 */
class XChaCha20Poly1305Test {

    @Test
    fun `hChaCha20 matches the published sub-key vector`() {
        val key = hex("000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f")
        val nonce = hex("000000090000004a0000000031415927")

        val subKey = XChaCha20Poly1305.hChaCha20(key, nonce)

        assertEquals("82413b4227b27bfed30e42508a877d73a0f9e4d58a74a853c12ec41326d3ecdc", toHex(subKey))
    }

    @Test
    fun `seal and open round-trip with associated data`() {
        val key = ByteArray(FdSecFormat.FILE_KEY_SIZE) { it.toByte() }
        val nonce = ByteArray(FdSecFormat.NONCE_SIZE) { (it + 1).toByte() }
        val associatedData = "FDSEC1/test".toByteArray(Charsets.UTF_8)
        val plaintext = "the quick brown fox".toByteArray(Charsets.UTF_8)

        val sealed = XChaCha20Poly1305.seal(key, nonce, associatedData, plaintext)

        assertEquals(plaintext.size + FdSecFormat.TAG_SIZE, sealed.size)
        assertArrayEquals(plaintext, XChaCha20Poly1305.open(key, nonce, associatedData, sealed))
    }

    @Test
    fun `open returns null on a flipped bit`() {
        val key = ByteArray(FdSecFormat.FILE_KEY_SIZE) { it.toByte() }
        val nonce = ByteArray(FdSecFormat.NONCE_SIZE) { (it + 1).toByte() }
        val associatedData = ByteArray(0)
        val sealed = XChaCha20Poly1305.seal(key, nonce, associatedData, "payload".toByteArray(Charsets.UTF_8))

        sealed[0] = (sealed[0].toInt() xor 1).toByte()

        assertNull(XChaCha20Poly1305.open(key, nonce, associatedData, sealed))
    }

    @Test
    fun `open returns null when the associated data differs`() {
        val key = ByteArray(FdSecFormat.FILE_KEY_SIZE) { it.toByte() }
        val nonce = ByteArray(FdSecFormat.NONCE_SIZE) { (it + 1).toByte() }
        val sealed = XChaCha20Poly1305.seal(key, nonce, "one".toByteArray(Charsets.UTF_8), "payload".toByteArray())

        assertNull(XChaCha20Poly1305.open(key, nonce, "two".toByteArray(Charsets.UTF_8), sealed))
    }
}
