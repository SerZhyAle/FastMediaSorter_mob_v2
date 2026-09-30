package com.sza.fastmediasorter.domain.usecase

import android.content.Context
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.coroutines.CoroutineContext

/**
 * S3938: one caller is a click handler on Main, so the storage-root stats must run through the
 * injected IO dispatcher, and only on the path that actually creates the resource.
 */
class EnsureAllFilesPredefinedResourceUseCaseTest {

    private val resourceRepository = mockk<ResourceRepository>()
    private val settingsRepository = mockk<SettingsRepository>()
    private val addResourceUseCase = mockk<AddResourceUseCase>()
    private val ioDispatcher = RecordingDispatcher()
    private val useCase = EnsureAllFilesPredefinedResourceUseCase(
        context = mockk<Context>(relaxed = true),
        resourceRepository = resourceRepository,
        settingsRepository = settingsRepository,
        addResourceUseCase = addResourceUseCase,
        resolveResourceIconUseCase = mockk(relaxed = true),
        ioDispatcher = ioDispatcher
    )

    @Test
    fun `creating the resource stats the root through the io dispatcher`() = runTest {
        val added = slot<MediaResource>()
        coEvery { resourceRepository.getAllResourcesSync() } returns emptyList()
        every { settingsRepository.getSettings() } returns flowOf(AppSettings())
        coEvery { addResourceUseCase(capture(added), true) } returns Result.success(7L)

        val result = useCase().getOrThrow()

        assertEquals(7L, result.resourceId)
        assertTrue(result.created)
        assertEquals(1, ioDispatcher.dispatchCount)
        assertTrue(added.captured.allFiles)
    }

    @Test
    fun `an existing resource is returned without touching storage`() = runTest {
        coEvery { resourceRepository.getAllResourcesSync() } returns emptyList()
        every { settingsRepository.getSettings() } returns flowOf(AppSettings())
        val created = slot<MediaResource>()
        coEvery { addResourceUseCase(capture(created), true) } returns Result.success(3L)
        useCase().getOrThrow()
        coEvery { resourceRepository.getAllResourcesSync() } returns listOf(created.captured.copy(id = 3L))
        val dispatchesBefore = ioDispatcher.dispatchCount

        val result = useCase().getOrThrow()

        assertEquals(3L, result.resourceId)
        assertFalse(result.created)
        assertEquals(dispatchesBefore, ioDispatcher.dispatchCount)
    }

    private class RecordingDispatcher : CoroutineDispatcher() {
        var dispatchCount = 0
            private set

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            dispatchCount++
            block.run()
        }
    }
}
