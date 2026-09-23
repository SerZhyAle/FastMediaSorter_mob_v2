package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.domain.model.FdSecResult
import com.sza.fastmediasorter.domain.transfer.SiblingFolder
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class PlaceVerifiedFileBesideUseCaseTest {

    @get:Rule
    val folder: TemporaryFolder = TemporaryFolder()

    private val useCase = PlaceVerifiedFileBesideUseCase()

    @Test
    fun `a proven file lands under its name with identical bytes and no temporary child`() = runTest {
        val remote = FakeFolder()

        val result = useCase(proven(), remote, "holiday.fd-sec", folder.newFolder("scratch"))

        assertEquals(FdSecResult.Placed("holiday.fd-sec"), result)
        assertEquals(setOf("holiday.fd-sec"), remote.children.keys)
        assertArrayEquals(PAYLOAD, remote.children.getValue("holiday.fd-sec"))
    }

    @Test
    fun `a taken name is suffixed and the existing child is untouched`() = runTest {
        val remote = FakeFolder()
        remote.children["holiday.png"] = EXISTING
        remote.children["holiday-1.png"] = EXISTING

        val result = useCase(proven(), remote, "holiday.png", folder.newFolder("scratch"))

        assertEquals(FdSecResult.Placed("holiday-2.png"), result)
        assertArrayEquals(EXISTING, remote.children.getValue("holiday.png"))
        assertArrayEquals(EXISTING, remote.children.getValue("holiday-1.png"))
        assertArrayEquals(PAYLOAD, remote.children.getValue("holiday-2.png"))
    }

    @Test
    fun `a read-back that differs leaves neither the final nor the temporary child`() = runTest {
        val remote = FakeFolder(corruptRead = true)

        val result = useCase(proven(), remote, "holiday.fd-sec", folder.newFolder("scratch"))

        assertTrue(result is FdSecResult.Failed)
        assertTrue(remote.children.isEmpty())
    }

    @Test
    fun `a failed write leaves the folder as it was`() = runTest {
        val remote = FakeFolder(failWrite = true)

        val result = useCase(proven(), remote, "holiday.fd-sec", folder.newFolder("scratch"))

        assertTrue(result is FdSecResult.Failed)
        assertTrue(remote.children.isEmpty())
    }

    @Test
    fun `a failed rename removes the temporary child`() = runTest {
        val remote = FakeFolder(failRename = true)

        val result = useCase(proven(), remote, "holiday.fd-sec", folder.newFolder("scratch"))

        assertTrue(result is FdSecResult.Failed)
        assertTrue(remote.children.isEmpty())
    }

    @Test
    fun `the read-back copy is gone from scratch afterwards`() = runTest {
        val scratch = folder.newFolder("scratch")

        useCase(proven(), FakeFolder(), "holiday.fd-sec", scratch)

        assertTrue(scratch.listFiles().isNullOrEmpty())
    }

    private fun proven(): File = folder.newFile("proven").apply { writeBytes(PAYLOAD) }

    /** A folder whose address for a child is its name. */
    private class FakeFolder(
        private val failWrite: Boolean = false,
        private val corruptRead: Boolean = false,
        private val failRename: Boolean = false,
    ) : SiblingFolder {
        val children = linkedMapOf<String, ByteArray>()

        override suspend fun contains(name: String): Boolean = name in children

        override suspend fun write(source: File, name: String): String {
            if (failWrite) throw IOException("refused")
            check(name !in children) { "overwrite" }
            children[name] = source.readBytes()
            return name
        }

        override suspend fun read(address: String, target: File) {
            val stored = children[address] ?: throw IOException("missing")
            target.writeBytes(if (corruptRead) stored.copyOf().also { it[0] = it[0].inc() } else stored)
        }

        override suspend fun rename(address: String, newName: String): String {
            if (failRename) throw IOException("refused")
            check(newName !in children) { "overwrite" }
            children[newName] = children.remove(address) ?: throw IOException("missing")
            return newName
        }

        override suspend fun delete(address: String) {
            children.remove(address) ?: throw IOException("missing")
        }
    }

    private companion object {
        val PAYLOAD = ByteArray(70_000) { (it % 251).toByte() }
        val EXISTING = byteArrayOf(1, 2, 3)
    }
}
