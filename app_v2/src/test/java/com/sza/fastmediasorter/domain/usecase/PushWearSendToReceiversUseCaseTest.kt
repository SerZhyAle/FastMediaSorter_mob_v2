package com.sza.fastmediasorter.domain.usecase

import android.content.Context
import com.google.gson.Gson
import com.sza.fastmediasorter.core.share.ShareTarget
import com.sza.fastmediasorter.core.share.ShareTargetAvailability
import com.sza.fastmediasorter.core.share.ShareTargetAvailabilityResolver
import com.sza.fastmediasorter.core.share.ShareTargetDefault
import com.sza.fastmediasorter.core.share.ShareTargetIconResolver
import com.sza.fastmediasorter.core.share.ShareTargetRegistry
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.WearEventEnvelope
import com.sza.fastmediasorter.domain.model.WearNode
import com.sza.fastmediasorter.domain.model.WearSendToReceiversPayload
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.repository.WearableDataLayerRepository
import com.sza.fastmediasorter.service.WearDataLayerPaths
import io.mockk.CapturingSlot
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S2142 step 03.4: what the phone publishes to the watch is exactly what the owner left switched on.
 *
 * The two cases pinned here are the ones the phase names as its verification - a switched-off
 * receiver never reaches the watch, and an empty set is still published rather than skipped.
 */
class PushWearSendToReceiversUseCaseTest {

    private val wearRepository = mockk<WearableDataLayerRepository>()
    private val availabilityResolver = mockk<ShareTargetAvailabilityResolver>()
    private val iconResolver = mockk<ShareTargetIconResolver>()
    private val settingsRepository = mockk<SettingsRepository>()
    private val context = mockk<Context>()
    private val gson = Gson()

    private fun target(id: String, availability: ShareTargetAvailability = ShareTargetAvailability.ALWAYS) =
        ShareTarget(
            id = id,
            titleRes = TITLE_RES,
            defaultEnabled = ShareTargetDefault.ALWAYS_ON,
            availability = availability
        )

    /**
     * A real registry and a real enablement rule rather than mocks of both: the point of the
     * publisher is that it reuses the phone's own gates, and mocking them would test nothing.
     */
    private fun useCase(targets: Set<ShareTarget>, settings: AppSettings): PushWearSendToReceiversUseCase {
        val registry = ShareTargetRegistry(targets)
        every { context.getString(TITLE_RES) } returns "Receiver"
        every { iconResolver.resolveLabel(any()) } returns null
        every { availabilityResolver.isAvailable(any(), any()) } returns true
        every { availabilityResolver.isDefaultEnabled(any(), any()) } returns true
        every { settingsRepository.getSettings() } returns flowOf(settings)
        return PushWearSendToReceiversUseCase(
            context = context,
            wearableRepository = wearRepository,
            gson = gson,
            registry = registry,
            availabilityResolver = availabilityResolver,
            iconResolver = iconResolver,
            isEnabled = IsShareTargetEnabledUseCase(registry, availabilityResolver),
            settingsRepository = settingsRepository
        )
    }

    private fun captureEnvelope(): Pair<CapturingSlot<String>, CapturingSlot<WearEventEnvelope>> {
        val pathSlot = slot<String>()
        val envelopeSlot = slot<WearEventEnvelope>()
        coEvery { wearRepository.getConnectedNodes() } returns listOf(WearNode("node-1", "Watch"))
        coEvery { wearRepository.putEnvelopeDataItem(capture(pathSlot), capture(envelopeSlot)) } just Runs
        return pathSlot to envelopeSlot
    }

    private fun decode(envelope: WearEventEnvelope): WearSendToReceiversPayload =
        gson.fromJson(String(envelope.data, Charsets.UTF_8), WearSendToReceiversPayload::class.java)

    @Test
    fun `a receiver switched off on the phone never reaches the watch`() = runTest {
        val settings = AppSettings(
            enableWearCompanion = true,
            disabledShareTargets = setOf("email")
        )
        val (pathSlot, envelopeSlot) = captureEnvelope()

        val result = useCase(setOf(target("email"), target("print")), settings)()

        assertTrue(result.isSuccess)
        assertEquals(WearDataLayerPaths.SEND_TO_RECEIVERS, pathSlot.captured)
        assertEquals(WearDataLayerPaths.EVENT_SEND_TO_RECEIVERS, envelopeSlot.captured.eventType)
        assertEquals(listOf("print"), decode(envelopeSlot.captured).receivers.map { it.id })
    }

    /**
     * The empty set is a real value, not "nothing to say": it is how switching the last receiver off
     * withdraws the list from the watch, and skipping it would strand the previous one there.
     */
    @Test
    fun `an empty set is still published rather than skipped`() = runTest {
        val settings = AppSettings(
            enableWearCompanion = true,
            disabledShareTargets = setOf("email")
        )
        val (_, envelopeSlot) = captureEnvelope()

        val result = useCase(setOf(target("email")), settings)()

        assertTrue(result.isSuccess)
        assertEquals(emptyList<String>(), decode(envelopeSlot.captured).receivers.map { it.id })
    }

    /**
     * The watch receiver is the opposite direction - the phone sending a file to the paired watch -
     * so publishing it would offer the watch a way to send a file to itself.
     */
    @Test
    fun `the paired-watch receiver is never published to the watch`() = runTest {
        val settings = AppSettings(enableWearCompanion = true)
        val (_, envelopeSlot) = captureEnvelope()

        val targets = setOf(
            target("watch", ShareTargetAvailability.REQUIRES_WATCH),
            target("print")
        )
        useCase(targets, settings)()

        assertEquals(listOf("print"), decode(envelopeSlot.captured).receivers.map { it.id })
    }

    @Test
    fun `no connected watch publishes nothing`() = runTest {
        val settings = AppSettings(enableWearCompanion = true)
        coEvery { wearRepository.getConnectedNodes() } returns emptyList()

        val result = useCase(setOf(target("print")), settings)()

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { wearRepository.putEnvelopeDataItem(any(), any()) }
    }

    private companion object {
        /** Any resource id: the label is resolved through a mocked context, never loaded. */
        const val TITLE_RES = 1
    }
}
