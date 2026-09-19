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

    private val mediaStoreRepository: com.sza.fastmediasorter.domain.repository.MediaStoreRepository =
        mockk(relaxed = true)

    private lateinit var useCase: ListPhoneResourcePageUseCase

    @Before
    fun setUp() {
        every { scannerFactory.getScanner(any()) } returns scanner
        coEvery { buildWatchThumbnail(any()) } returns null
        useCase = ListPhoneResourcePageUseCase(
            resourceRepository,
            scannerFactory,
            buildWatchThumbnail,
            mediaStoreRepository,
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
        assertEquals(listOf("Photos"), page.items.orEmpty().map { it.name })
        assertTrue("root entries are browsable", page.items.orEmpty().all { it.isDirectory })
    }

    @Test
    fun `root reports empty when the phone exposes nothing`() = runTest {
        coEvery { resourceRepository.getAllResourcesSync() } returns listOf(
            resource(id = 2, name = "Protected", accessPin = "1234")
        )

        val page = useCase(request(WearPhoneResourceRequestKind.ROOT))

        assertEquals(WearPhoneResourceResponseStatus.EMPTY, page.status)
        assertTrue(page.items.orEmpty().isEmpty())
    }

    @Test
    fun `children page is bounded and hands back a next page token`() = runTest {
        val total = ListPhoneResourcePageUseCase.PAGE_SIZE + 10
        coEvery { resourceRepository.getResourceById(1L) } returns resource(id = 1, name = "Photos")
        coEvery { scanner.listDirectoryContents(any(), any(), any(), any(), any()) } returns
            (1..total).map { file(name = "clip$it.mp4") }

        val first = useCase(request(WearPhoneResourceRequestKind.CHILDREN, parentToken = "1:"))

        assertEquals(ListPhoneResourcePageUseCase.PAGE_SIZE, first.items.orEmpty().size)
        assertEquals(ListPhoneResourcePageUseCase.PAGE_SIZE.toString(), first.nextPageToken)

        val second = useCase(
            request(WearPhoneResourceRequestKind.CHILDREN, parentToken = "1:", pageToken = first.nextPageToken)
        )

        assertEquals(10, second.items.orEmpty().size)
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

        assertEquals(listOf("visible.jpg"), page.items.orEmpty().map { it.name })
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
        assertTrue("a failure carries no metadata", page.items.orEmpty().isEmpty())
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

        assertEquals(listOf("Photos", "Podcasts"), page.items.orEmpty().map { it.name })
    }

    @Test
    fun `the images chip does not list an audio-only resource`() = runTest {
        coEvery { resourceRepository.getAllResourcesSync() } returns listOf(
            resource(id = 1, name = "Photos", supportedMediaTypes = setOf(MediaType.IMAGE)),
            resource(id = 2, name = "Podcasts", supportedMediaTypes = setOf(MediaType.AUDIO))
        )

        val page = useCase(request(WearPhoneResourceRequestKind.ROOT, mediaType = "photos"))

        assertEquals(listOf("Photos"), page.items.orEmpty().map { it.name })
    }

    @Test
    fun `the documents chip lists a resource holding documents`() = runTest {
        coEvery { resourceRepository.getAllResourcesSync() } returns listOf(
            resource(id = 1, name = "Papers", supportedMediaTypes = setOf(MediaType.PDF)),
            resource(id = 2, name = "Photos", supportedMediaTypes = setOf(MediaType.IMAGE))
        )

        val page = useCase(request(WearPhoneResourceRequestKind.ROOT, mediaType = "documents"))

        assertEquals(listOf("Papers"), page.items.orEmpty().map { it.name })
    }

    /**
     * S3160: this case asserted the opposite until 2026-09-16 - an unknown token widened the answer
     * back to every kind and said nothing. It replaced a dead end with a list that looks filtered and
     * is not, which no wearer and no log can tell from a correct one.
     */
    @Test
    fun `an unknown media type is refused instead of widening the answer`() = runTest {
        coEvery { resourceRepository.getAllResourcesSync() } returns listOf(
            resource(id = 1, name = "Photos", supportedMediaTypes = setOf(MediaType.IMAGE))
        )

        val page = useCase(request(WearPhoneResourceRequestKind.ROOT, mediaType = "sculptures"))

        assertEquals(WearPhoneResourceResponseStatus.UNSUPPORTED_MEDIA, page.status)
        assertTrue("a refusal carries no items", page.items.orEmpty().isEmpty())
    }

    /**
     * S3160: the two spellings of "no filter" must survive the refusal above. The All chip sends null
     * today and the request KDoc has promised `all` since S1846, so both stay accepted.
     */
    @Test
    fun `the all token and a null media type both leave the answer unnarrowed`() = runTest {
        coEvery { resourceRepository.getAllResourcesSync() } returns listOf(
            resource(id = 1, name = "Photos", supportedMediaTypes = setOf(MediaType.IMAGE)),
            resource(id = 2, name = "Podcasts", supportedMediaTypes = setOf(MediaType.AUDIO))
        )

        val explicit = useCase(request(WearPhoneResourceRequestKind.ROOT, mediaType = "all"))
        val implicit = useCase(request(WearPhoneResourceRequestKind.ROOT))

        assertEquals(listOf("Photos", "Podcasts"), explicit.items.orEmpty().map { it.name })
        assertEquals(explicit.items.orEmpty().map { it.name }, implicit.items.orEmpty().map { it.name })
    }

    /**
     * S3160: a thumbnail request carries no chip, so the refusal must not reach the kinds that never
     * narrow anything - the watch would lose every picture on the screen instead of one list.
     */
    @Test
    fun `a request carrying no media type is never refused`() = runTest {
        coEvery { resourceRepository.getAllResourcesSync() } returns listOf(
            resource(id = 1, name = "Photos", supportedMediaTypes = setOf(MediaType.IMAGE))
        )

        val page = useCase(request(WearPhoneResourceRequestKind.ROOT))

        assertEquals(WearPhoneResourceResponseStatus.OK, page.status)
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
    fun `a page carries no embedded pictures for CHILDREN listing`() = runTest {
        coEvery { resourceRepository.getResourceById(1L) } returns resource(id = 1, name = "Photos")
        coEvery { scanner.listDirectoryContents(any(), any(), any(), any(), any()) } returns
            (1..ListPhoneResourcePageUseCase.PAGE_SIZE).map { file(name = "shot$it.jpg") }

        val page = useCase(request(WearPhoneResourceRequestKind.CHILDREN, parentToken = "1:"))

        assertEquals(ListPhoneResourcePageUseCase.PAGE_SIZE, page.items.orEmpty().size)
        assertEquals(0, page.items.orEmpty().count { it.thumbnailBase64 != null })
        coVerify(exactly = 0) { buildWatchThumbnail(any()) }
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
        assertTrue("a timed-out scan carries no metadata", page.items.orEmpty().isEmpty())
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

        assertEquals(listOf("IMG_0001.jpg", "IMG_0001.jpg"), page.items.orEmpty().map { it.name })
        assertEquals(
            "a shared name must not collapse two files onto one token",
            2,
            page.items.orEmpty().map { it.token }.distinct().size
        )
    }

    // S2860: the default virtual resources (virtual://recent, virtual://all_images,
    // virtual://camera_photos) overlap - they all return the same MediaStore row for one
    // physical file. Without deduplication the watch renders each file once per resource.
    @Test
    fun `flat list deduplicates files shared across overlapping resources`() = runTest {
        coEvery { resourceRepository.getAllResourcesSync() } returns listOf(
            resource(id = 1, name = "Recent"),
            resource(id = 2, name = "All Images")
        )
        coEvery { scanner.listDirectoryContents(any(), any(), any(), any(), any()) } returns listOf(
            file(name = "IMG_0001.jpg", contentUri = "content://media/external/images/media/100"),
            file(name = "IMG_0002.jpg", contentUri = "content://media/external/images/media/101")
        )

        val page = useCase(request(WearPhoneResourceRequestKind.ROOT, mediaType = "recents"))

        assertEquals(WearPhoneResourceResponseStatus.OK, page.status)
        assertEquals(
            "each file appears once despite two resources covering it",
            listOf("IMG_0001.jpg", "IMG_0002.jpg"),
            page.items.orEmpty().map { it.name }
        )
    }

    @Test
    fun `flat list deduplicates by path when files carry no content URI`() = runTest {
        coEvery { resourceRepository.getAllResourcesSync() } returns listOf(
            resource(id = 1, name = "Folder A"),
            resource(id = 2, name = "Folder B")
        )
        val sharedPath = "/storage/emulated/0/DCIM/IMG_0001.jpg"
        coEvery { scanner.listDirectoryContents(any(), any(), any(), any(), any()) } returns listOf(
            file(name = "IMG_0001.jpg", contentUri = null).copy(path = sharedPath)
        )

        val page = useCase(request(WearPhoneResourceRequestKind.ROOT, mediaType = "recents"))

        assertEquals(listOf("IMG_0001.jpg"), page.items.orEmpty().map { it.name })
    }

    // S2982: two distinct files sharing a display name live at two paths - a path names at most one
    // physical file. The original S2860 form of this test left both copies on the helper's
    // name-derived path, which models a state no filesystem can hold, and the dual-key dedup then
    // correctly collapsed them.
    @Test
    fun `flat list keeps same-named files with different MediaStore ids separate`() = runTest {
        coEvery { resourceRepository.getAllResourcesSync() } returns listOf(
            resource(id = 1, name = "Recent"),
            resource(id = 2, name = "All Images")
        )
        coEvery { scanner.listDirectoryContents(any(), any(), any(), any(), any()) } returns listOf(
            file(name = "IMG_0001.jpg", contentUri = "content://media/external/images/media/100")
                .copy(path = "/storage/emulated/0/DCIM/IMG_0001.jpg"),
            file(name = "IMG_0001.jpg", contentUri = "content://media/external/images/media/200")
                .copy(path = "/storage/emulated/0/Download/IMG_0001.jpg")
        )

        val page = useCase(request(WearPhoneResourceRequestKind.ROOT, mediaType = "recents"))

        assertEquals(
            "two distinct MediaStore entries survive deduplication",
            2,
            page.items.orEmpty().size
        )
        assertEquals(2, page.items.orEmpty().map { it.token }.distinct().size)
    }

    // S2982: MediaStore can hold a stale row beside the fresh one for a file that was rewritten in
    // place - two ids, one `_data`. The path is what says they are one file, so the dual-key dedup
    // collapses them rather than rendering the same tile twice on the watch.
    @Test
    fun `flat list collapses two MediaStore ids that share one path`() = runTest {
        val sharedPath = "/storage/emulated/0/DCIM/IMG_0001.jpg"
        coEvery { resourceRepository.getAllResourcesSync() } returns listOf(
            resource(id = 1, name = "Recent")
        )
        coEvery { scanner.listDirectoryContents(any(), any(), any(), any(), any()) } returns listOf(
            file(name = "IMG_0001.jpg", contentUri = "content://media/external/images/media/100")
                .copy(path = sharedPath),
            file(name = "IMG_0001.jpg", contentUri = "content://media/external/images/media/200")
                .copy(path = sharedPath)
        )

        val page = useCase(request(WearPhoneResourceRequestKind.ROOT, mediaType = "recents"))

        assertEquals(
            "one path is one physical file however many MediaStore rows point at it",
            listOf("IMG_0001.jpg"),
            page.items.orEmpty().map { it.name }
        )
    }

    // S2982: a file covered by a virtual resource (MediaStore id key) and a local folder resource
    // (path key, no contentUri) is one physical file. The dual-key dedup must recognise it as one.
    @Test
    fun `flat list deduplicates a file shared between a virtual and a folder resource`() = runTest {
        val sharedPath = "/storage/emulated/0/Download/S2925_bg_light.png"
        coEvery { resourceRepository.getAllResourcesSync() } returns listOf(
            resource(id = 1, name = "Recent").copy(path = "virtual://recent"),
            resource(id = 2, name = "Download")
        )
        coEvery {
            scanner.listDirectoryContents(
                match { it == "virtual://recent" }, any(), any(), any(), any()
            )
        } returns listOf(
            file(name = "S2925_bg_light.png", contentUri = "content://media/external/images/media/32090")
                .copy(path = sharedPath)
        )
        coEvery {
            scanner.listDirectoryContents(
                match { it == "/storage/emulated/0/Download" }, any(), any(), any(), any()
            )
        } returns listOf(
            file(name = "S2925_bg_light.png", contentUri = null).copy(path = sharedPath)
        )

        val page = useCase(request(WearPhoneResourceRequestKind.ROOT, mediaType = "recents"))

        assertEquals(WearPhoneResourceResponseStatus.OK, page.status)
        assertEquals(
            "a file covered by both a virtual and a folder resource appears once",
            listOf("S2925_bg_light.png"),
            page.items.orEmpty().map { it.name }
        )
    }

    /**
     * S2911: the open channel delivers phone-owned storage only, so the listing must not offer a
     * resource whose every file would be refused at tap time - on the watch the rejected tile reads
     * as "The watch cannot open this kind of file.", blaming a format that is fine.
     */
    @Test
    fun `a network resource is offered nowhere in the phone section`() = runTest {
        coEvery { resourceRepository.getAllResourcesSync() } returns listOf(
            resource(id = 1, name = "Photos"),
            resource(id = 2, name = "NAS", type = ResourceType.SFTP)
        )
        coEvery { scanner.listDirectoryContents(any(), any(), any(), any(), any()) } returns
            listOf(file(name = "IMG_0001.jpg", contentUri = "content://media/external/images/media/100"))
        coEvery {
            scanner.listDirectoryContents(match { it.endsWith("NAS") }, any(), any(), any(), any())
        } returns listOf(file(name = "remote.jpg"))

        val roots = useCase(request(WearPhoneResourceRequestKind.ROOT))
        val flat = useCase(request(WearPhoneResourceRequestKind.ROOT, mediaType = "recents"))

        assertEquals(
            "a network resource is not a phone-section root",
            listOf("Photos"),
            roots.items.orEmpty().map { it.name }
        )
        assertEquals(
            "a network resource contributes no files to the flat list",
            listOf("IMG_0001.jpg"),
            flat.items.orEmpty().map { it.name }
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
