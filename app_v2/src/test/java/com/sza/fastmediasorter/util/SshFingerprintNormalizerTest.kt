package com.sza.fastmediasorter.util

import com.sza.fastmediasorter.utils.SshFingerprintNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * S0046 - Phase 02 Step 02.3.
 *
 * Tests cover every input form `canonical(..)` advertises plus the short-form helper and
 * null/blank/garbage handling. No mocks, no Robolectric - pure JVM.
 *
 * Test vector: 32 zero-bytes (SHA256-sized) for a deterministic round-trip target.
 *  - canonical base64 (no padding) = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
 *  - canonical = "SHA256:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
 *  - hex = 64 zero hex chars
 */
class SshFingerprintNormalizerTest {

    private val zeroCanonical = "SHA256:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
    private val zeroBase64 = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
    private val zeroHexNoColons = "0".repeat(64)
    private val zeroHexWithColons = (1..32).joinToString(":") { "00" }

    @Test
    fun `canonical passes through already canonical input unchanged`() {
        assertEquals(zeroCanonical, SshFingerprintNormalizer.canonical(zeroCanonical))
    }

    @Test
    fun `canonical strips trailing padding from SHA256-prefixed input`() {
        // Spec example payload with padding `=` - implementation must strip and re-encode no-pad.
        val padded = zeroCanonical + "="
        assertEquals(zeroCanonical, SshFingerprintNormalizer.canonical(padded))
    }

    @Test
    fun `canonical adds SHA256 prefix to bare base64 of correct length`() {
        assertEquals(zeroCanonical, SshFingerprintNormalizer.canonical(zeroBase64))
    }

    @Test
    fun `canonical decodes hex with colons to same canonical`() {
        assertEquals(zeroCanonical, SshFingerprintNormalizer.canonical(zeroHexWithColons))
    }

    @Test
    fun `canonical decodes hex without colons to same canonical`() {
        assertEquals(zeroCanonical, SshFingerprintNormalizer.canonical(zeroHexNoColons))
    }

    @Test
    fun `canonical returns null for null input`() {
        assertNull(SshFingerprintNormalizer.canonical(null))
    }

    @Test
    fun `canonical returns null for blank input`() {
        assertNull(SshFingerprintNormalizer.canonical(""))
        assertNull(SshFingerprintNormalizer.canonical("   "))
    }

    @Test
    fun `canonical returns null for garbage input`() {
        assertNull(SshFingerprintNormalizer.canonical("not-a-fingerprint!"))
        assertNull(SshFingerprintNormalizer.canonical("zz:zz:zz"))
        // Wrong-length hex (62 chars instead of 64).
        assertNull(SshFingerprintNormalizer.canonical("0".repeat(62)))
        // Wrong-length base64 (decodes to 30 bytes instead of 32).
        assertNull(SshFingerprintNormalizer.canonical("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"))
    }

    @Test
    fun `shortForList truncates base64 payload to 12 chars plus double dot`() {
        // 43-char zero payload → 12 chars + ".." = "SHA256:AAAAAAAAAAAA.."
        val short = SshFingerprintNormalizer.shortForList(zeroCanonical)
        assertEquals("SHA256:AAAAAAAAAAAA..", short)
    }

    @Test
    fun `shortForList returns input unchanged when payload shorter than 12 chars`() {
        val tiny = "SHA256:abc"
        assertEquals(tiny, SshFingerprintNormalizer.shortForList(tiny))
    }

    @Test
    fun `shortForList returns input unchanged when prefix missing`() {
        val malformed = "no-prefix-here"
        assertEquals(malformed, SshFingerprintNormalizer.shortForList(malformed))
    }

    @Test
    fun `fromRawKeyBytes computes canonical SHA256 fingerprint for arbitrary byte array`() {
        val emptyBytes = ByteArray(0)
        val expectedSha256OfEmpty = "SHA256:47DEQpj8HBSa+/TImW+5JCeuQeRkm5NMpJWZG3hSuFU"
        val result = SshFingerprintNormalizer.fromRawKeyBytes(emptyBytes)
        assertEquals(expectedSha256OfEmpty, result)
        assertEquals(result, SshFingerprintNormalizer.canonical(result))
    }

    @Test
    fun `fromBase64Key decodes base64 public key and computes canonical SHA256 fingerprint`() {
        assertNull(SshFingerprintNormalizer.fromBase64Key(null))
        assertNull(SshFingerprintNormalizer.fromBase64Key(""))
        // Base64 of empty bytes is "" which is blank -> null.
        // Base64 of 4 bytes: "AQIDBA==" -> bytes [1, 2, 3, 4]
        val base64Key = "AQIDBA=="
        val rawBytes = byteArrayOf(1, 2, 3, 4)
        val expected = SshFingerprintNormalizer.fromRawKeyBytes(rawBytes)
        val result = SshFingerprintNormalizer.fromBase64Key(base64Key)
        assertEquals(expected, result)
    }
}
