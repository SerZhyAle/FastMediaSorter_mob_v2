package com.sza.fastmediasorter.data.transfer

import com.sza.fastmediasorter.data.cloud.CloudFile
import com.sza.fastmediasorter.data.cloud.CloudResult
import com.sza.fastmediasorter.data.cloud.CloudStorageClient
import com.sza.fastmediasorter.domain.model.FdSecResult
import com.sza.fastmediasorter.domain.usecase.PlaceVerifiedFileBesideUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

class CloudSiblingFolderTest {

    @get:Rule
    val temp: TemporaryFolder = TemporaryFolder()

    /** An id-addressed drive that, like Google Drive, keeps same-named children apart by id alone. */
    private class Drive {
        val names = linkedMapOf<String, String>()
        val bytes = mutableMapOf<String, ByteArray>()
        var nextId = 0
        val client: CloudStorageClient = mockk()

        init {
            coEvery { client.fileExists(any(), FOLDER) } answers {
                CloudResult.Success(firstArg<String>() in names.values)
            }
            coEvery { client.uploadFile(any(), any(), any(), any(), any(), any()) } answers {
                val id = "id-${nextId++}"
                names[id] = secondArg()
                bytes[id] = firstArg<InputStream>().readBytes()
                CloudResult.Success(CloudFile(id = id, name = secondArg(), path = "", isFolder = false))
            }
            coEvery { client.downloadFile(any(), any(), any()) } answers {
                val content = bytes[firstArg()] ?: return@answers CloudResult.Error("not found")
                secondArg<OutputStream>().write(content)
                CloudResult.Success(true)
            }
            coEvery { client.renameFile(any(), any()) } answers {
                names[firstArg()] = secondArg()
                CloudResult.Success(CloudFile(id = firstArg(), name = secondArg(), path = "", isFolder = false))
            }
            coEvery { client.deleteFile(any()) } answers {
                names.remove(firstArg<String>())
                bytes.remove(firstArg<String>())
                CloudResult.Success(true)
            }
        }
    }

    @Test
    fun `a proven file lands under its name beside a same-named one, and no temporary is left`() = runTest {
        val drive = Drive()
        drive.names["existing"] = "holiday.png"
        drive.bytes["existing"] = byteArrayOf(9)
        val proven = temp.newFile("holiday.png").apply { writeBytes(PAYLOAD) }

        val result = PlaceVerifiedFileBesideUseCase()(
            proven,
            CloudSiblingFolder(drive.client, FOLDER),
            "holiday.png",
            temp.newFolder("scratch"),
        )

        assertEquals(FdSecResult.Placed("holiday-1.png"), result)
        assertEquals(listOf("holiday.png", "holiday-1.png"), drive.names.values.toList())
        val placedId = drive.names.entries.single { it.value == "holiday-1.png" }.key
        assertArrayEquals(PAYLOAD, drive.bytes[placedId])
    }

    @Test
    fun `a read-back that differs removes the temporary by its id`() = runTest {
        val drive = Drive()
        coEvery { drive.client.downloadFile(any(), any(), any()) } answers {
            secondArg<OutputStream>().write(byteArrayOf(0))
            CloudResult.Success(true)
        }
        val proven = temp.newFile("a.bin").apply { writeBytes(PAYLOAD) }

        val result = PlaceVerifiedFileBesideUseCase()(
            proven,
            CloudSiblingFolder(drive.client, FOLDER),
            "a.bin",
            temp.newFolder("scratch"),
        )

        assertTrue(result is FdSecResult.Failed)
        assertTrue(drive.names.isEmpty())
    }

    @Test
    fun `a blank folder id uploads to the root`() = runTest {
        val client = mockk<CloudStorageClient>()
        coEvery { client.uploadFile(any(), any(), any(), null, any(), any()) } returns
            CloudResult.Success(CloudFile(id = "/a.bin", name = "a.bin", path = "", isFolder = false))
        val source = temp.newFile("a.bin").apply { writeBytes(PAYLOAD) }

        val address = CloudSiblingFolder(client, "").write(source, "a.bin")

        assertEquals("/a.bin", address)
        coVerify { client.uploadFile(any(), "a.bin", any(), null, PAYLOAD.size.toLong(), any()) }
    }

    @Test
    fun `a rename reports the id the provider returns`() = runTest {
        val client = mockk<CloudStorageClient>()
        coEvery { client.renameFile("/f/.tmp", "b.png") } returns
            CloudResult.Success(CloudFile(id = "/f/b.png", name = "b.png", path = "", isFolder = false))

        assertEquals("/f/b.png", CloudSiblingFolder(client, "/f").rename("/f/.tmp", "b.png"))
    }

    @Test(expected = IOException::class)
    fun `a provider error on upload is an IOException`() = runTest {
        val client = mockk<CloudStorageClient>()
        coEvery { client.uploadFile(any(), any(), any(), any(), any(), any()) } returns CloudResult.Error("quota")
        CloudSiblingFolder(client, FOLDER).write(temp.newFile("a.bin"), "a.bin")
    }

    @Test(expected = IOException::class)
    fun `a provider error on the name check is an IOException`() = runTest {
        val client = mockk<CloudStorageClient>()
        coEvery { client.fileExists(any(), any()) } returns CloudResult.Error("offline")
        CloudSiblingFolder(client, FOLDER).contains("a.bin")
    }

    @Test(expected = IOException::class)
    fun `a refused delete is an IOException`() = runTest {
        val client = mockk<CloudStorageClient>()
        coEvery { client.deleteFile(any()) } returns CloudResult.Success(false)
        CloudSiblingFolder(client, FOLDER).delete("id-0")
    }

    private companion object {
        const val FOLDER = "folder-id"
        val PAYLOAD = byteArrayOf(1, 2, 3, 4, 5)
    }
}
