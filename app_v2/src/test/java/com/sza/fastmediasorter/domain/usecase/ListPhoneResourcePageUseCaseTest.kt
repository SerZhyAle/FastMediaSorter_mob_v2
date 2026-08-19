package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.domain.model.FileAttributes
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.model.SortMode
import com.sza.fastmediasorter.domain.model.WearPhoneResourceRequest
import com.sza.fastmediasorter.domain.model.WearPhoneResourceRequestKind
import com.sza.fastmediasorter.domain.model.WearPhoneResourceResponseStatus
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * S1697: this use case is the only place phone visibility policy is applied before a watch sees
 * anything, so each case here stands for a way the watch could otherwise learn about content the
 * phone user hid, protected, or cannot currently reach.
 */
class ListPhoneResourcePageUseCaseTest {

    private val resourceRepository: ResourceRepository = mockk()
    private val scanner: MediaScanner = mockk()
    private val scannerFactory: MediaScannerFactory = mockk()

    // S1730: the page now carries a picture per item. These cases assert visibility policy, so the
    // producer is stubbed to decline - the state in which the watch draws a type icon.
    private val buildWatchThumbnail: BuildWatchThumbnailUseCase = mockk()

    private lateinit var useCase: ListPhoneResourcePageUseCase

    @Before
    fun setUp() {
        every { scannerFactory.getScanner(any()) } returns scanner
        coEvery { buildWatchThumbnail(any()) } returns null
        useCase = ListPhoneResourcePageUseCase(resourceRepository, scannerFactory, buildWatchThumbnail)
    }

    @Test
    fun `root lists only resources the phone exposes`() = runTest {
        coEvery { resourceRepository.getAllResourcesSync() } returns listOf(
            resource(id = 1, name = "Photos"),
            resource(id = 2, name = "Protected", accessPin = "1234"),
            resource(id = 3, name = "Offline", isAvailable = false),
            resource(id = 4, name = "Live stream", type = ResourceType.HTTP_STREAM)
        )

        val page = useCase(request(WearPhoneResourceRequestKind.ROOT))

        assertEquals(WearPhoneResourceResponseStatus.OK, page.status)
        assertEquals(listOf("Photos"), page.items.map { it.name })
        assertTrue("root entries are browsable", page.items.all { it.isDirectory })
    }

    @Test
    fun `root reports empty when the phone exposes nothing`() = runTest {
        coEvery { resourceRepository.getAllResourcesSync() } returns listOf(
            resource(id = 2, name = "Protected", accessPin = "1234")
        )

        val page = useCase(request(WearPhoneResourceRequestKind.ROOT))

        assertEquals(WearPhoneResourceResponseStatus.EMPTY, page.status)
        assertTrue(page.items.isEmpty())
    }

    @Test
    fun `children page is bounded and hands back a next page token`() = runTest {
        val total = ListPhoneResourcePageUseCase.PAGE_SIZE + 10
        coEvery { resourceRepository.getResourceById(1L) } returns resource(id = 1, name = "Photos")
        coEvery { scanner.listDirectoryContents(any(), any(), any(), any(), any()) } returns
            (1..total).map { file(name = "clip$it.mp4") }

        val first = useCase(request(WearPhoneResourceRequestKind.CHILDREN, parentToken = "1:"))

        assertEquals(ListPhoneResourcePageUseCase.PAGE_SIZE, first.items.size)
        assertEquals(ListPhoneResourcePageUseCase.PAGE_SIZE.toString(), first.nextPageToken)

        val second = useCase(
            request(WearPhoneResourceRequestKind.CHILDREN, parentToken = "1:", pageToken = first.nextPageToken)
        )

        assertEquals(10, second.items.size)
        assertNull("last page ends the walk", second.nextPageToken)
    }

    @Test
    fun `hidden entries stay invisible unless the resource shows them`() = runTest {
        coEvery { resourceRepository.getResourceById(1L) } returns resource(id = 1, name = "Photos")
        coEvery { scanner.listDirectoryContents(any(), any(), any(), any(), any()) } returns listOf(
            file(name = "visible.jpg"),
            file(name = ".dotfile.jpg"),
            file(name = "flagged.jpg", hidden = true)
        )

        val page = useCase(request(WearPhoneResourceRequestKind.CHILDREN, parentToken = "1:"))

        assertEquals(listOf("visible.jpg"), page.items.map { it.name })
    }

    @Test
    fun `an unreachable resource maps to phone unavailable`() = runTest {
        coEvery { resourceRepository.getResourceById(1L) } returns resource(id = 1, name = "Photos")
        coEvery { scanner.listDirectoryContents(any(), any(), any(), any(), any()) } throws
            IllegalStateException("host down")

        val page = useCase(request(WearPhoneResourceRequestKind.CHILDREN, parentToken = "1:"))

        assertEquals(WearPhoneResourceResponseStatus.PHONE_UNAVAILABLE, page.status)
        assertTrue("a failure carries no metadata", page.items.isEmpty())
    }

    @Test
    fun `a protected resource is denied even when its token is guessed`() = runTest {
        coEvery { resourceRepository.getResourceById(2L) } returns
            resource(id = 2, name = "Protected", accessPin = "1234")

        val page = useCase(request(WearPhoneResourceRequestKind.CHILDREN, parentToken = "2:"))

        assertEquals(WearPhoneResourceResponseStatus.ACCESS_DENIED, page.status)
    }

    @Test
    fun `a traversing token is refused instead of resolved`() = runTest {
        val page = useCase(request(WearPhoneResourceRequestKind.CHILDREN, parentToken = "1:../../etc"))

        assertEquals(WearPhoneResourceResponseStatus.NOT_FOUND, page.status)
    }

    @Test
    fun `every response is correlated with its request`() = runTest {
        coEvery { resourceRepository.getAllResourcesSync() } returns listOf(resource(id = 1, name = "Photos"))

        val request = request(WearPhoneResourceRequestKind.ROOT)
        val page = useCase(request)

        assertEquals(request.requestId, page.requestId)
        assertNotNull(page.status)
    }

    private fun request(
        kind: WearPhoneResourceRequestKind,
        parentToken: String? = null,
        pageToken: String? = null
    ) = WearPhoneResourceRequest(
        requestId = "req-${kind.name.lowercase()}",
        kind = kind,
        parentToken = parentToken,
        pageToken = pageToken
    )

    private fun resource(
        id: Long,
        name: String,
        type: ResourceType = ResourceType.LOCAL,
        accessPin: String? = null,
        isAvailable: Boolean = true
    ) = MediaResource(
        id = id,
        name = name,
        path = "/storage/emulated/0/$name",
        type = type,
        sortMode = SortMode.NAME_ASC,
        accessPin = accessPin,
        isAvailable = isAvailable
    )

    private fun file(name: String, hidden: Boolean = false) = MediaFile(
        name = name,
        path = "/storage/emulated/0/Photos/$name",
        type = MediaType.IMAGE,
        size = 1024L,
        createdDate = 0L,
        attributes = if (hidden) FileAttributes(readOnly = false, hidden = true) else null
    )
}
