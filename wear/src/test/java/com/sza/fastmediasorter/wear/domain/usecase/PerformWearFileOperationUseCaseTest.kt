package com.sza.fastmediasorter.wear.domain.usecase

import android.content.Context
import android.net.Uri
import com.sza.fastmediasorter.wear.data.files.WearMediaFileStager
import com.sza.fastmediasorter.wear.domain.files.WearFileCapabilityPolicy
import com.sza.fastmediasorter.wear.domain.model.WearFileOperation
import com.sza.fastmediasorter.wear.domain.model.WearFileOperationOutcome
import com.sza.fastmediasorter.wear.domain.model.WearFileSendOutcome
import com.sza.fastmediasorter.wear.domain.model.WearMediaFile
import com.sza.fastmediasorter.wear.domain.repository.WearFileSenderRepository
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
    private lateinit var useCase: PerformWearFileOperationUseCase

    @Before
    fun setUp() {
        val context = mockk<Context>(relaxed = true)
        // The policy calls an app-owned file anything under getExternalFilesDir's parent, so the
        // temporary folder becomes the sandbox root and every file this test writes is APP_OWNED.
        every { context.getExternalFilesDir(null) } returns temporaryFolder.newFolder("files")
        useCase = PerformWearFileOperationUseCase(
            capabilityPolicy = WearFileCapabilityPolicy(context),
            senderRepository = sender,
            stager = WearMediaFileStager(context)
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

        override suspend fun sendFile(file: File): WearFileSendOutcome {
            calls++
            return outcomesByName[file.name] ?: outcome
        }
    }
}
