package com.sza.fastmediasorter.data.security.fdsec

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Round trips under a cheap KDF profile and a small cluster, so a multi-chunk original stays fast.
 *
 * The profile is a test seam of the contract; no shipped path constructs anything but
 * [FdSecKdfProfile.V1].
 */
class FdSecRoundTripTest {

    @get:Rule
    val folder: TemporaryFolder = TemporaryFolder()

    private val cheapProfile = FdSecKdfProfile(thresholdBytes = 64, memoryKib = 64, iterations = 1, parallelism = 1)

    @Test
    fun `an empty original round-trips as one chunk of length zero`() {
        assertRoundTrip(ByteArray(0), "empty.txt", PASSPHRASE)
    }

    @Test
    fun `a one-byte original round-trips`() {
        assertRoundTrip(byteArrayOf(0x41), "one.bin", PASSPHRASE)
    }

    @Test
    fun `a multi-chunk original round-trips`() {
        val payload = ByteArray(SMALL_CHUNK * 3 + 7) { (it % 251).toByte() }
        assertRoundTrip(payload, "many.bin", PASSPHRASE)
    }

    @Test
    fun `a long credential takes the fast branch and still round-trips`() {
        val long = "x".repeat(cheapProfile.thresholdBytes + 1)
        assertRoundTrip(byteArrayOf(1, 2, 3), "fast.bin", long)
    }

    @Test
    fun `an empty credential is accepted and round-trips`() {
        assertRoundTrip(byteArrayOf(9), "obfuscated.bin", "")
    }

    @Test
    fun `packing keeps the original untouched`() {
        val source = sourceFile(byteArrayOf(7, 7, 7), "kept.bin")
        val before = source.readBytes()

        container().pack(source, File(folder.newFolder("out"), "kept.fd-sec"), PASSPHRASE.toCharArray())

        assertTrue(source.exists())
        assertArrayEquals(before, source.readBytes())
    }

    @Test
    fun `a container is never written over an existing file`() {
        val source = sourceFile(byteArrayOf(1), "collide.bin")
        val destination = File(folder.newFolder("collide-out"), "collide.fd-sec")
        destination.writeBytes(byteArrayOf(0))

        val outcome = container().pack(source, destination, PASSPHRASE.toCharArray())

        assertTrue("expected a refusal, got $outcome", outcome is FdSecOutcome.Failed)
        assertEquals(1, destination.length())
    }

    private fun assertRoundTrip(content: ByteArray, name: String, credential: String) {
        val source = sourceFile(content, name)
        val destination =
            File(folder.newFolder("$name-out"), name.substringBeforeLast('.') + FdSecFormat.CONTAINER_SUFFIX)

        val packed = container().pack(source, destination, credential.toCharArray())
        assertTrue("pack refused: $packed", packed is FdSecOutcome.Packed)
        assertEquals(0L, destination.length() % FdSecFormat.MIN_ALIGNMENT)

        val restoreInto = folder.newFolder("$name-restored")
        val unpacked = container().unpack(destination, restoreInto, credential.toCharArray())

        assertTrue("unpack refused: $unpacked", unpacked is FdSecOutcome.Unpacked)
        val result = unpacked as FdSecOutcome.Unpacked
        assertEquals(name, result.metadata.originalName)
        assertEquals(content.size.toLong(), result.metadata.realSize)
        assertArrayEquals(content, result.restored.readBytes())
    }

    private fun sourceFile(content: ByteArray, name: String): File {
        val file = File(folder.newFolder("$name-src"), name)
        file.writeBytes(content)
        return file
    }

    private fun container(): FdSecContainer = FdSecContainer(
        schedule = FdSecKeySchedule(cheapProfile),
        chunkSize = SMALL_CHUNK,
        alignment = SMALL_ALIGNMENT,
    )

    private companion object {
        const val SMALL_ALIGNMENT = 512
        const val SMALL_CHUNK = 1024
        const val PASSPHRASE = "correct horse battery staple"
    }
}
