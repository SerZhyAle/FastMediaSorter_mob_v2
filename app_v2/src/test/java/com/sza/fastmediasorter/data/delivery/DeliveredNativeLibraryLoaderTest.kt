package com.sza.fastmediasorter.data.delivery

import android.content.Context
import com.sza.fastmediasorter.domain.delivery.BundledDeliverableSets
import com.sza.fastmediasorter.domain.delivery.DeliverableCapabilityRepository
import com.sza.fastmediasorter.domain.delivery.DeliverableSet
import com.sza.fastmediasorter.domain.delivery.DeliverableSetContributor
import com.sza.fastmediasorter.domain.delivery.DeliverableSourceDescriptor
import com.sza.fastmediasorter.domain.delivery.PayloadFile
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

@OptIn(ExperimentalCoroutinesApi::class)
class DeliveredNativeLibraryLoaderTest {

    private val context = mockk<Context>(relaxed = true)
    private val verifier = mockk<PayloadIntegrityVerifier>(relaxed = true)
    private val bundledSets = mockk<BundledDeliverableSets>()
    private val contributors = emptySet<DeliverableSetContributor>()
    private val capabilityRepository = mockk<DeliverableCapabilityRepository>(relaxed = true)

    @Test
    fun `load makes a delivered so payload read-only before loading it`() = runTest {
        val filesDir = Files.createTempDirectory("delivery-test").toFile()
        val setDir = File(filesDir, "delivery/${DeliverableSet.FFMPEG_DTS.name}").apply { mkdirs() }
        val payload = File(setDir, "libtest.so").apply { writeBytes(byteArrayOf(0x7F)) }
        every { context.filesDir } returns filesDir
        every { bundledSets.contains(DeliverableSet.FFMPEG_DTS) } returns false
        // A relaxed mock answers a sealed result with its first subclass, Failed, which stops load before the
        // read-only step; the payload has to pass verification for that step to be reached at all.
        every { verifier.verify(any(), any()) } returns PayloadIntegrityVerifier.Result.Verified
        val descriptor = DeliverableSourceDescriptor(
            set = DeliverableSet.FFMPEG_DTS,
            files = listOf(PayloadFile("libtest.so", emptyList(), "", 1L)),
        )
        val contributor = object : DeliverableSetContributor {
            override fun descriptors() = mapOf(DeliverableSet.FFMPEG_DTS to descriptor)
        }

        val loader = DeliveredNativeLibraryLoader(
            context = context,
            verifier = verifier,
            bundledSets = bundledSets,
            contributors = setOf(contributor),
            capabilityRepository = capabilityRepository,
            recoveryScope = this
        )

        // The host JVM cannot attach or load the fake payload; only the read-only step before that matters.
        runCatching { loader.load(DeliverableSet.FFMPEG_DTS) }

        assertFalse(payload.canWrite())
        filesDir.walkBottomUp().forEach { it.setWritable(true) }
        filesDir.deleteRecursively()
    }

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
