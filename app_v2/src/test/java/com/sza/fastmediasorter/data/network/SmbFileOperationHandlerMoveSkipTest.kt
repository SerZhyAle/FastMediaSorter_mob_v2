package com.sza.fastmediasorter.data.network

import android.content.Context
import com.sza.fastmediasorter.data.transfer.local.LocalDestinationClassifier
import com.sza.fastmediasorter.data.transfer.local.LocalDestinationWriter
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import com.sza.fastmediasorter.domain.usecase.FileOperation
import com.sza.fastmediasorter.domain.usecase.FileOperationResult
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * S1180: when two scheduled ops share source folders, the first moves+deletes the local originals and
 * the second still holds the pre-deletion snapshot. The second op's SMB move must treat a vanished
 * local source as a skip (the file already reached the destination), not a failure - otherwise every
 * already-moved file is reported as "move failed" and inflates the operation's error count.
 *
 * A full end-to-end reproduction needs a live SMB server and two overlapping scheduled ops, which the
 * emulator cannot provide; this test exercises the skip decision itself, device-independently. The
 * vanished-source guard returns before any SmbClient call, so relaxed collaborator mocks suffice.
 */
class SmbFileOperationHandlerMoveSkipTest {

    private val smbClient = mockk<SmbClient>(relaxed = true)

    private fun newHandler(): SmbFileOperationHandler = SmbFileOperationHandler(
        context = mockk<Context>(relaxed = true),
        smbClient = smbClient,
        sftpClient = mockk(relaxed = true),
        ftpClient = mockk(relaxed = true),
        credentialsRepository = mockk<NetworkCredentialsRepository>(relaxed = true),
        endpointResolver = mockk(relaxed = true),
        stagingDir = mockk(relaxed = true),
        stagingRegistry = mockk(relaxed = true),
        destinationClassifier = mockk<LocalDestinationClassifier>(relaxed = true),
        destinationWriter = mockk<LocalDestinationWriter>(relaxed = true),
    )

    @Test
    fun `move counts a vanished local source as skipped, not an error`() = runBlocking {
        val handler = newHandler()
        // A local path guaranteed not to exist - stands in for an original a concurrent op already moved.
        val vanished = File("/storage/emulated/0/Download/s1180_vanished_source.jpg")
        assertTrue("precondition: source must be absent", !vanished.exists())

        val result = handler.executeMove(
            FileOperation.Move(
                sources = listOf(vanished),
                destination = File("smb://testserver/share/dest"),
                overwrite = false,
            ),
            progressCallback = null,
        )

        assertTrue("expected Success, got $result", result is FileOperationResult.Success)
        result as FileOperationResult.Success
        assertEquals("the vanished source is one skip", 1, result.skippedCount)
        assertEquals("no real move happened", 0, result.processedCount)
        // The skip short-circuits before any upload attempt.
        coVerify(exactly = 0) { smbClient.uploadFile(any(), any(), any(), any(), any()) }
    }
}
