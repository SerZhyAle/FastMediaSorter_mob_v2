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
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
@OptIn(ExperimentalCoroutinesApi::class)
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
        useCase = ListPhoneResourcePageUseCase(
            resourceRepository,
            scannerFactory,
            buildWatchThumbnail,
            // S1860: the scan is started in this scope rather than in the caller's job. Its own
            // scheduler is never advanced, so a stub that delays never finishes - which is exactly
            // the blocking scanner the timeout exists for, expressed without a real wait.
            CoroutineScope(UnconfinedTestDispatcher())
        )
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

    /**
     * S1911: an unreachable source and an abandoned request are different answers. `runCatching` made
     * them one, so a cancelled request told the watch SOURCE_UNAVAILABLE - a page the same cancelled
     * scope could never publish, leaving only a wrong status and an E-level line about a routine
     * teardown. Cancellation belongs to the caller, so it leaves this use case as itself.
     */
    @Test(expected = CancellationException::class)
    fun `a cancelled scan propagates instead of becoming source unavailable`() = runTest {
        coEvery { resourceRepository.getResourceById(1L) } returns resource(id = 1, name = "Photos")
        coEvery { scanner.listDirectoryContents(any(), any(), any(), any(), any()) } throws
            CancellationException("scope torn down")

        useCase(request(WearPhoneResourceRequestKind.CHILDREN, parentToken = "1:"))
    }

    @Test
    fun `an unreachable source maps to source unavailable, not to phone unavailable`() = runTest {
        coEvery { resourceRepository.getResourceById(1L) } returns resource(id = 1, name = "Photos")
        coEvery { scanner.listDirectoryContents(any(), any(), any(), any(), any()) } throws
            IllegalStateException("host down")

        val page = useCase(request(WearPhoneResourceRequestKind.CHILDREN, parentToken = "1:"))

        assertEquals(WearPhoneResourceResponseStatus.SOURCE_UNAVAILABLE, page.status)
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

    @Test
    fun `a request naming no media type lists every exposed resource`() = runTest {
        coEvery { resourceRepository.getAllResourcesSync() } returns listOf(
            resource(id = 1, name = "Photos", supportedMediaTypes = setOf(MediaType.IMAGE)),
            resource(id = 2, name = "Podcasts", supportedMediaTypes = setOf(MediaType.AUDIO))
        )

        val page = useCase(request(WearPhoneResourceRequestKind.ROOT))

        assertEquals(listOf("Photos", "Podcasts"), page.items.map { it.name })
    }

    @Test
    fun `the images chip does not list an audio-only resource`() = runTest {
        coEvery { resourceRepository.getAllResourcesSync() } returns listOf(
            resource(id = 1, name = "Photos", supportedMediaTypes = setOf(MediaType.IMAGE)),
            resource(id = 2, name = "Podcasts", supportedMediaTypes = setOf(MediaType.AUDIO))
        )

        val page = useCase(request(WearPhoneResourceRequestKind.ROOT, mediaType = "photos"))

        assertEquals(listOf("Photos"), page.items.map { it.name })
    }

    @Test
    fun `the documents chip lists a resource holding documents`() = runTest {
        coEvery { resourceRepository.getAllResourcesSync() } returns listOf(
            resource(id = 1, name = "Papers", supportedMediaTypes = setOf(MediaType.PDF)),
            resource(id = 2, name = "Photos", supportedMediaTypes = setOf(MediaType.IMAGE))
        )

        val page = useCase(request(WearPhoneResourceRequestKind.ROOT, mediaType = "documents"))

        assertEquals(listOf("Papers"), page.items.map { it.name })
    }

    @Test
    fun `an unknown media type narrows nothing rather than emptying the list`() = runTest {
        coEvery { resourceRepository.getAllResourcesSync() } returns listOf(
            resource(id = 1, name = "Photos", supportedMediaTypes = setOf(MediaType.IMAGE))
        )

        val page = useCase(request(WearPhoneResourceRequestKind.ROOT, mediaType = "sculptures"))

        assertEquals(listOf("Photos"), page.items.map { it.name })
    }

    /**
     * S1860: the page's picture budget must be spent before the decode, not after it.
     *
     * Trimming afterwards cost a decode per item in the whole fifty-item window while only about
     * four pictures could ever ship, which on a camera folder read tens of megabytes and answered
     * nothing before the watch gave up. Counting the calls is the only way to see the difference:
     * the resulting page looks identical either way.
     */
    @Test
    fun `a page decodes only the pictures it can carry, not the whole window`() = runTest {
        val affordable = ListPhoneResourcePageUseCase.MAX_PAGE_THUMBNAIL_CHARS / MAX_ENCODED_CHARS
        coEvery { resourceRepository.getResourceById(1L) } returns resource(id = 1, name = "Photos")
        coEvery { scanner.listDirectoryContents(any(), any(), any(), any(), any()) } returns
            (1..ListPhoneResourcePageUseCase.PAGE_SIZE).map { file(name = "shot$it.jpg") }
        coEvery { buildWatchThumbnail(any()) } returns "x".repeat(MAX_ENCODED_CHARS)

        val page = useCase(request(WearPhoneResourceRequestKind.CHILDREN, parentToken = "1:"))

        assertEquals(ListPhoneResourcePageUseCase.PAGE_SIZE, page.items.size)
        assertEquals(affordable, page.items.count { it.thumbnailBase64 != null })
        coVerify(exactly = affordable) { buildWatchThumbnail(any()) }
    }

    /**
     * S1860: a source that never answers is the phone's problem to bound, because the watch bounds
     * it at ten seconds and then blames the connection - sending the user to reconnect a phone that
     * is working.
     */
    @Test
    fun `a scan that outlives its allowance is answered as source unavailable`() = runTest {
        coEvery { resourceRepository.getResourceById(1L) } returns resource(id = 1, name = "Photos")
        coEvery { scanner.listDirectoryContents(any(), any(), any(), any(), any()) } coAnswers {
            delay(LONGER_THAN_ANY_BUDGET_MS)
            listOf(file(name = "late.jpg"))
        }

        val page = useCase(request(WearPhoneResourceRequestKind.CHILDREN, parentToken = "1:"))

        assertEquals(WearPhoneResourceResponseStatus.SOURCE_UNAVAILABLE, page.status)
        assertTrue("a timed-out scan carries no metadata", page.items.isEmpty())
    }

    @Test
    fun `a token without a MediaStore id keeps the pre-change wire form`() {
        val token = PhoneResourceToken(resourceId = 7L, relativePath = "holiday/beach.jpg")

        assertEquals("7:holiday/beach.jpg", token.serialize())
    }

    @Test
    fun `a token with a MediaStore id round-trips through serialize and parse`() {
        val token = PhoneResourceToken(resourceId = 7L, relativePath = "", mediaStoreId = 4321L)

        val parsed = PhoneResourceToken.parse(token.serialize())

        assertEquals(token, parsed)
        assertEquals(4321L, parsed?.mediaStoreId)
        assertEquals("", parsed?.relativePath)
    }

    @Test
    fun `an identity token whose id is not a number is refused`() {
        assertNull(PhoneResourceToken.parse("7:media:not-a-number"))
    }

    @Test
    fun `two same-named files of one resource get different tokens`() = runTest {
        coEvery { resourceRepository.getResourceById(1L) } returns resource(id = 1, name = "Camera")
        coEvery { scanner.listDirectoryContents(any(), any(), any(), any(), any()) } returns listOf(
            file(name = "IMG_0001.jpg", contentUri = "content://media/external/images/media/100"),
            file(name = "IMG_0001.jpg", contentUri = "content://media/external/images/media/200")
        )

        val page = useCase(request(WearPhoneResourceRequestKind.CHILDREN, parentToken = "1:"))

        assertEquals(listOf("IMG_0001.jpg", "IMG_0001.jpg"), page.items.map { it.name })
        assertEquals(
            "a shared name must not collapse two files onto one token",
            2,
            page.items.map { it.token }.distinct().size
        )
    }

    private fun request(
        kind: WearPhoneResourceRequestKind,
        parentToken: String? = null,
        pageToken: String? = null,
        mediaType: String? = null
    ) = WearPhoneResourceRequest(
        requestId = "req-${kind.name.lowercase()}",
        kind = kind,
        parentToken = parentToken,
        pageToken = pageToken,
        mediaType = mediaType
    )

    private fun resource(
        id: Long,
        name: String,
        type: ResourceType = ResourceType.LOCAL,
        accessPin: String? = null,
        isAvailable: Boolean = true,
        supportedMediaTypes: Set<MediaType> = setOf(MediaType.IMAGE, MediaType.VIDEO)
    ) = MediaResource(
        id = id,
        name = name,
        path = "/storage/emulated/0/$name",
        type = type,
        sortMode = SortMode.NAME_ASC,
        accessPin = accessPin,
        isAvailable = isAvailable,
        supportedMediaTypes = supportedMediaTypes
    )

    private fun file(name: String, hidden: Boolean = false, contentUri: String? = null) = MediaFile(
        name = name,
        path = "/storage/emulated/0/Photos/$name",
        type = MediaType.IMAGE,
        size = 1024L,
        createdDate = 0L,
        contentUri = contentUri,
        attributes = if (hidden) FileAttributes(readOnly = false, hidden = true) else null
    )

    private companion object {
        /** Longer than any allowance the use case grants a scan, so the timeout is what decides. */
        const val LONGER_THAN_ANY_BUDGET_MS = 60_000L
    }
}
