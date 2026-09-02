package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.model.SortMode
import com.sza.fastmediasorter.domain.model.WEAR_FILE_TRANSFER_MAX_BYTES
import com.sza.fastmediasorter.domain.model.WearPhoneResourceRequest
import com.sza.fastmediasorter.domain.model.WearPhoneResourceRequestKind
import com.sza.fastmediasorter.domain.model.WearPhoneResourceResponseStatus
import com.sza.fastmediasorter.domain.repository.MediaStoreRepository
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.RandomAccessFile

/**
 * S1909: this use case decides which phone file leaves for the watch, so every case here stands for a
 * way a file could otherwise go out that policy meant to keep in - a token climbing out of its resource,
 * a PIN-protected or network resource, a family the watch cannot draw, or a transfer over the size cap.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OpenPhoneResourceChannelUseCaseTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val resourceRepository: ResourceRepository = mockk()
    private val mediaStoreRepository: MediaStoreRepository = mockk()
    private lateinit var useCase: OpenPhoneResourceChannelUseCase
    private lateinit var root: File

    @Before
    fun setUp() {
        useCase = OpenPhoneResourceChannelUseCase(resourceRepository, mediaStoreRepository)
        root = temporaryFolder.newFolder("resource-root")
    }

    @Test
    fun `absent item token is refused`() = runTest {
        assertRejected(WearPhoneResourceResponseStatus.NOT_FOUND, useCase(request(itemToken = null)))
    }

    @Test
    fun `token climbing out of the resource with a parent segment is refused`() = runTest {
        writeFile("photo.jpg")
        stubResource(resource())

        assertRejected(WearPhoneResourceResponseStatus.NOT_FOUND, useCase(request("1:../photo.jpg")))
    }

    @Test
    fun `token escaping to an absolute path is refused`() = runTest {
        writeFile("photo.jpg")
        stubResource(resource())

        assertRejected(WearPhoneResourceResponseStatus.NOT_FOUND, useCase(request("1:/etc/hosts")))
    }

    @Test
    fun `a repository failure is reported as an unavailable phone, not a missing file`() = runTest {
        coEvery { resourceRepository.getResourceById(RESOURCE_ID) } throws IllegalStateException("db down")

        assertRejected(WearPhoneResourceResponseStatus.PHONE_UNAVAILABLE, useCase(request("1:photo.jpg")))
    }

    @Test
    fun `an unknown resource is refused`() = runTest {
        stubResource(null)

        assertRejected(WearPhoneResourceResponseStatus.NOT_FOUND, useCase(request("1:photo.jpg")))
    }

    @Test
    fun `a network resource is never delivered`() = runTest {
        writeFile("photo.jpg")
        stubResource(resource(type = ResourceType.SMB))

        assertRejected(WearPhoneResourceResponseStatus.UNSUPPORTED_MEDIA, useCase(request("1:photo.jpg")))
    }

    @Test
    fun `a pin protected resource is never delivered`() = runTest {
        writeFile("photo.jpg")
        stubResource(resource(accessPin = "1234"))

        assertRejected(WearPhoneResourceResponseStatus.UNSUPPORTED_MEDIA, useCase(request("1:photo.jpg")))
    }

    @Test
    fun `an unavailable resource is never delivered`() = runTest {
        writeFile("photo.jpg")
        stubResource(resource(isAvailable = false))

        assertRejected(WearPhoneResourceResponseStatus.UNSUPPORTED_MEDIA, useCase(request("1:photo.jpg")))
    }

    @Test
    fun `a token naming no existing file is refused`() = runTest {
        stubResource(resource())

        assertRejected(WearPhoneResourceResponseStatus.NOT_FOUND, useCase(request("1:missing.jpg")))
    }

    @Test
    fun `a family the watch cannot draw is refused`() = runTest {
        writeFile("notes.txt")
        stubResource(resource())

        assertRejected(WearPhoneResourceResponseStatus.UNSUPPORTED_MEDIA, useCase(request("1:notes.txt")))
    }

    @Test
    fun `a file over the transfer cap is refused`() = runTest {
        // Sparse rather than written: the cap is read from length(), so materialising a real 32 MB of
        // bytes would buy nothing and slow the suite down.
        val big = File(root, "huge.jpg")
        RandomAccessFile(big, "rw").use { it.setLength(WEAR_FILE_TRANSFER_MAX_BYTES + 1) }
        stubResource(resource())

        assertRejected(WearPhoneResourceResponseStatus.TRANSFER_REJECTED, useCase(request("1:huge.jpg")))
    }

    @Test
    fun `a file exactly at the transfer cap is still delivered`() = runTest {
        val atCap = File(root, "at-cap.jpg")
        RandomAccessFile(atCap, "rw").use { it.setLength(WEAR_FILE_TRANSFER_MAX_BYTES) }
        stubResource(resource())

        val approved = useCase(request("1:at-cap.jpg")) as PhoneResourceChannel.Approved
        assertEquals(WEAR_FILE_TRANSFER_MAX_BYTES, approved.sizeBytes)
    }

    @Test
    fun `a supported file is approved with its own name, size and family`() = runTest {
        writeFile("photo.jpg", "watch-bound bytes")
        stubResource(resource())

        val approved = useCase(request("1:photo.jpg")) as PhoneResourceChannel.Approved

        assertEquals("photo.jpg", approved.name)
        assertEquals(MediaType.IMAGE, approved.mediaType)
        assertEquals("watch-bound bytes".length.toLong(), approved.sizeBytes)
    }

    @Test
    fun `the approved file is opened by the caller and yields the file bytes`() = runTest {
        writeFile("photo.jpg", "watch-bound bytes")
        stubResource(resource())

        val approved = useCase(request("1:photo.jpg")) as PhoneResourceChannel.Approved

        val read = approved.file.inputStream().use { it.readBytes().decodeToString() }
        assertEquals("watch-bound bytes", read)
    }

    @Test
    fun `a file in a nested folder of the resource is delivered`() = runTest {
        File(root, "sub").mkdirs()
        File(root, "sub/photo.jpg").writeText("nested")
        stubResource(resource())

        assertTrue(useCase(request("1:sub/photo.jpg")) is PhoneResourceChannel.Approved)
    }

    @Test
    fun `an identity token opens the file its MediaStore id names`() = runTest {
        val photo = writeFile("shot.jpg")
        stubResource(resource())
        coEvery { mediaStoreRepository.getFileByMediaStoreId(MEDIA_ID) } returns mediaFile(photo)

        val channel = useCase(request("1:media:$MEDIA_ID"))

        assertEquals("shot.jpg", (channel as PhoneResourceChannel.Approved).name)
    }

    @Test
    fun `an identity token whose id resolves to nothing is a missing file`() = runTest {
        stubResource(resource())
        coEvery { mediaStoreRepository.getFileByMediaStoreId(MEDIA_ID) } returns null

        assertRejected(WearPhoneResourceResponseStatus.NOT_FOUND, useCase(request("1:media:$MEDIA_ID")))
    }

    @Test
    fun `an identity token pointing outside an ordinary resource is refused`() = runTest {
        // A MediaStore id names any row on the device, so without confinement it would reach past
        // the resource - the containment the path form gets from escapesResource.
        val outsider = temporaryFolder.newFile("elsewhere.jpg").apply { writeText("not yours") }
        stubResource(resource())
        coEvery { mediaStoreRepository.getFileByMediaStoreId(MEDIA_ID) } returns mediaFile(outsider)

        assertRejected(WearPhoneResourceResponseStatus.NOT_FOUND, useCase(request("1:media:$MEDIA_ID")))
    }

    @Test
    fun `a virtual resource serves an identity token from any folder it aggregates`() = runTest {
        val outsider = temporaryFolder.newFile("aggregated.jpg").apply { writeText("x") }
        stubResource(resource(path = "virtual://camera_photos"))
        coEvery { mediaStoreRepository.getFileByMediaStoreId(MEDIA_ID) } returns mediaFile(outsider)

        val channel = useCase(request("1:media:$MEDIA_ID"))

        assertEquals("aggregated.jpg", (channel as PhoneResourceChannel.Approved).name)
    }

    @Test
    fun `a virtual resource refuses a name-only token instead of guessing`() = runTest {
        writeFile("photo.jpg")
        stubResource(resource(path = "virtual://camera_photos"))

        assertRejected(WearPhoneResourceResponseStatus.NOT_FOUND, useCase(request("1:photo.jpg")))
    }

    @Test
    fun `two same-named files in different folders are told apart by their ids`() = runTest {
        // The whole point of the identity token: one virtual resource merges several folders, so
        // the same name legitimately appears twice and only the id separates them.
        val first = writeFileIn("camera", "IMG_0001.jpg", body = "first")
        val second = writeFileIn("downloads", "IMG_0001.jpg", body = "second-and-longer")
        stubResource(resource())
        coEvery { mediaStoreRepository.getFileByMediaStoreId(MEDIA_ID) } returns mediaFile(first)
        coEvery { mediaStoreRepository.getFileByMediaStoreId(OTHER_MEDIA_ID) } returns mediaFile(second)

        val one = useCase(request("1:media:$MEDIA_ID")) as PhoneResourceChannel.Approved
        val two = useCase(request("1:media:$OTHER_MEDIA_ID")) as PhoneResourceChannel.Approved

        assertEquals(first.name, second.name)
        assertEquals(first.length(), one.sizeBytes)
        assertEquals(second.length(), two.sizeBytes)
        assertTrue("distinct files must not report one size", one.sizeBytes != two.sizeBytes)
    }

    private fun writeFile(name: String, body: String = "x") = File(root, name).apply { writeText(body) }

    private fun writeFileIn(folder: String, name: String, body: String = "x") =
        File(File(root, folder).apply { mkdirs() }, name).apply { writeText(body) }

    private fun stubResource(value: MediaResource?) {
        coEvery { resourceRepository.getResourceById(RESOURCE_ID) } returns value
    }

    private fun assertRejected(expected: WearPhoneResourceResponseStatus, actual: PhoneResourceChannel) {
        assertEquals(expected, (actual as PhoneResourceChannel.Rejected).status)
    }

    private fun mediaFile(file: File) = MediaFile(
        name = file.name,
        path = file.absolutePath,
        type = MediaType.IMAGE,
        size = file.length(),
        createdDate = 0L
    )

    private fun resource(
        type: ResourceType = ResourceType.LOCAL,
        accessPin: String? = null,
        isAvailable: Boolean = true,
        path: String = root.absolutePath
    ) = MediaResource(
        id = RESOURCE_ID,
        name = "Photos",
        path = path,
        type = type,
        sortMode = SortMode.NAME_ASC,
        accessPin = accessPin,
        isAvailable = isAvailable,
        supportedMediaTypes = setOf(MediaType.IMAGE, MediaType.VIDEO)
    )

    private fun request(itemToken: String? = "1:photo.jpg") = WearPhoneResourceRequest(
        requestId = "req-open",
        kind = WearPhoneResourceRequestKind.OPEN,
        itemToken = itemToken
    )

    private companion object {
        const val RESOURCE_ID = 1L
        const val MEDIA_ID = 100L
        const val OTHER_MEDIA_ID = 200L
    }
}
