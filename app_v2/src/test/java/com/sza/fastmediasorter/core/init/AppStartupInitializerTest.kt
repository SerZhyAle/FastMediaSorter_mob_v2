package com.sza.fastmediasorter.core.init

import android.content.Context
import com.sza.fastmediasorter.core.coordinator.RemoteSourceDisableCoordinator
import com.sza.fastmediasorter.data.input.DefaultsMapLoader
import com.sza.fastmediasorter.data.input.InputBindingRepository
import com.sza.fastmediasorter.data.stats.StatsSessionTracker
import com.sza.fastmediasorter.domain.repository.PlaybackPositionRepository
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.repository.StatisticsRepository
import com.sza.fastmediasorter.domain.repository.ThumbnailCacheRepository
import com.sza.fastmediasorter.domain.usecase.RenameVirtualResourcesUseCase
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppStartupInitializerTest {

    @Test
    fun `connection throttle bootstrap guard starts only once`() {
        val initializer = AppStartupInitializer(
            context = mockk<Context>(relaxed = true),
            settingsRepository = dagger.Lazy { mockk<SettingsRepository>(relaxed = true) },
            resourceRepository = dagger.Lazy { mockk<ResourceRepository>(relaxed = true) },
            playbackPositionRepository = dagger.Lazy { mockk<PlaybackPositionRepository>(relaxed = true) },
            thumbnailCacheRepository = dagger.Lazy { mockk<ThumbnailCacheRepository>(relaxed = true) },
            applicationScope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob()),
            renameVirtualResourcesUseCase = dagger.Lazy { mockk<RenameVirtualResourcesUseCase>(relaxed = true) },
            inputBindingRepository = dagger.Lazy { mockk<InputBindingRepository>(relaxed = true) },
            defaultsMapLoader = dagger.Lazy { mockk<DefaultsMapLoader>(relaxed = true) },
            remoteSourceDisableCoordinator = mockk<RemoteSourceDisableCoordinator>(relaxed = true),
            statisticsRepository = dagger.Lazy { mockk<StatisticsRepository>(relaxed = true) },
            statsSessionTracker = dagger.Lazy { mockk<StatsSessionTracker>(relaxed = true) },
        )

        assertTrue(initializer.tryStartConnectionThrottleManagerInitialization())
        assertFalse(initializer.tryStartConnectionThrottleManagerInitialization())
    }
}