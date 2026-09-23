package com.sza.fastmediasorter.domain.usecase.companion

import android.content.Context
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.sza.fastmediasorter.data.companion.CompanionConfigParser
import com.sza.fastmediasorter.data.companion.CompanionConfigSerializer
import com.sza.fastmediasorter.data.local.db.NetworkCredentialsEntity
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.usecase.SmbOperationsUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.util.Base64
import java.util.zip.GZIPInputStream

/**
 * S3447: producer half of `FMSCFG` rule 5 for this app's exporter.
 *
 * The exporter cannot emit the catalog's canonical inputs (it writes one root and one `lan` path per
 * resource), so - as the 2026-09-22 per-producer proposal in the contracts catalog asks - it is pinned to
 * its OWN bytes for stated inputs, and every way those bytes differ from the canonical vectors is named:
 * key order is the canonical one, every key is a documented one, and the only v2 fields written at their
 * import default are the itemized ones below. `createdAt` is the one value that is not a function of the
 * input, so it is shape-checked and then masked.
 *
 * A change to an expected string here is a change to what the app writes at a contract boundary - agree it
 * with the contract owner first (docs/contracts/FMSCFG.md).
 */
class ExportCompanionConfigConformanceTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val parser = CompanionConfigParser()
    private val serializer = CompanionConfigSerializer()

    @Test
    fun `serializer re-emits both canonical vectors byte for byte`() {
        listOf(CANONICAL_V1, CANONICAL_V2).forEach { resource ->
            val vector = readResource(resource)

            assertEquals(resource, vector, serializer.serialize(parser.parse(vector)))
        }
    }

    @Test
    fun `read-only export writes its pinned bytes`() = runTest {
        val payload = exportQrJson(readOnlyResource, includePassword = true)

        assertEquals(EXPECTED_READ_ONLY, maskCreatedAt(payload))
    }

    @Test
    fun `writable destination export writes its pinned bytes with the password excluded`() = runTest {
        val export = useCase().exportQrPayload(destinationResource, includePassword = false).getOrThrow()

        assertFalse(export.passwordIncluded)
        assertEquals(EXPECTED_DESTINATION, maskCreatedAt(inflate(export.payload)))
    }

    @Test
    fun `file transport writes the same bytes as the QR transport`() = runTest {
        val file = useCase().invoke(readOnlyResource, includePassword = true).getOrThrow()

        assertEquals(EXPECTED_READ_ONLY, maskCreatedAt(file.readText(Charsets.UTF_8)))
    }

    @Test
    fun `top-level keys follow the canonical order`() = runTest {
        val canonicalKeys = keysOf(readResource(CANONICAL_V2))
        assertEquals(canonicalKeys, keysOf(readResource(CANONICAL_V1)))

        listOf(readOnlyResource, destinationResource).forEach { resource ->
            assertEquals(resource.name, canonicalKeys, keysOf(exportQrJson(resource, includePassword = true)))
        }
    }

    @Test
    fun `every root key is documented and in canonical order`() = runTest {
        val canonicalRoots = rootsOf(readResource(CANONICAL_V2))
        canonicalRoots.forEach { assertIsOrderedSubsetOfContract(it.keySet().toList()) }

        listOf(readOnlyResource, destinationResource).forEach { resource ->
            rootsOf(exportQrJson(resource, includePassword = true)).forEach {
                assertIsOrderedSubsetOfContract(it.keySet().toList())
            }
        }
    }

    @Test
    fun `schemaVersion 2 is written only because a root carries a v2 field`() = runTest {
        listOf(readOnlyResource, destinationResource).forEach { resource ->
            val json = exportQrJson(resource, includePassword = true)

            assertEquals(2, JsonParser.parseString(json).asJsonObject.get("schemaVersion").asInt)
            assertTrue(resource.name, rootsOf(json).any { root -> root.keySet().any { it in V2_ROOT_DEFAULTS } })
        }
    }

    @Test
    fun `only the itemized v2 fields are written at their import default`() = runTest {
        assertEquals(
            setOf("slideshowInterval"),
            fieldsAtImportDefault(exportQrJson(readOnlyResource, includePassword = true))
        )
        assertEquals(
            setOf("scanSubdirectories", "slideshowInterval"),
            fieldsAtImportDefault(exportQrJson(destinationResource, includePassword = true))
        )
    }

    private suspend fun exportQrJson(resource: MediaResource, includePassword: Boolean): String =
        inflate(useCase().exportQrPayload(resource, includePassword).getOrThrow().payload)

    private fun useCase(): ExportCompanionConfigUseCase {
        val context = mockk<Context> { every { cacheDir } returns tempFolder.root }
        val credentials = mockk<NetworkCredentialsEntity> {
            every { username } returns "fms"
            every { password } returns "k7PmQ2wXr9TzS4vGnHb3JdLe"
        }
        val smbOperations = mockk<SmbOperationsUseCase> {
            coEvery { getSftpCredentials(CREDENTIALS_ID) } returns Result.success(credentials)
        }
        return ExportCompanionConfigUseCase(context, serializer, smbOperations, Dispatchers.Unconfined)
    }

    private fun inflate(payload: String): String {
        assertTrue(payload.startsWith(CompanionConfigParser.COMPRESSED_PREFIX))
        val compressed = Base64.getDecoder().decode(payload.removePrefix(CompanionConfigParser.COMPRESSED_PREFIX))
        return GZIPInputStream(ByteArrayInputStream(compressed)).use { it.readBytes().toString(Charsets.UTF_8) }
    }

    private fun maskCreatedAt(json: String): String {
        val match = CREATED_AT.find(json)
        assertTrue("createdAt must be an RFC 3339 UTC timestamp: $json", match != null)
        return json.replace(CREATED_AT, "\"createdAt\":\"$MASK\"")
    }

    private fun keysOf(json: String): List<String> = JsonParser.parseString(json).asJsonObject.keySet().toList()

    private fun rootsOf(json: String): List<JsonObject> =
        JsonParser.parseString(json).asJsonObject.getAsJsonArray("roots").map { it.asJsonObject }

    private fun fieldsAtImportDefault(json: String): Set<String> = rootsOf(json).flatMap { root ->
        V2_ROOT_DEFAULTS.filter { (key, default) -> root.get(key)?.toString() == default }.keys
    }.toSet()

    private fun assertIsOrderedSubsetOfContract(keys: List<String>) {
        val positions = keys.map { ROOT_KEY_ORDER.indexOf(it) }
        assertFalse("undocumented root key in $keys", positions.contains(-1))
        assertEquals("root keys out of contract order: $keys", positions.sorted(), positions)
    }

    private fun readResource(name: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream(name)) { "$name test resource missing" }
            .bufferedReader().use { it.readText() }.trim()

    private companion object {
        const val CANONICAL_V1 = "companion/canonical_vector.json"
        const val CANONICAL_V2 = "companion/canonical_vector_v2.json"
        const val CREDENTIALS_ID = "cred-1"
        const val MASK = "<createdAt>"
        const val FINGERPRINT = "SHA256:8f6TQvCbXjDMOyu4A9JzKcWlEHmR5pNsGgVaU2wYqhk"
        val CREATED_AT = Regex("\"createdAt\":\"\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z\"")

        // v1 fields, the 2.1 readOnly, then the v2 table - the order CONFIG_FORMAT.md documents them in.
        val ROOT_KEY_ORDER = listOf(
            "virtualPath", "label", "readOnly", "profile", "mediaTypes", "scanSubdirectories",
            "showSubfoldersAsItems", "showHiddenFiles", "allFiles", "isDestination", "destinationColor",
            "comment", "accessPin", "slideshowInterval"
        )

        // CONFIG_FORMAT.md v2 table, "Default on import", as JSON literals; fields with no fixed default omitted.
        val V2_ROOT_DEFAULTS = mapOf(
            "profile" to "\"none\"",
            "scanSubdirectories" to "true",
            "showSubfoldersAsItems" to "false",
            "showHiddenFiles" to "false",
            "allFiles" to "false",
            "isDestination" to "false",
            "slideshowInterval" to "10"
        )

        val readOnlyResource = MediaResource(
            name = "Home PC",
            path = "sftp://192.168.1.23:2022/Photos",
            type = ResourceType.SFTP,
            credentialsId = CREDENTIALS_ID,
            supportedMediaTypes = linkedSetOf(MediaType.IMAGE, MediaType.VIDEO),
            isReadOnly = true,
            hostKeyFingerprint = FINGERPRINT
        )

        val destinationResource = MediaResource(
            name = "Drop Box",
            path = "sftp://192.168.1.23:2022/Inbox",
            type = ResourceType.SFTP,
            credentialsId = CREDENTIALS_ID,
            isDestination = true,
            destinationColor = -14575885,
            isReadOnly = false,
            scanSubdirectories = true,
            allFiles = true,
            comment = "Move target",
            hostKeyFingerprint = FINGERPRINT
        )

        const val EXPECTED_READ_ONLY = "{\"schemaVersion\":2,\"resourceName\":\"Home PC\",\"protocol\":\"sftp\"," +
            "\"accessPaths\":[{\"kind\":\"lan\",\"host\":\"192.168.1.23\",\"port\":2022}],\"username\":\"fms\"," +
            "\"password\":\"k7PmQ2wXr9TzS4vGnHb3JdLe\",\"hostKeyFingerprintSha256\":\"$FINGERPRINT\"," +
            "\"roots\":[{\"virtualPath\":\"/Photos\",\"label\":\"Home PC\",\"mediaTypes\":[\"image\",\"video\"]," +
            "\"scanSubdirectories\":false,\"slideshowInterval\":10}],\"createdAt\":\"$MASK\"}"

        const val EXPECTED_DESTINATION = "{\"schemaVersion\":2,\"resourceName\":\"Drop Box\",\"protocol\":\"sftp\"," +
            "\"accessPaths\":[{\"kind\":\"lan\",\"host\":\"192.168.1.23\",\"port\":2022}],\"username\":\"fms\"," +
            "\"password\":\"\",\"hostKeyFingerprintSha256\":\"$FINGERPRINT\"," +
            "\"roots\":[{\"virtualPath\":\"/Inbox\",\"label\":\"Drop Box\",\"readOnly\":false," +
            "\"scanSubdirectories\":true,\"allFiles\":true,\"isDestination\":true,\"destinationColor\":-14575885," +
            "\"comment\":\"Move target\",\"slideshowInterval\":10}],\"createdAt\":\"$MASK\"}"
    }
}
