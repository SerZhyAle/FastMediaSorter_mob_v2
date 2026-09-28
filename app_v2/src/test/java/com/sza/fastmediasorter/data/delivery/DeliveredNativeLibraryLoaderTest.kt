package com.sza.fastmediasorter.data.delivery

import android.content.Context
import com.sza.fastmediasorter.domain.delivery.BundledDeliverableSets
import com.sza.fastmediasorter.domain.delivery.DeliverableCapabilityRepository
import com.sza.fastmediasorter.domain.delivery.DeliverableSet
import com.sza.fastmediasorter.domain.delivery.DeliverableSetContributor
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeliveredNativeLibraryLoaderTest {

    private val context = mockk<Context>(relaxed = true)
    private val verifier = mockk<PayloadIntegrityVerifier>(relaxed = true)
    private val bundledSets = mockk<BundledDeliverableSets>()
    private val contributors = emptySet<DeliverableSetContributor>()
    private val capabilityRepository = mockk<DeliverableCapabilityRepository>(relaxed = true)

    @Test
    fun `isLoaded returns false initially and true after load for bundled set`() = runTest {
        every { bundledSets.contains(DeliverableSet.FFMPEG_DTS) } returns true

        val loader = DeliveredNativeLibraryLoader(
            context = context,
            verifier = verifier,
            bundledSets = bundledSets,
            contributors = contributors,
            capabilityRepository = capabilityRepository,
            recoveryScope = this
        )

        assertFalse(loader.isLoaded(DeliverableSet.FFMPEG_DTS))
        loader.load(DeliverableSet.FFMPEG_DTS)
        assertTrue(loader.isLoaded(DeliverableSet.FFMPEG_DTS))
    }

    @Test
    fun `loadAsync loads bundled set asynchronously on recoveryScope`() = runTest {
        every { bundledSets.contains(DeliverableSet.FFMPEG_DTS) } returns true

        val loader = DeliveredNativeLibraryLoader(
            context = context,
            verifier = verifier,
            bundledSets = bundledSets,
            contributors = contributors,
            capabilityRepository = capabilityRepository,
            recoveryScope = this
        )

        assertFalse(loader.isLoaded(DeliverableSet.FFMPEG_DTS))
        loader.loadAsync(DeliverableSet.FFMPEG_DTS)
        testScheduler.advanceUntilIdle()

        assertTrue(loader.isLoaded(DeliverableSet.FFMPEG_DTS))
    }

    @Test
    fun `loadAsync skips loading when set is not bundled and not installed`() = runTest {
        every { bundledSets.contains(DeliverableSet.FFMPEG_DTS) } returns false
        every { capabilityRepository.isInstalledBlocking(DeliverableSet.FFMPEG_DTS) } returns false

        val loader = DeliveredNativeLibraryLoader(
            context = context,
            verifier = verifier,
            bundledSets = bundledSets,
            contributors = contributors,
            capabilityRepository = capabilityRepository,
            recoveryScope = this
        )

        loader.loadAsync(DeliverableSet.FFMPEG_DTS)
        testScheduler.advanceUntilIdle()

        assertFalse(loader.isLoaded(DeliverableSet.FFMPEG_DTS))
        verify(exactly = 0) { verifier.verify(any(), any()) }
    }
}
