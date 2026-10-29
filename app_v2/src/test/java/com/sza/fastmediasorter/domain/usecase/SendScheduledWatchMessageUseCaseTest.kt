package com.sza.fastmediasorter.domain.usecase

import com.google.gson.Gson
import com.sza.fastmediasorter.core.capability.MediaCapabilities
import com.sza.fastmediasorter.data.wear.ScheduledWatchImageEncoder
import com.sza.fastmediasorter.data.wear.ScheduledWatchReceiptRegistry
import com.sza.fastmediasorter.domain.model.ScheduledOpType
import com.sza.fastmediasorter.domain.model.ScheduledOperation
import com.sza.fastmediasorter.domain.model.ScheduledRunOutcome
import com.sza.fastmediasorter.domain.model.TimeFilter
import com.sza.fastmediasorter.domain.model.WearNode
import com.sza.fastmediasorter.domain.model.WearScheduledMessageCodec
import com.sza.fastmediasorter.domain.model.WearScheduledMessageParseResult
import com.sza.fastmediasorter.domain.model.WearScheduledMessageReceipt
import com.sza.fastmediasorter.domain.model.WearScheduledMessageReceiptReasons
import com.sza.fastmediasorter.domain.repository.WearableDataLayerRepository
import com.sza.fastmediasorter.service.WearDataLayerPaths
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** S4248: every way a scheduled watch message ends, with the watch side replaced by fakes. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SendScheduledWatchMessageUseCaseTest {

    private val gson = Gson()
    private val receipts = ScheduledWatchReceiptRegistry()
    private val dataLayer = mockk<WearableDataLayerRepository>(relaxed = true)
    private val encoder = mockk<ScheduledWatchImageEncoder>()
    private val capabilities = mockk<MediaCapabilities> { every { supportsWearCompanion } returns true }

    private val useCase = SendScheduledWatchMessageUseCase(capabilities, dataLayer, encoder, receipts, gson)

    private val operation = ScheduledOperation(
        id = 5,
        sourceResourceId = null,
        operationType = ScheduledOpType.WATCH_MESSAGE,
        targetResourceId = null,
        timeFilter = TimeFilter.ALL,
        startTimeHour = 9,
        startTimeMinute = 0,
        intervalHours = 24,
        intervalMinutes = 0,
        watchTitle = "Water",
        watchText = "Drink a glass",
    )

    @Test
    fun `build without the bridge skips with its own reason`() = runTest {
        every { capabilities.supportsWearCompanion } returns false

        val result = useCase(operation)

        assertEquals(ScheduledRunOutcome.SKIPPED, result.outcome)
        assertEquals("Watch is not supported in this build", result.skippedReason)
    }

    @Test
    fun `switched-off companion skips and sends nothing`() = runTest {
        coEvery { dataLayer.isCompanionEnabled() } returns false

        val result = useCase(operation)

        assertEquals("Wear Companion is off", result.skippedReason)
        coVerify(exactly = 0) { dataLayer.sendMessage(any(), any(), any()) }
    }

    @Test
    fun `no paired watch nearby is a skip, not an error`() = runTest {
        coEvery { dataLayer.isCompanionEnabled() } returns true
        coEvery { dataLayer.getConnectedNodes() } returns emptyList()

        val result = useCase(operation)

        assertEquals(ScheduledRunOutcome.SKIPPED, result.outcome)
        assertEquals("Watch not reachable", result.skippedReason)
    }

    @Test
    fun `a shown receipt makes the run OK`() = runTest {
        answerWith { WearScheduledMessageReceipt(it, shown = true) }

        val result = useCase(operation)

        assertEquals(ScheduledRunOutcome.OK, result.outcome)
    }

    @Test
    fun `a refused receipt is a skip carrying the watch's reason`() = runTest {
        answerWith {
            WearScheduledMessageReceipt(it, false, WearScheduledMessageReceiptReasons.NO_NOTIFICATION_PERMISSION)
        }

        val result = useCase(operation)

        assertEquals(ScheduledRunOutcome.SKIPPED, result.outcome)
        assertEquals("Watch is not allowed to show notifications", result.skippedReason)
    }

    @Test
    fun `a watch that never answers is a skip after the wait`() = runTest {
        coEvery { dataLayer.isCompanionEnabled() } returns true
        coEvery { dataLayer.getConnectedNodes() } returns listOf(WearNode("node-1", "Watch"))

        val result = useCase(operation)

        assertEquals("Watch did not confirm", result.skippedReason)
    }

    @Test
    fun `an unreadable picture still sends the text`() = runTest {
        coEvery { encoder.encode(any()) } returns null
        var sentTitle: String? = null
        answerWith { id -> WearScheduledMessageReceipt(id, shown = true) }
        coEvery { dataLayer.sendMessage(any(), WearDataLayerPaths.SCHEDULED_MESSAGE, any()) } answers {
            val parsed = WearScheduledMessageCodec.parse(thirdArg(), gson) as WearScheduledMessageParseResult.Parsed
            sentTitle = parsed.payload.title
            receipts.complete(WearScheduledMessageReceipt(parsed.payload.messageId, shown = true))
        }

        val result = useCase(operation.copy(watchImageUri = "content://gone/1"))

        assertEquals(ScheduledRunOutcome.OK, result.outcome)
        assertTrue(sentTitle == "Water")
    }

    private fun answerWith(receiptFor: (String) -> WearScheduledMessageReceipt) {
        coEvery { dataLayer.isCompanionEnabled() } returns true
        coEvery { dataLayer.getConnectedNodes() } returns listOf(WearNode("node-1", "Watch"))
        coEvery { dataLayer.sendMessage(any(), WearDataLayerPaths.SCHEDULED_MESSAGE, any()) } answers {
            val parsed = WearScheduledMessageCodec.parse(thirdArg(), gson) as WearScheduledMessageParseResult.Parsed
            receipts.complete(receiptFor(parsed.payload.messageId))
        }
    }
}
