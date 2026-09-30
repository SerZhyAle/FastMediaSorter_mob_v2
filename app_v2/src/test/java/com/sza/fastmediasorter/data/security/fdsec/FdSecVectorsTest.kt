package com.sza.fastmediasorter.data.security.fdsec

import com.google.gson.JsonObject
import com.sza.fastmediasorter.data.security.fdsec.FdSecTestSupport.hex
import com.sza.fastmediasorter.data.security.fdsec.FdSecTestSupport.resourceBytes
import com.sza.fastmediasorter.data.security.fdsec.FdSecTestSupport.toHex
import com.sza.fastmediasorter.data.security.fdsec.FdSecTestSupport.vectors
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.MessageDigest
import java.time.Instant

/**
 * Conformance against the contract's published vectors.
 *
 * The contract states outright that conformance is decided here and not by its prose: an
 * implementation that reproduces these containers is correct even where the document is unclear,
 * and one that does not is wrong even where it seemed to agree.
 */
class FdSecVectorsTest {

    @get:Rule
    val folder: TemporaryFolder = TemporaryFolder()

    private val root = vectors()

    @Test
    fun `the slow root branch matches the published value`() {
        val container = root.getAsJsonObject("containers").getAsJsonObject("empty")
        val salt = hex(container.getAsJsonObject("random_inputs").get("salt").asString)
        val credential = container.get("credential").asString

        val derived = FdSecKeySchedule().deriveRoot(credential.toByteArray(Charsets.UTF_8), salt)

        assertEquals(root.get("root_slow_branch").asString, toHex(derived))
    }

    @Test
    fun `the mask key and the kek match the published values`() {
        val container = root.getAsJsonObject("containers").getAsJsonObject("empty")
        val salt = hex(container.getAsJsonObject("random_inputs").get("salt").asString)
        val schedule = FdSecKeySchedule()
        val derived = schedule.deriveRoot(container.get("credential").asString.toByteArray(Charsets.UTF_8), salt)

        val layout = container.getAsJsonObject("layout")

        assertEquals(layout.get("mask_key").asString, toHex(schedule.maskKey(derived)))
        assertEquals(layout.get("kek").asString, toHex(schedule.kek(derived)))
    }

    @Test
    fun `the empty container is reproduced byte for byte`() {
        assertContainerReproduced("empty", "container-empty.fd-sec")
    }

    @Test
    fun `the one-byte container is reproduced byte for byte`() {
        assertContainerReproduced("onebyte", "container-onebyte.fd-sec")
    }

    @Test
    fun `the published containers unpack to their originals`() {
        assertContainerUnpacks("empty", "container-empty.fd-sec")
        assertContainerUnpacks("onebyte", "container-onebyte.fd-sec")
    }

    @Test
    fun `the vendored vectors match their provenance record`() {
        val recorded = provenanceHashes()

        assertEquals(VENDORED_RESOURCES, recorded.keys)
        for ((name, expected) in recorded) {
            val actual = toHex(MessageDigest.getInstance("SHA-256").digest(resourceBytes(name)))
            assertEquals(
                "$name differs from PROVENANCE.txt - re-vendor it from the contracts catalog",
                expected,
                actual
            )
        }
    }

    private fun provenanceHashes(): Map<String, String> =
        String(resourceBytes("PROVENANCE.txt"), Charsets.UTF_8).lineSequence()
            .map { it.trim().split(WHITESPACE) }
            .filter { it.size == PROVENANCE_ROW_FIELDS && it[0] == "sha256" }
            .associate { it[1] to it[2].lowercase() }

    private fun assertContainerReproduced(key: String, resource: String) {
        val vector = root.getAsJsonObject("containers").getAsJsonObject(key)
        val source = writeOriginal(vector)
        val destination = File(
            folder.newFolder(key + "-out"),
            source.nameWithoutExtension + FdSecFormat.CONTAINER_SUFFIX
        )

        val outcome = containerFor(vector).pack(source, destination, credentialOf(vector), stampsOf(vector))

        assertTrue("pack refused: $outcome", outcome is FdSecOutcome.Packed)
        assertArrayEquals(resourceBytes(resource), destination.readBytes())
        val layout = vector.getAsJsonObject("layout")
        assertEquals(layout.get("total_len").asLong, destination.length())
        assertEquals(layout.get("payload_offset").asInt, FdSecFormat.preLen(FdSecFormat.DEFAULT_ALIGNMENT))
    }

    private fun assertContainerUnpacks(key: String, resource: String) {
        val vector = root.getAsJsonObject("containers").getAsJsonObject(key)
        val directory = folder.newFolder(key + "-in")
        val container = File(directory, "vector" + FdSecFormat.CONTAINER_SUFFIX)
        container.writeBytes(resourceBytes(resource))
        val target = folder.newFolder(key + "-restored")

        val outcome = FdSecContainer().unpack(container, target, credentialOf(vector))

        assertTrue("unpack refused: $outcome", outcome is FdSecOutcome.Unpacked)
        val unpacked = outcome as FdSecOutcome.Unpacked
        assertEquals(vector.get("name").asString, unpacked.metadata.originalName)
        assertEquals(vector.get("size").asLong, unpacked.metadata.realSize)
        assertArrayEquals(hex(vector.get("content").asString), unpacked.restored.readBytes())
    }

    private fun containerFor(vector: JsonObject): FdSecContainer {
        val inputs = vector.getAsJsonObject("random_inputs")
        val queued = QueuedRandomSource(
            hex(inputs.get("salt").asString),
            hex(inputs.get("file_key").asString),
            hex(inputs.get("wrap_nonce").asString),
            hex(inputs.get("meta_pad").asString),
            hex(inputs.get("align_pad").asString),
            hex(inputs.get("tail_pad").asString),
        )
        return FdSecContainer(randomSource = queued)
    }

    private fun writeOriginal(vector: JsonObject): File {
        val source = File(folder.newFolder(vector.get("name").asString + "-src"), vector.get("name").asString)
        source.writeBytes(hex(vector.get("content").asString))
        return source
    }

    private fun credentialOf(vector: JsonObject): CharArray = vector.get("credential").asString.toCharArray()

    private fun stampsOf(vector: JsonObject): FdSecStamps = FdSecStamps(
        encryptedAtFileTime = fileTime(vector.get("encoded_at").asString),
        createdFileTime = fileTime(vector.get("created_at").asString),
        accessedFileTime = fileTime(vector.get("accessed_at").asString),
        modifiedFileTime = fileTime(vector.get("modified_at").asString),
    )

    private fun fileTime(iso: String): Long = FdSecMetadata.millisToFileTime(Instant.parse(iso).toEpochMilli())

    private companion object {
        const val PROVENANCE_ROW_FIELDS = 3
        val WHITESPACE = Regex("\\s+")
        val VENDORED_RESOURCES = setOf("vectors.json", "container-empty.fd-sec", "container-onebyte.fd-sec")
    }
}
