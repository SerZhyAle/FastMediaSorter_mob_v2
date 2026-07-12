package com.sza.fastmediasorter.data.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.GZIPOutputStream

/**
 * S0421: parser contract test against the frozen Phase 05 canonical vector.
 * The fixture bytes are identical to the companion repo's `TestCanonicalVector`
 * (P:\windows\fms_companion, internal/config/schema_test.go) and the example in
 * its docs/CONFIG_FORMAT.md - both ends must agree byte for byte.
 */
class CompanionConfigParserTest {

    private val parser = CompanionConfigParser()

    private fun canonicalVector(): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("companion/canonical_vector.json")) {
            "canonical_vector.json test resource missing"
        }.use { it.readBytes().toString(Charsets.UTF_8) }

    private fun canonicalVectorV2(): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("companion/canonical_vector_v2.json")) {
            "canonical_vector_v2.json test resource missing"
        }.use { it.readBytes().toString(Charsets.UTF_8) }

    @Test
    fun `parses canonical vector into expected DTO`() {
        val dto = parser.parse(canonicalVector())

        assertEquals(1, dto.schemaVersion)
        assertEquals("Home PC", dto.resourceName)
        assertEquals("sftp", dto.protocol)
        assertEquals("fms", dto.username)
        assertEquals("k7PmQ2wXr9TzS4vGnHb3JdLe", dto.password)
        assertEquals("SHA256:8f6TQvCbXjDMOyu4A9JzKcWlEHmR5pNsGgVaU2wYqhk", dto.hostKeyFingerprintSha256)

        val paths = requireNotNull(dto.accessPaths)
        assertEquals(2, paths.size)
        // Contract order: LAN first, then port-forward.
        assertEquals(CompanionAccessPathDto.KIND_LAN, paths[0].kind)
        assertEquals("192.168.1.23", paths[0].host)
        assertEquals(2022, paths[0].port)
        assertEquals(CompanionAccessPathDto.KIND_PORT_FORWARD, paths[1].kind)
        assertEquals("203.0.113.7", paths[1].host)

        val roots = requireNotNull(dto.roots)
        assertEquals(2, roots.size)
        assertEquals("/Photos", roots[0].virtualPath)
        assertEquals("Photos", roots[0].label)
        assertEquals("/Music", roots[1].virtualPath)
        assertEquals("Music", roots[1].label)
        // S1002: a v1 config leaves every optional v2 param null (import applies v1 defaults).
        assertNull(roots[0].profile)
        assertNull(roots[0].mediaTypes)
        assertNull(roots[0].accessPin)
        assertNull(roots[0].slideshowInterval)
        assertNull(roots[0].isDestination)
    }

    @Test
    fun `parses v2 canonical vector with resource params`() {
        val dto = parser.parse(canonicalVectorV2())

        assertEquals(2, dto.schemaVersion)
        val roots = requireNotNull(dto.roots)
        assertEquals(2, roots.size)

        val audio = roots[0]
        assertEquals("/Music", audio.virtualPath)
        assertEquals("Home Music", audio.label)
        assertEquals("audio_library", audio.profile)
        assertEquals(listOf("audio"), audio.mediaTypes)
        assertEquals("Vinyl rips", audio.comment)
        assertEquals("1234", audio.accessPin)
        assertEquals(15, audio.slideshowInterval)
        assertEquals(false, audio.isDestination)

        val drop = roots[1]
        assertEquals(true, drop.isDestination)
        assertEquals(-14575885, drop.destinationColor)
    }

    @Test
    fun `soft-ignores unknown profile token`() {
        val json = "{\"schemaVersion\":2,\"resourceName\":\"Test\",\"protocol\":\"sftp\"," +
            "\"accessPaths\":[{\"kind\":\"lan\",\"host\":\"192.168.1.5\",\"port\":22}]," +
            "\"username\":\"fms\",\"password\":\"secret\",\"hostKeyFingerprintSha256\":\"\"," +
            "\"roots\":[{\"virtualPath\":\"/Media\",\"label\":\"Media\",\"profile\":\"weird_token\"}]}"

        // The parser never rejects an unknown profile token - resolution (and fallback) happens at import.
        val dto = parser.parse(json)

        assertEquals("weird_token", dto.roots?.first()?.profile)
    }

    @Test
    fun `parses gzip base64 QR variant`() {
        val json = canonicalVector()
        val compressed = ByteArrayOutputStream().use { bytes ->
            GZIPOutputStream(bytes).use { it.write(json.toByteArray(Charsets.UTF_8)) }
            bytes.toByteArray()
        }
        val payload = CompanionConfigParser.COMPRESSED_PREFIX + Base64.getEncoder().encodeToString(compressed)

        val dto = parser.parse(payload)

        assertEquals("Home PC", dto.resourceName)
        assertEquals(2, dto.accessPaths?.size)
    }

    @Test
    fun `rejects unknown higher schemaVersion`() {
        // v2 is now supported; 3 is the first unsupported version.
        val bumped = canonicalVector().replaceFirst("\"schemaVersion\":1", "\"schemaVersion\":3")

        val e = assertThrows(CompanionConfigException::class.java) { parser.parse(bumped) }

        assertEquals(CompanionConfigException.Reason.UNSUPPORTED_VERSION, e.reason)
    }

    @Test
    fun `rejects non-sftp protocol`() {
        val wrong = canonicalVector().replaceFirst("\"protocol\":\"sftp\"", "\"protocol\":\"ftp\"")

        val e = assertThrows(CompanionConfigException::class.java) { parser.parse(wrong) }

        assertEquals(CompanionConfigException.Reason.INVALID_CONTENT, e.reason)
    }

    @Test
    fun `rejects garbage payload`() {
        val e = assertThrows(CompanionConfigException::class.java) { parser.parse("not a config at all") }

        assertEquals(CompanionConfigException.Reason.MALFORMED, e.reason)
    }

    @Test
    fun `accepts empty password`() {
        val json = "{\"schemaVersion\":1,\"resourceName\":\"Test\",\"protocol\":\"sftp\"," +
            "\"accessPaths\":[{\"kind\":\"lan\",\"host\":\"192.168.1.5\",\"port\":22}]," +
            "\"username\":\"fms\",\"password\":\"\"," +
            "\"hostKeyFingerprintSha256\":\"SHA256:8f6TQvCbXjDMOyu4A9JzKcWlEHmR5pNsGgVaU2wYqhk\"," +
            "\"roots\":[{\"virtualPath\":\"/Photos\",\"label\":\"Photos\"}]}"

        val dto = parser.parse(json)

        assertEquals("", dto.password)
    }

    @Test
    fun `accepts empty hostKeyFingerprintSha256`() {
        val json = "{\"schemaVersion\":1,\"resourceName\":\"Test\",\"protocol\":\"sftp\"," +
            "\"accessPaths\":[{\"kind\":\"lan\",\"host\":\"192.168.1.5\",\"port\":22}]," +
            "\"username\":\"fms\",\"password\":\"secret\"," +
            "\"hostKeyFingerprintSha256\":\"\"," +
            "\"roots\":[{\"virtualPath\":\"/Photos\",\"label\":\"Photos\"}]}"

        val dto = parser.parse(json)

        assertEquals("", dto.hostKeyFingerprintSha256)
    }

    @Test
    fun `rejects empty access paths`() {
        val noPaths = canonicalVector().replaceFirst(
            Regex("\"accessPaths\":\\[[^\\]]*\\]"),
            "\"accessPaths\":[]"
        )

        val e = assertThrows(CompanionConfigException::class.java) { parser.parse(noPaths) }

        assertEquals(CompanionConfigException.Reason.INVALID_CONTENT, e.reason)
        assertTrue(e.message.orEmpty().contains("accessPaths"))
    }
}
