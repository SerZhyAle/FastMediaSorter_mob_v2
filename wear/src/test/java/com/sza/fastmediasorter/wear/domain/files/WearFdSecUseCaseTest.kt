package com.sza.fastmediasorter.wear.domain.files

import com.sza.fastmediasorter.wear.domain.model.WearFdSecResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNoException
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.nio.file.Files

/**
 * S3383: what the watch's own layer owes on top of the shared crypto core.
 *
 * The format itself is proven by the contract's conformance vectors, which this module runs from the
 * same source; what is asserted here is the policy around it - the original surviving, the visible
 * name saying nothing, a collision suffixed, and each refusal reported as itself.
 */
class WearFdSecUseCaseTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val useCase = WearFdSecUseCase()

    @Test
    fun `packs and restores the original byte for byte`() = runTest {
        val original = folder.newFile("holiday.jpg")
        val bytes = ByteArray(SAMPLE_SIZE) { (it % Byte.MAX_VALUE).toByte() }
        original.writeBytes(bytes)

        val packed = useCase.pack(original, credential())
        assertTrue(packed is WearFdSecResult.Packed)
        val container = (packed as WearFdSecResult.Packed).container
        assertTrue("the original must survive a pack", original.isFile)

        original.delete()
        val restored = useCase.restoreBeside(container, credential())
        assertTrue(restored is WearFdSecResult.Restored)
        val recovered = (restored as WearFdSecResult.Restored).file
        assertEquals("holiday.jpg", recovered.name)
        assertArrayEquals(bytes, recovered.readBytes())
    }

    @Test
    fun `the container name carries no trace of the original extension`() = runTest {
        val original = folder.newFile("secret.mp4")
        original.writeBytes(ByteArray(SAMPLE_SIZE))

        val packed = useCase.pack(original, credential()) as WearFdSecResult.Packed

        assertEquals("secret.fd-sec", packed.container.name)
        assertTrue(useCase.isContainer(packed.container.name))
    }

    @Test
    fun `a name already taken is suffixed rather than overwritten`() = runTest {
        val original = folder.newFile("note.txt")
        original.writeBytes(ByteArray(SAMPLE_SIZE))
        val occupied = File(folder.root, "note.fd-sec")
        occupied.writeBytes(ByteArray(SAMPLE_SIZE) { 1 })

        val packed = useCase.pack(original, credential()) as WearFdSecResult.Packed

        assertEquals("note-1.fd-sec", packed.container.name)
        assertArrayEquals(ByteArray(SAMPLE_SIZE) { 1 }, occupied.readBytes())
    }

    @Test
    fun `a wrong credential is reported as the one class that names all three readings`() = runTest {
        val original = folder.newFile("clip.mp3")
        original.writeBytes(ByteArray(SAMPLE_SIZE))
        val container = (useCase.pack(original, credential()) as WearFdSecResult.Packed).container

        val opened = useCase.restoreBeside(container, "not-the-one".toCharArray())

        assertEquals(WearFdSecResult.WrongCredentialOrTamper, opened)
    }

    @Test
    fun `a file that never was a container is refused without a guess`() = runTest {
        val stranger = folder.newFile("stranger.fd-sec")
        stranger.writeBytes(ByteArray(SAMPLE_SIZE) { 7 })

        val opened = useCase.restoreBeside(stranger, credential())

        assertTrue(
            opened is WearFdSecResult.Damaged || opened == WearFdSecResult.WrongCredentialOrTamper
        )
    }

    @Test
    fun `a container is never packed again and a missing file is refused`() = runTest {
        val container = folder.newFile("already.fd-sec")
        val missing = File(folder.root, "gone.png")

        assertTrue(useCase.pack(container, credential()) is WearFdSecResult.Failed)
        assertTrue(useCase.pack(missing, credential()) is WearFdSecResult.Failed)
    }

    @Test
    fun `a regular file under a linked folder is packed, not refused as a link`() = runTest {
        val realFolder = folder.newFolder("real")
        val linkedFolder = linkOrSkip(File(folder.root, "linked"), realFolder)
        File(realFolder, "voice.m4a").writeBytes(ByteArray(SAMPLE_SIZE))

        val packed = useCase.pack(File(linkedFolder, "voice.m4a"), credential())

        assertTrue("got $packed", packed is WearFdSecResult.Packed)
    }

    @Test
    fun `a link to a file is still refused`() = runTest {
        val target = folder.newFile("target.jpg")
        target.writeBytes(ByteArray(SAMPLE_SIZE))
        val link = linkOrSkip(File(folder.root, "pointer.jpg"), target)

        assertTrue(useCase.pack(link, credential()) is WearFdSecResult.Failed)
    }

    @Test
    fun `staging folders left by a killed restore are swept`() {
        val leftover = File(folder.root, ".fdsec-restore-12345")
        assertTrue(leftover.mkdirs())

        useCase.sweepStaging(folder.root)

        assertFalse(leftover.exists())
    }

    @Test
    fun `a restore first clears the staging a killed restore left beside the container`() = runTest {
        val original = folder.newFile("diary.txt")
        original.writeBytes(ByteArray(SAMPLE_SIZE))
        val container = (useCase.pack(original, credential()) as WearFdSecResult.Packed).container
        original.delete()
        val leftover = File(folder.root, ".fdsec-restore-1")
        assertTrue(leftover.mkdirs())
        File(leftover, "diary.txt").writeBytes(ByteArray(SAMPLE_SIZE))

        val restored = useCase.restoreBeside(container, credential())

        assertTrue(restored is WearFdSecResult.Restored)
        assertFalse(leftover.exists())
    }

    private fun credential(): CharArray = "watch-pass".toCharArray()

    /** Windows without developer mode refuses to create a link; the test is skipped there, not failed. */
    private fun linkOrSkip(link: File, target: File): File {
        try {
            Files.createSymbolicLink(link.toPath(), target.toPath())
        } catch (e: IOException) {
            assumeNoException(e)
        } catch (e: UnsupportedOperationException) {
            assumeNoException(e)
        }
        return link
    }

    private companion object {
        const val SAMPLE_SIZE = 64
    }
}
