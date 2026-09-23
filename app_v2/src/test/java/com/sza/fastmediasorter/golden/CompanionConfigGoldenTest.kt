package com.sza.fastmediasorter.golden

import com.sza.fastmediasorter.data.companion.CompanionConfigParser
import com.sza.fastmediasorter.data.companion.CompanionConfigSerializer
import com.sza.fastmediasorter.data.companion.CompanionResourceTokens
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.ResourceProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S3371 phase 06 step 06.3: golden proof that the consumer half of the frozen `.fmscfg` contract
 * still reads what shipped companions wrote.
 *
 * SCOPE. The authoritative format text is the companion repo's `docs/CONFIG_FORMAT.md`, which is not
 * in this tree, so nothing here asserts companion-side history. Every fixture is built only from
 * what the consumer - [CompanionConfigParser] - demonstrably accepts and from the format facts its
 * own source states:
 *
 * - `companion_v1.fmscfg` is `schemaVersion` 1, the parser's `MIN_SCHEMA_VERSION`. Its roots carry
 *   only `virtualPath` and `label`, which [com.sza.fastmediasorter.data.companion.CompanionRootDto]
 *   calls "the frozen v1 fields"; every other root field is an S1002 v2 addition.
 * - `companion_v2.fmscfg` is `schemaVersion` 2, the parser's `SUPPORTED_SCHEMA_VERSION`, with the
 *   S1002 per-root params, the S1014 `accessNote`, the S1016 `readOnly`/`isDestination` pair, and
 *   the S0984 passwordless share (empty `password`, empty `hostKeyFingerprintSha256`) the parser
 *   names as valid Android-side.
 * - `companion_v2_compressed.fmscfg` is the same v2 payload in the second transport the parser
 *   declares - `FMSCFG1:` + base64(gzip(json)), the S1039 dense QR form.
 *
 * The frozen canonical vector shared with the companion repo lives in
 * `CompanionConfigParserTest`; these fixtures are the compatibility half beside it, not a copy.
 */
class CompanionConfigGoldenTest {

    private val parser = CompanionConfigParser()
    private val serializer = CompanionConfigSerializer()

    @Test
    fun `a v1 config parses with every v2 root field absent`() {
        val dto = parser.parse(readFixture("companion_v1.fmscfg"))

        assertEquals(1, dto.schemaVersion)
        assertEquals("Studio desktop", dto.resourceName)
        assertEquals("studio", dto.username)
        assertEquals(2, dto.accessPaths.orEmpty().size)
        assertEquals("10.0.0.14", dto.accessPaths?.first()?.host)
        assertEquals(2222, dto.accessPaths?.first()?.port)
        assertEquals(listOf("/Renders", "/Sources"), dto.roots.orEmpty().map { it.virtualPath })

        val renders = dto.roots.orEmpty().first()
        assertNull("a v1 root carries no S1002 profile", renders.profile)
        assertNull("a v1 root carries no S1002 media types", renders.mediaTypes)
        assertNull("a v1 root carries no S1016 readOnly flag", renders.readOnly)
        assertNull("a v1 config carries no S1014 access note", dto.accessNote)
        assertTrue("absent readOnly plus absent isDestination resolves read-only", renders.resolveReadOnly())
    }

    @Test
    fun `a v1 config keeps its pinned server data`() {
        val dto = parser.parse(readFixture("companion_v1.fmscfg"))

        assertEquals("Xq4TmR8pLz2VnKe7", dto.password)
        assertEquals(
            "SHA256:2b9QlWc0ZxPnMhTj6RyUaFdKgEsVoI1LrBtXpNmC4hY",
            dto.hostKeyFingerprintSha256
        )
        assertEquals("portforward", dto.accessPaths?.get(1)?.kind)
        assertEquals("198.51.100.22", dto.accessPaths?.get(1)?.host)
    }

    @Test
    fun `a v2 config parses its per-root resource params and write rule`() {
        val dto = parser.parse(readFixture("companion_v2.fmscfg"))

        assertEquals(2, dto.schemaVersion)
        assertEquals(3, dto.roots.orEmpty().size)

        val renders = dto.roots.orEmpty()[0]
        assertEquals(ResourceProfile.PHOTO_STORAGE, CompanionResourceTokens.profileFromToken(renders.profile))
        assertEquals(listOf(MediaType.IMAGE), renders.mediaTypes.orEmpty().mapNotNull(::mediaTypeOf))
        assertEquals("4821", renders.accessPin)
        assertEquals(12, renders.slideshowInterval)
        assertFalse("readOnly=false makes the root writable", renders.resolveReadOnly())

        val approved = dto.roots.orEmpty()[1]
        assertFalse("isDestination=true makes the root writable whatever readOnly says", approved.resolveReadOnly())
        assertEquals(true, approved.isDestination)

        val sources = dto.roots.orEmpty()[2]
        assertTrue("a v1-shaped root inside a v2 file stays read-only", sources.resolveReadOnly())
    }

    @Test
    fun `a v2 config carries the access note and the passwordless share the parser allows`() {
        val dto = parser.parse(readFixture("companion_v2.fmscfg"))

        assertEquals("", dto.password)
        assertEquals("", dto.hostKeyFingerprintSha256)
        assertEquals(
            "Open port 2222 on the studio router before connecting from outside.",
            dto.accessNote
        )
    }

    @Test
    fun `the compressed transport decodes to the same config as the plain one`() {
        val plain = parser.parse(readFixture("companion_v2.fmscfg"))
        val compressed = parser.parse(readFixture("companion_v2_compressed.fmscfg"))

        val raw = readFixture("companion_v2_compressed.fmscfg").trim()
        assertTrue(raw.startsWith(CompanionConfigParser.COMPRESSED_PREFIX))
        assertEquals(plain, compressed)
    }

    @Test
    fun `a config this build re-serializes is still readable in both transports`() {
        listOf("companion_v1.fmscfg", "companion_v2.fmscfg").forEach { fixture ->
            val dto = parser.parse(readFixture(fixture))

            assertEquals(
                "$fixture lost data through the plain transport",
                dto,
                parser.parse(serializer.serialize(dto))
            )
            assertEquals(
                "$fixture lost data through the compressed transport",
                dto,
                parser.parse(serializer.serializeCompressed(dto))
            )
        }
    }

    private fun mediaTypeOf(token: String): MediaType? = CompanionResourceTokens.mediaTypeFromToken(token)

    private fun readFixture(fileName: String): String {
        val path = "golden/companion/$fileName"
        return checkNotNull(javaClass.classLoader?.getResourceAsStream(path)) {
            "Golden fixture $path is missing from the test resources"
        }.bufferedReader().use { it.readText() }
    }
}
