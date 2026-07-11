package com.sza.fastmediasorter.data.companion

import org.junit.Assert.assertEquals
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
        val bumped = canonicalVector().replaceFirst("\"schemaVersion\":1", "\"schemaVersion\":99")

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
