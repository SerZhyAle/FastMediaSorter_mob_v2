package com.sza.fastmediasorter.wear.domain.usecase

import android.content.Context
import android.net.Uri
import com.sza.fastmediasorter.wear.data.files.WearMediaFileStager
import com.sza.fastmediasorter.wear.data.files.WearMediaStoreFileWriter
import com.sza.fastmediasorter.wear.domain.files.WEAR_PHONE_FILE_CACHE_DIR
import com.sza.fastmediasorter.wear.domain.files.WearFileCapabilityPolicy
import com.sza.fastmediasorter.wear.domain.files.WearMediaStoreConsent
import com.sza.fastmediasorter.wear.domain.model.WearFileOperation
import com.sza.fastmediasorter.wear.domain.model.WearFileOperationOutcome
import com.sza.fastmediasorter.wear.domain.model.WearFileSendOutcome
import com.sza.fastmediasorter.wear.domain.model.WearMediaFile
import com.sza.fastmediasorter.wear.domain.model.WearOpenOnPhoneOutcome
import com.sza.fastmediasorter.wear.domain.model.WearOpenOnPhoneRequest
import com.sza.fastmediasorter.wear.domain.repository.WearFileSenderRepository
import com.sza.fastmediasorter.wear.domain.repository.WearOpenOnPhoneRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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

    @Before
    fun setUp() {
        val context = mockk<Context>(relaxed = true)
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
        useCase = PerformWearFileOperationUseCase(
            capabilityPolicy = WearFileCapabilityPolicy(context, consent),
            senderRepository = sender,
            openOnPhoneRepository = opener,
            stager = WearMediaFileStager(context),
            mediaStoreWriter = WearMediaStoreFileWriter(context, consent)
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

    /** A file where a copy fetched from the phone lands, so the policy calls it a paired-phone copy. */
    private fun phoneCopy(name: String): WearMediaFile {
        val directory = File(cacheDir, WEAR_PHONE_FILE_CACHE_DIR).apply { mkdirs() }
        return mediaFile(File(directory, name).apply { writeText("payload") })
    }

    /** A file URI mocked rather than parsed: `Uri.parse` is not available to a plain JVM test. */
    private fun mediaFile(file: File): WearMediaFile {
        val uri = mockk<Uri>()
        every { uri.scheme } returns "file"
        every { uri.path } returns file.absolutePath
        return WearMediaFile(
            id = file.name.hashCode().toLong(),
            name = file.name,
            uri = uri,
            mimeType = "text/plain",
            size = file.length(),
            dateModified = 0L
        )
    }

    private class FakeSenderRepository : WearFileSenderRepository {
        var outcome: WearFileSendOutcome = WearFileSendOutcome.SENT
        var outcomesByName: Map<String, WearFileSendOutcome> = emptyMap()
        var calls: Int = 0

        override suspend fun sendFile(file: File): com.sza.fastmediasorter.wear.domain.repository.WearFileSendResult {
            calls++
            val resOutcome = outcomesByName[file.name] ?: outcome
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
    }
}
