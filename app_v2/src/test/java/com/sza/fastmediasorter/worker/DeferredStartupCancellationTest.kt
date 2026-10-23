package com.sza.fastmediasorter.worker

import android.content.Context
import com.sza.fastmediasorter.core.coordinator.RemoteSourceDisableCoordinator
import com.sza.fastmediasorter.core.init.AppStartupInitializer
import com.sza.fastmediasorter.data.input.DefaultsMapLoader
import com.sza.fastmediasorter.data.input.InputBindingRepository
import com.sza.fastmediasorter.data.stats.StatsSessionTracker
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.repository.PlaybackPositionRepository
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.repository.StatisticsRepository
import com.sza.fastmediasorter.domain.repository.ThumbnailCacheRepository
import com.sza.fastmediasorter.domain.usecase.RenameVirtualResourcesUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeferredStartupCancellationTest {

    private val positions = mockk<PlaybackPositionRepository>(relaxed = true)
    private val thumbnails = mockk<ThumbnailCacheRepository>(relaxed = true)
    private val statistics = mockk<StatisticsRepository>(relaxed = true)
    private val appScope = CoroutineScope(UnconfinedTestDispatcher() + SupervisorJob())

    @After
    fun tearDown() {
        runBlocking { appScope.coroutineContext.job.cancelAndJoin() }
    }

    private val initializer = AppStartupInitializer(
        context = mockk<Context>(relaxed = true),
        settingsRepository = dagger.Lazy {
            mockk<SettingsRepository> {
                every { getSettings() } returns flowOf(mockk<AppSettings>(relaxed = true))
            }
        },
        resourceRepository = dagger.Lazy {
            mockk<ResourceRepository>(relaxed = true) {
                every { getAllResources() } returns flowOf(emptyList())
            }
        },
        playbackPositionRepository = dagger.Lazy { positions },
        thumbnailCacheRepository = dagger.Lazy { thumbnails },
        applicationScope = appScope,
        renameVirtualResourcesUseCase = dagger.Lazy { mockk<RenameVirtualResourcesUseCase>(relaxed = true) },
        inputBindingRepository = dagger.Lazy { mockk<InputBindingRepository>(relaxed = true) },
        defaultsMapLoader = dagger.Lazy { mockk<DefaultsMapLoader>(relaxed = true) },
        remoteSourceDisableCoordinator = mockk<RemoteSourceDisableCoordinator>(relaxed = true),
        statisticsRepository = dagger.Lazy { statistics },
        statsSessionTracker = dagger.Lazy { mockk<StatsSessionTracker>(relaxed = true) },
    )

    private fun failureOf(block: suspend () -> Unit): Throwable? =
        runCatching { runBlocking { block() } }.exceptionOrNull()

    @Test
    fun `cancellation inside a maintenance task stops the pass and reaches the caller`() {
        coEvery { positions.cleanupOldPositions() } throws CancellationException("job cancelled")

        val thrown = failureOf { initializer.runDeferredStartupTasks() }

        assertTrue("expected cancellation, got $thrown", thrown is CancellationException)
        assertEquals("job cancelled", thrown?.message)
        coVerify(exactly = 0) { thumbnails.cleanupOldThumbnails(any()) }
    }

    @Test
    fun `an ordinary failure is logged and the remaining tasks still run`() {
        coEvery { positions.cleanupOldPositions() } throws IllegalStateException("disk full")

        val thrown = failureOf { initializer.runDeferredStartupTasks() }

        assertEquals(null, thrown)
        coVerify(exactly = 1) { thumbnails.cleanupOldThumbnails(any()) }
    }

    @Test
    fun `a fresh pass after a cancelled one completes the skipped work`() {
        var calls = 0
        coEvery { positions.cleanupOldPositions() } coAnswers {
            calls++
            if (calls == 1) throw CancellationException("first pass cancelled")
        }

        assertTrue(failureOf { initializer.runDeferredStartupTasks() } is CancellationException)
        val second = failureOf { initializer.runDeferredStartupTasks() }

        assertEquals(null, second)
        assertEquals(2, calls)
        coVerify(exactly = 1) { thumbnails.cleanupOldThumbnails(any()) }
    }

    @Test
    fun `a launch record cancelled mid-write is retried by the next pass`() {
        var calls = 0
        coEvery { statistics.recordLaunch(any(), any()) } coAnswers {
            calls++
            if (calls == 1) throw CancellationException("first pass cancelled")
        }

        assertTrue(failureOf { initializer.runDeferredStartupTasks() } is CancellationException)
        failureOf { initializer.runDeferredStartupTasks() }
        failureOf { initializer.runDeferredStartupTasks() }

        assertEquals(2, calls)
    }

    @Test
    fun `worker task rethrows cancellation and does not run the next task`() {
        var nextRan = false

        val thrown = failureOf {
            DeferredStartupWorker.runTask("cancelled") { throw CancellationException("stopped") }
            DeferredStartupWorker.runTask("next") { nextRan = true }
        }

        assertTrue(thrown is CancellationException)
        assertEquals(false, nextRan)
    }

    @Test
    fun `worker task swallows an ordinary failure and the next task runs`() {
        var nextRan = false

        val thrown = failureOf {
            DeferredStartupWorker.runTask("failing") { error("boom") }
            DeferredStartupWorker.runTask("next") { nextRan = true }
        }

        assertEquals(null, thrown)
        assertTrue(nextRan)
    }
}
