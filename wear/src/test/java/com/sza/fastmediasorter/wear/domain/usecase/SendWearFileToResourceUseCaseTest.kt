package com.sza.fastmediasorter.wear.domain.usecase

import android.net.Uri
import com.sza.fastmediasorter.wear.domain.model.WearMediaFile
import com.sza.fastmediasorter.wear.domain.model.WearResourceSendFailure
import com.sza.fastmediasorter.wear.domain.model.WearResourceSendReport
import com.sza.fastmediasorter.wear.domain.repository.WearResourceSendRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** S4230: the use case adds nothing to the repository's answer except a guard against an empty source id. */
class SendWearFileToResourceUseCaseTest {

    private val repository = mockk<WearResourceSendRepository>()
    private val useCase = SendWearFileToResourceUseCase(repository)
    private val file = WearMediaFile(
        id = 1L,
        name = "rec.m4a",
        uri = mockk<Uri>(),
        mimeType = "audio/mp4",
        size = 10L,
        dateModified = 0L
    )

    @Test
    fun `a stored file is passed through`() = runTest {
        coEvery { repository.send("nas", file) } returns WearResourceSendReport("NAS", "rec.m4a", null)

        val report = useCase("nas", file)

        assertEquals("rec.m4a", report.finalName)
        assertEquals(true, report.sent)
    }

    @Test
    fun `a failure is passed through`() = runTest {
        coEvery { repository.send("nas", file) } returns
            WearResourceSendReport("NAS", null, WearResourceSendFailure.NO_SPACE)

        val report = useCase("nas", file)

        assertEquals(WearResourceSendFailure.NO_SPACE, report.failure)
        assertEquals(false, report.sent)
    }

    @Test
    fun `a blank source id never reaches the repository`() = runTest {
        val report = useCase("  ", file)

        assertEquals(WearResourceSendFailure.UNREACHABLE, report.failure)
        coVerify(exactly = 0) { repository.send(any(), any()) }
    }
}
