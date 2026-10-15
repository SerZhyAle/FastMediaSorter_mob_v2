package com.sza.fastmediasorter.domain.usecase.transfer

import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.model.transfer.DriveRefusalReason
import com.sza.fastmediasorter.domain.transfer.DriveChannelSettings
import com.sza.fastmediasorter.domain.transfer.DriveFileChannel
import com.sza.fastmediasorter.domain.transfer.DriveProducerTarget
import com.sza.fastmediasorter.domain.transfer.DriveRequestProgress
import com.sza.fastmediasorter.testing.createMediaResource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FetchScheduledChangesViaDriveUseCaseTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val settings = mockk<DriveChannelSettings>()
    private val resolve = mockk<ResolveDriveProducerUseCase>()
    private val channel = mockk<DriveFileChannel>()
    private val useCase = FetchScheduledChangesViaDriveUseCase(settings, resolve, channel)

    private val producer = DriveProducerTarget(
        deviceId = "dev-2",
        deviceName = "Tablet",
        lastSeenMs = null,
        resourceId = "res-1",
        rootPath = "DCIM",
        pinProtected = false
    )

    private val record = createMediaResource(id = 1L, type = ResourceType.SFTP, path = "sftp://tablet:2222/DCIM")
        .copy(exchangeResourceId = "res-1")

    @Test
    fun `the window start goes to the producer and every reply up to final is kept`() = runTest {
        every { channel.changes("dev-2", "res-1", "DCIM", "1970-01-01T00:00:05Z") } returns flowOf(
            DriveRequestProgress.Waiting("Tablet", null),
            DriveRequestProgress.Files(listOf("/in/a.jpg"), "2026-10-15T10:00:00Z", final = false),
            DriveRequestProgress.Files(listOf("/in/b.jpg"), "2026-10-15T10:00:00Z", final = true),
        )

        val outcome = useCase(producer, 5_000L)

        assertEquals(FetchScheduledChangesViaDriveUseCase.Outcome.Fetched(listOf("/in/a.jpg", "/in/b.jpg")), outcome)
    }

    @Test
    fun `an answer cut short removes the files it already placed`() = runTest {
        val partial = tempFolder.newFile("a.jpg")
        every { channel.changes(any(), any(), any(), null) } returns flowOf(
            DriveRequestProgress.Files(listOf(partial.path), null, final = false),
            DriveRequestProgress.TimedOut("Tablet", null),
        )

        val outcome = useCase(producer, null) as FetchScheduledChangesViaDriveUseCase.Outcome.Unavailable

        assertTrue(outcome.reason.contains("did not answer"))
        assertFalse(partial.exists())
    }

    @Test
    fun `a refusal names its cause`() = runTest {
        every { channel.changes(any(), any(), any(), any()) } returns
            flowOf(DriveRequestProgress.Refused(DriveRefusalReason.NOT_PERMITTED))

        val outcome = useCase(producer, null) as FetchScheduledChangesViaDriveUseCase.Outcome.Unavailable

        assertTrue(outcome.reason.contains("not shared via Google Drive"))
    }

    @Test
    fun `a PIN-locked folder is never requested`() = runTest {
        val outcome = useCase(producer.copy(pinProtected = true), null)

        assertTrue(outcome is FetchScheduledChangesViaDriveUseCase.Outcome.Unavailable)
        verify(exactly = 0) { channel.changes(any(), any(), any(), any()) }
    }

    @Test
    fun `the channel switched off means no Drive path and no lookup`() = runTest {
        every { settings.enabled } returns flowOf(false)

        assertNull(useCase.producerFor(record))
        coVerify(exactly = 0) { resolve(any<MediaResource>()) }
    }

    @Test
    fun `a record that is not from another device has no Drive path`() = runTest {
        every { settings.enabled } returns flowOf(true)

        assertNull(useCase.producerFor(record.copy(exchangeResourceId = null)))
    }

    @Test
    fun `an enabled channel resolves the producer of the record`() = runTest {
        every { settings.enabled } returns flowOf(true)
        coEvery { resolve(record) } returns producer

        assertEquals(producer, useCase.producerFor(record))
    }
}
