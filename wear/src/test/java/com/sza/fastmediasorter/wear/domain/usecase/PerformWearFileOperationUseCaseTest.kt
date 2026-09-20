package com.sza.fastmediasorter.wear.domain.usecase

import android.content.Context
import android.net.Uri
import com.sza.fastmediasorter.wear.data.files.WearMediaFileStager
import com.sza.fastmediasorter.wear.data.files.WearMediaStoreFileWriter
import com.sza.fastmediasorter.wear.data.files.WearWatchFilePublisher
import com.sza.fastmediasorter.wear.data.repository.WearSendToReceiversRepository
import com.sza.fastmediasorter.wear.domain.files.WEAR_PHONE_FILE_CACHE_DIR
import com.sza.fastmediasorter.wear.domain.files.WearFileCapabilityPolicy
import com.sza.fastmediasorter.wear.domain.files.WearMediaStoreConsent
import com.sza.fastmediasorter.wear.domain.model.WearFileOperation
import com.sza.fastmediasorter.wear.domain.model.WearFileOperationKind
import com.sza.fastmediasorter.wear.domain.model.WearFileOperationOutcome
import com.sza.fastmediasorter.wear.domain.model.WearFileSendOutcome
import com.sza.fastmediasorter.wear.domain.model.WearFileStorageClass
import com.sza.fastmediasorter.wear.domain.model.WearMediaFile
import com.sza.fastmediasorter.wear.domain.model.WearOpenOnPhoneOutcome
import com.sza.fastmediasorter.wear.domain.model.WearOpenOnPhoneRequest
import com.sza.fastmediasorter.wear.domain.repository.WearFileSenderRepository
import com.sza.fastmediasorter.wear.domain.repository.WearOpenOnPhoneRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Pins the move contract: strategic §7 rates a move that deletes a source the phone never received
 * as losing the file outright, and a fake sender is the only way to prove the gate without a paired
 * phone in the loop.
 */
class PerformWearFileOperationUseCaseTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val sender = FakeSenderRepository()
    private val opener = FakeOpenOnPhoneRepository()
    private lateinit var useCase: PerformWearFileOperationUseCase
    private lateinit var cacheDir: File
    private lateinit var context: Context
    private lateinit var receivers: WearSendToReceiversRepository

    @Before
    fun setUp() {
        context = mockk<Context>(relaxed = true)
        cacheDir = temporaryFolder.newFolder("cache")
        // The policy calls an app-owned file anything under getExternalFilesDir's parent, so the
        // temporary folder becomes the sandbox root and every file this test writes is APP_OWNED.
        every { context.getExternalFilesDir(null) } returns temporaryFolder.newFolder("files")
        // The classifier also reads the cache directory, to tell a paired-phone copy from the watch's
        // own file. A relaxed mock answers it with a mock File, which the File constructor rejects.
        every { context.cacheDir } returns cacheDir
        // S2142: every file this test writes is APP_OWNED, which takes the file branch and never
        // reaches the resolver, so the confirmation seam only has to exist - not to answer anything.
        val consent = mockk<WearMediaStoreConsent>(relaxed = true)
        // S2142: the published receiver list, answered explicitly rather than relaxed - the policy
        // reads it to decide whether «Send to..» is offered at all, and a relaxed answer would make
        // that decision by accident.
        receivers = mockk<WearSendToReceiversRepository>()
        every { receivers.observe() } returns MutableStateFlow(emptyList())
        // S3359: the policy asks this one whether the watch can be written to at all; `publish` and
        // `freeBytes` stay unstubbed, so an operation of this instance reaching the watch's own
        // storage still fails loudly instead of being answered by a relaxed mock.
        val publisher = mockk<WearWatchFilePublisher>()
        every { publisher.isAvailable() } returns true
        useCase = PerformWearFileOperationUseCase(
            capabilityPolicy = WearFileCapabilityPolicy(context, consent, receivers, publisher),
            senderRepository = sender,
            openOnPhoneRepository = opener,
            stager = WearMediaFileStager(context),
            mediaStoreWriter = WearMediaStoreFileWriter(context, consent),
            watchPublisher = publisher,
            // Unstubbed on purpose: no local operation may read a share, and a relaxed answer would
            // let one do so unnoticed.
            downloadNetworkFile = mockk(),
            sendToReceivers = receivers,
            // The operations under test are the local ones, which never reach either of these.
            reachability = mockk(relaxed = true),
            sendToLauncher = mockk(relaxed = true)
        )
    }

    @Test
    fun `an unreachable phone leaves the source on disk`() = runTest {
        val source = temporaryFolder.newFile("note.txt").apply { writeText("payload") }
        sender.outcome = WearFileSendOutcome.PHONE_UNREACHABLE

        val results = useCase(listOf(mediaFile(source)), WearFileOperation.MoveToPhone, false).toList()

        assertEquals(WearFileOperationOutcome.PHONE_UNREACHABLE, results.single().outcome)
        assertTrue("a move that did not arrive must not delete its source", source.exists())
    }

    @Test
    fun `a confirmed send removes the source`() = runTest {
        val source = temporaryFolder.newFile("note.txt").apply { writeText("payload") }
        sender.outcome = WearFileSendOutcome.SENT

        val results = useCase(listOf(mediaFile(source)), WearFileOperation.MoveToPhone, false).toList()

        assertEquals(WearFileOperationOutcome.SUCCEEDED, results.single().outcome)
        assertTrue("a confirmed move must remove its source", !source.exists())
    }

    @Test
    fun `a network file is refused for every operation and never reaches the sender`() = runTest {
        val source = temporaryFolder.newFile("remote.txt")
        val operations = listOf(
            WearFileOperation.SendToPhone,
            WearFileOperation.MoveToPhone,
            WearFileOperation.Delete,
            WearFileOperation.Rename("other.txt")
        )

        operations.forEach { operation ->
            val results = useCase(listOf(mediaFile(source)), operation, isNetworkSource = true).toList()
            assertEquals(WearFileOperationOutcome.REFUSED_UNSUPPORTED, results.single().outcome)
        }

        assertEquals(0, sender.calls)
        assertTrue("a refused file must be left untouched", source.exists())
    }

    @Test
    fun `a batch of three reports three results when the middle one fails`() = runTest {
        val first = temporaryFolder.newFile("first.txt")
        val second = temporaryFolder.newFile("second.txt")
        val third = temporaryFolder.newFile("third.txt")
        sender.outcomesByName = mapOf("second.txt" to WearFileSendOutcome.FAILED)
        sender.outcome = WearFileSendOutcome.SENT

        val files = listOf(mediaFile(first), mediaFile(second), mediaFile(third))
        val results = useCase(files, WearFileOperation.SendToPhone, false).toList()

        assertEquals(3, results.size)
        assertEquals(
            listOf(
                WearFileOperationOutcome.SUCCEEDED,
                WearFileOperationOutcome.FAILED,
                WearFileOperationOutcome.SUCCEEDED
            ),
            results.map { it.outcome }
        )
    }

    /**
     * S2004: the phone's four answers must stay four answers here. A notification the user has yet to
     * tap is not a file already on screen, and a refusal the phone gave is not the silence of a phone
     * out of range - strategic §11 criterion 9 asks the watch to tell them apart.
     */
    @Test
    fun `each phone answer to an open request maps to its own outcome`() = runTest {
        val copy = phoneCopy("clip.mp4")
        val expected = mapOf(
            WearOpenOnPhoneOutcome.SHOWN to WearFileOperationOutcome.OPENED_ON_PHONE,
            WearOpenOnPhoneOutcome.NOTIFIED to WearFileOperationOutcome.NOTIFIED_ON_PHONE,
            WearOpenOnPhoneOutcome.REFUSED_NO_NOTIFICATION to
                WearFileOperationOutcome.REFUSED_PHONE_NOTIFICATIONS_OFF,
            WearOpenOnPhoneOutcome.NOT_FOUND to WearFileOperationOutcome.FAILED
        )

        expected.forEach { (answer, outcome) ->
            opener.answer = answer
            val results = useCase(listOf(copy), WearFileOperation.OpenOnPhone(TOKEN), false).toList()
            assertEquals(outcome, results.single().outcome)
        }
    }

    @Test
    fun `a phone that never answers an open request reads as unreachable`() = runTest {
        opener.answer = null

        val results = useCase(
            listOf(phoneCopy("clip.mp4")),
            WearFileOperation.OpenOnPhone(TOKEN),
            false
        ).toList()

        assertEquals(WearFileOperationOutcome.PHONE_UNREACHABLE, results.single().outcome)
    }

    /** The watch's own file has no original on the phone, so the request never leaves the watch. */
    @Test
    fun `a watch owned file is refused an open request and nothing is asked of the phone`() = runTest {
        val source = temporaryFolder.newFile("note.txt")

        val results = useCase(listOf(mediaFile(source)), WearFileOperation.OpenOnPhone(TOKEN), false).toList()

        assertEquals(WearFileOperationOutcome.REFUSED_UNSUPPORTED, results.single().outcome)
        assertEquals(0, opener.calls)
    }

    @Test
    fun `a phone copy is published to the watch and the original is left alone`() = runTest {
        val publisher = publisher(WearWatchFilePublisher.Result.Published(mockk(), "clip.mp4"))
        val copy = phoneCopy("clip.mp4", VIDEO_MIME_TYPE)

        val results = copyUseCase(publisher)(listOf(copy), WearFileOperation.CopyToWatch, false).toList()

        assertEquals(WearFileOperationOutcome.SUCCEEDED, results.single().outcome)
        assertNull("a name the store kept is not worth repeating", results.single().finalName)
        assertTrue("a copy must leave the file it copied from", File(copy.uri.path!!).exists())
    }

    /** S1863's answer to a collision: the suffix the store chose is the name the owner is told. */
    @Test
    fun `a name the store had to suffix is reported back`() = runTest {
        val publisher = publisher(WearWatchFilePublisher.Result.Published(mockk(), "clip (1).mp4"))

        val results = copyUseCase(publisher)(
            listOf(phoneCopy("clip.mp4", VIDEO_MIME_TYPE)),
            WearFileOperation.CopyToWatch,
            false
        ).toList()

        assertEquals("clip (1).mp4", results.single().finalName)
    }

    /**
     * Strategic §11 criterion 8: the refusal arrives before the write, not out of the middle of one.
     * A half-written row would be a file the owner can find and cannot play.
     */
    @Test
    fun `a watch with less free space than the file writes nothing at all`() = runTest {
        val publisher = mockk<WearWatchFilePublisher>()
        every { publisher.freeBytes() } returns 1L

        val results = copyUseCase(publisher)(
            listOf(phoneCopy("clip.mp4", VIDEO_MIME_TYPE)),
            WearFileOperation.CopyToWatch,
            false
        ).toList()

        assertEquals(WearFileOperationOutcome.REFUSED_NO_SPACE, results.single().outcome)
        verify(exactly = 0) { publisher.publish(any(), any(), any(), any()) }
    }

    /** A document reaches no category list on the watch, so the copy is refused before space is asked. */
    @Test
    fun `a document is refused because the watch lists it nowhere`() = runTest {
        val publisher = mockk<WearWatchFilePublisher>()

        val results = copyUseCase(publisher)(
            listOf(phoneCopy("manual.pdf", "application/pdf")),
            WearFileOperation.CopyToWatch,
            false
        ).toList()

        assertEquals(WearFileOperationOutcome.REFUSED_UNSUPPORTED, results.single().outcome)
        verify(exactly = 0) { publisher.freeBytes() }
    }

    @Test
    fun `a publish that did not finish is reported as a failure`() = runTest {
        val publisher = publisher(WearWatchFilePublisher.Result.Failed)

        val results = copyUseCase(publisher)(
            listOf(phoneCopy("clip.mp4", VIDEO_MIME_TYPE)),
            WearFileOperation.CopyToWatch,
            false
        ).toList()

        assertEquals(WearFileOperationOutcome.FAILED, results.single().outcome)
    }

    /**
     * Strategic §11 criterion 4: the share is read through the routing S1687 already owns, so the
     * copy adds no network verb - research artifact 02's finding, exercised end to end here.
     */
    @Test
    fun `a network file is downloaded and published to the watch`() = runTest {
        val fetched = temporaryFolder.newFile("fetched.mp3").apply { writeText("payload") }
        val publisher = publisher(WearWatchFilePublisher.Result.Published(mockk(), "remote.mp3"))
        val download = mockk<DownloadNetworkFileUseCase>()
        coEvery { download(any(), any()) } returns Result.success(fetched)

        val results = networkCopy(publisher, download, sourceId = SOURCE_ID).toList()

        assertEquals(WearFileOperationOutcome.SUCCEEDED, results.single().outcome)
        coVerify(exactly = 1) { download(any(), DownloadNetworkFileUseCase.Kind.AUDIO) }
    }

    /** The id names the share the bytes live on; without it there is nothing to read the file from. */
    @Test
    fun `a network copy with no source id fails and asks for no download`() = runTest {
        val publisher = mockk<WearWatchFilePublisher>()
        val download = mockk<DownloadNetworkFileUseCase>()

        val results = networkCopy(publisher, download, sourceId = null).toList()

        assertEquals(WearFileOperationOutcome.FAILED, results.single().outcome)
        coVerify(exactly = 0) { download(any(), any()) }
    }

    /** Refusing after the transfer would have spent exactly what the refusal exists to save. */
    @Test
    fun `a network copy with less free space than the file downloads nothing`() = runTest {
        val publisher = mockk<WearWatchFilePublisher>()
        every { publisher.freeBytes() } returns 1L
        val download = mockk<DownloadNetworkFileUseCase>()

        val results = networkCopy(publisher, download, sourceId = SOURCE_ID).toList()

        assertEquals(WearFileOperationOutcome.REFUSED_NO_SPACE, results.single().outcome)
        coVerify(exactly = 0) { download(any(), any()) }
    }

    private fun networkCopy(
        publisher: WearWatchFilePublisher,
        download: DownloadNetworkFileUseCase,
        sourceId: String?
    ) = copyUseCase(publisher, download, WearFileStorageClass.NETWORK)(
        files = listOf(networkFile("remote.mp3")),
        operation = WearFileOperation.CopyToWatch,
        isNetworkSource = true,
        networkSourceId = sourceId
    )

    /**
     * A listed share entry: only its name, size and type are read, and its path is never opened -
     * the bytes arrive from the download instead.
     */
    private fun networkFile(name: String): WearMediaFile =
        mediaFile(temporaryFolder.newFile(name).apply { writeText("payload") }, AUDIO_MIME_TYPE)

    private fun publisher(result: WearWatchFilePublisher.Result): WearWatchFilePublisher {
        val publisher = mockk<WearWatchFilePublisher>()
        every { publisher.freeBytes() } returns FREE_SPACE
        every { publisher.publish(any(), any(), any(), any()) } returns result
        return publisher
    }

    /**
     * The executor wired to a policy that already offers the copy, which the shipped one does not
     * until the menu stage of S3359.
     *
     * Without the stub every case above would stop at the capability gate and prove only that the
     * gate works - and the gate is the one thing these cases are not about.
     */
    private fun copyUseCase(
        publisher: WearWatchFilePublisher,
        download: DownloadNetworkFileUseCase = mockk(),
        storageClass: WearFileStorageClass = WearFileStorageClass.PHONE_COPY
    ): PerformWearFileOperationUseCase {
        val policy = mockk<WearFileCapabilityPolicy>()
        every { policy.classify(any(), any()) } returns storageClass
        every { policy.allowedOperations(any<WearMediaFile>(), any()) } returns
            setOf(WearFileOperationKind.COPY_TO_WATCH)
        return PerformWearFileOperationUseCase(
            capabilityPolicy = policy,
            senderRepository = sender,
            openOnPhoneRepository = opener,
            stager = WearMediaFileStager(context),
            mediaStoreWriter = mockk(relaxed = true),
            watchPublisher = publisher,
            downloadNetworkFile = download,
            sendToReceivers = receivers,
            reachability = mockk(relaxed = true),
            sendToLauncher = mockk(relaxed = true)
        )
    }

    /** A file where a copy fetched from the phone lands, so the policy calls it a paired-phone copy. */
    private fun phoneCopy(name: String, mimeType: String = TEXT_MIME_TYPE): WearMediaFile {
        val directory = File(cacheDir, WEAR_PHONE_FILE_CACHE_DIR).apply { mkdirs() }
        return mediaFile(File(directory, name).apply { writeText("payload") }, mimeType)
    }

    /** A file URI mocked rather than parsed: `Uri.parse` is not available to a plain JVM test. */
    private fun mediaFile(file: File, mimeType: String = TEXT_MIME_TYPE): WearMediaFile {
        val uri = mockk<Uri>()
        every { uri.scheme } returns "file"
        every { uri.path } returns file.absolutePath
        return WearMediaFile(
            id = file.name.hashCode().toLong(),
            name = file.name,
            uri = uri,
            mimeType = mimeType,
            size = file.length(),
            dateModified = 0L
        )
    }

    private class FakeSenderRepository : WearFileSenderRepository {
        var outcome: WearFileSendOutcome = WearFileSendOutcome.SENT
        var outcomesByName: Map<String, WearFileSendOutcome> = emptyMap()
        var calls: Int = 0

        /** S2142: the errand the last [sendFile] carried, so a test can assert it crossed at all. */
        var lastReceiverId: String? = null

        /** S2142: what the pre-flight reachability check answers; false stops before any staging. */
        var phoneReachable: Boolean = true

        override suspend fun sendFile(
            file: File,
            sendToReceiverId: String?
        ): com.sza.fastmediasorter.wear.domain.repository.WearFileSendResult {
            calls++
            lastReceiverId = sendToReceiverId
            val resOutcome = outcomesByName[file.name] ?: outcome
            return com.sza.fastmediasorter.wear.domain.repository.WearFileSendResult(resOutcome)
        }

        override suspend fun isPhoneReachable(): Boolean = phoneReachable

        override suspend fun sendUri(
            uri: android.net.Uri,
            displayName: String,
            sizeBytes: Long
        ): com.sza.fastmediasorter.wear.domain.repository.WearFileSendResult {
            calls++
            val resOutcome = outcomesByName[displayName] ?: outcome
            return com.sza.fastmediasorter.wear.domain.repository.WearFileSendResult(resOutcome)
        }
    }

    private class FakeOpenOnPhoneRepository : WearOpenOnPhoneRepository {
        /** Null stands for the phone never answering, which the repository reports the same way. */
        var answer: WearOpenOnPhoneOutcome? = WearOpenOnPhoneOutcome.SHOWN
        var calls: Int = 0

        override suspend fun requestOpen(request: WearOpenOnPhoneRequest): WearOpenOnPhoneOutcome? {
            calls++
            return answer
        }
    }

    private companion object {
        /** Any address will do: the watch echoes what the phone issued and never reads it. */
        const val TOKEN = "content://phone/clip.mp4"

        const val TEXT_MIME_TYPE = "text/plain"
        const val VIDEO_MIME_TYPE = "video/mp4"
        const val AUDIO_MIME_TYPE = "audio/mpeg"

        /** Any id will do: the executor hands it to the download, which resolves the share itself. */
        const val SOURCE_ID = "smb-1"

        /** More room than any test file needs, so only the case that refuses space says otherwise. */
        const val FREE_SPACE = 1_000_000L
    }
}
