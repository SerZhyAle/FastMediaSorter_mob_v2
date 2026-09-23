package com.sza.fastmediasorter.data.security.fdsec

import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.random.Random

/**
 * Every row of the contract's tamper checklist, each asserting the stated outcome class and only it.
 *
 * The distinction is the contract: a message must never claim "wrong password" for what might be
 * damage, nor "damaged" for what might be a wrong password.
 */
class FdSecTamperTest {

    @get:Rule
    val folder: TemporaryFolder = TemporaryFolder()

    private val cheapProfile = FdSecKdfProfile(thresholdBytes = 64, memoryKib = 64, iterations = 1, parallelism = 1)

    @Test
    fun `a flipped bit in the head is wrong-credential-or-tamper`() {
        assertOutcomeAfter(::flipHeadBit) { it is FdSecOutcome.WrongCredentialOrTamper }
    }

    @Test
    fun `a flipped bit in the sealed metadata is wrong-credential-or-tamper`() {
        assertOutcomeAfter({ flipAt(it, FdSecFormat.HEAD_SIZE + 3) }) { it is FdSecOutcome.WrongCredentialOrTamper }
    }

    @Test
    fun `a flipped bit in a chunk is wrong-credential-or-tamper`() {
        assertOutcomeAfter({ flipAt(it, FdSecFormat.preLen(SMALL_ALIGNMENT) + 2) }) {
            it is FdSecOutcome.WrongCredentialOrTamper
        }
    }

    @Test
    fun `two swapped chunks are wrong-credential-or-tamper`() {
        assertOutcomeAfter(::swapFirstTwoChunks) { it is FdSecOutcome.WrongCredentialOrTamper }
    }

    @Test
    fun `a removed final chunk is damaged`() {
        assertOutcomeAfter(::dropFinalChunk) { it is FdSecOutcome.Damaged }
    }

    @Test
    fun `a file truncated mid-chunk is damaged`() {
        assertOutcomeAfter({ truncateBy(it, 3) }) { it is FdSecOutcome.Damaged }
    }

    @Test
    fun `a wrong credential is wrong-credential-or-tamper and never damaged`() {
        val container = packFixture()

        val outcome = container(PASSPHRASE).unpack(container, folder.newFolder("wrong-out"), "not it".toCharArray())

        assertTrue("expected the first outcome class, got $outcome", outcome is FdSecOutcome.WrongCredentialOrTamper)
    }

    @Test
    fun `a random file of plausible length is wrong-credential-or-tamper`() {
        val noise = File(folder.newFolder("noise"), "noise.bin")
        noise.writeBytes(Random(SEED).nextBytes(PLAUSIBLE_LENGTH))

        val outcome = container(PASSPHRASE).unpack(noise, folder.newFolder("noise-out"), PASSPHRASE.toCharArray())

        assertTrue("expected the first outcome class, got $outcome", outcome is FdSecOutcome.WrongCredentialOrTamper)
    }

    @Test
    fun `a file below the smallest possible container is damaged`() {
        val short = File(folder.newFolder("short"), "short.bin")
        short.writeBytes(ByteArray(SHORT_LENGTH))

        val outcome = container(PASSPHRASE).unpack(short, folder.newFolder("short-out"), PASSPHRASE.toCharArray())

        assertTrue("expected damaged, got $outcome", outcome is FdSecOutcome.Damaged)
    }

    @Test
    fun `a length that is not a whole number of clusters is damaged`() {
        val odd = File(folder.newFolder("odd"), "odd.bin")
        odd.writeBytes(ByteArray(PLAUSIBLE_LENGTH + 1))

        val outcome = container(PASSPHRASE).unpack(odd, folder.newFolder("odd-out"), PASSPHRASE.toCharArray())

        assertTrue("expected damaged, got $outcome", outcome is FdSecOutcome.Damaged)
    }

    private fun assertOutcomeAfter(mutate: (File) -> Unit, expected: (FdSecOutcome) -> Boolean) {
        val fixture = packFixture()
        mutate(fixture)

        val outcome = container(
            PASSPHRASE
        ).unpack(fixture, folder.newFolder("out-" + fixture.parentFile.name), PASSPHRASE.toCharArray())

        assertTrue("unexpected outcome $outcome", expected(outcome))
    }

    private fun packFixture(): File {
        val source = File(folder.newFolder("src-" + folder.root.list()?.size), "payload.bin")
        source.writeBytes(ByteArray(SMALL_CHUNK * 3 + 5) { (it % 97).toByte() })
        val destination = File(folder.newFolder("box-" + folder.root.list()?.size), "payload.fd-sec")
        val outcome = container(PASSPHRASE).pack(source, destination, PASSPHRASE.toCharArray())
        check(outcome is FdSecOutcome.Packed) { "the fixture did not pack: $outcome" }
        return destination
    }

    private fun flipHeadBit(file: File) = flipAt(file, 20)

    private fun flipAt(file: File, offset: Int) {
        val bytes = file.readBytes()
        bytes[offset] = (bytes[offset].toInt() xor 1).toByte()
        file.writeBytes(bytes)
    }

    private fun swapFirstTwoChunks(file: File) {
        val bytes = file.readBytes()
        val first = FdSecFormat.preLen(SMALL_ALIGNMENT)
        val second = first + SMALL_CHUNK
        val held = bytes.copyOfRange(first, second)
        bytes.copyInto(bytes, first, second, second + SMALL_CHUNK)
        held.copyInto(bytes, second)
        file.writeBytes(bytes)
    }

    private fun dropFinalChunk(file: File) {
        val bytes = file.readBytes()
        val keep = FdSecFormat.preLen(SMALL_ALIGNMENT) + SMALL_CHUNK * 3
        val trimmed = bytes.copyOf(keep)
        file.writeBytes(trimmed)
    }

    private fun truncateBy(file: File, clusters: Int) {
        val bytes = file.readBytes()
        file.writeBytes(bytes.copyOf(bytes.size - clusters * SMALL_ALIGNMENT - 1))
    }

    private fun container(credential: String): FdSecContainer {
        check(credential.isNotEmpty()) { "the fixture credential must not be empty" }
        return FdSecContainer(
            schedule = FdSecKeySchedule(cheapProfile),
            chunkSize = SMALL_CHUNK,
            alignment = SMALL_ALIGNMENT,
        )
    }

    private companion object {
        const val SMALL_ALIGNMENT = 512
        const val SMALL_CHUNK = 1024
        const val PASSPHRASE = "correct horse battery staple"
        const val PLAUSIBLE_LENGTH = 12288
        const val SHORT_LENGTH = 5631
        const val SEED = 20260922
    }
}
