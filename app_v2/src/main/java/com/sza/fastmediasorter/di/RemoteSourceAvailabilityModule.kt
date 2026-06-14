package com.sza.fastmediasorter.di

import com.sza.fastmediasorter.core.capability.MediaCapabilities
import com.sza.fastmediasorter.core.capability.RemoteSourceAvailabilityGate
import com.sza.fastmediasorter.core.di.ApplicationScope
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import javax.inject.Singleton

/**
 * Single binding point for [RemoteSourceAvailabilityGate]. The gate is constructed once and kept
 * for the app lifetime so its settings-flag snapshot survives across screens; it collects settings
 * on the application scope rather than on any view-bound scope.
 */
@Module
@InstallIn(SingletonComponent::class)
object RemoteSourceAvailabilityModule {

    @Provides
    @Singleton
    fun provideRemoteSourceAvailabilityGate(
        mediaCapabilities: MediaCapabilities,
        settingsRepository: SettingsRepository,
        @ApplicationScope appScope: CoroutineScope,
    ): RemoteSourceAvailabilityGate =
        RemoteSourceAvailabilityGate(mediaCapabilities, settingsRepository, appScope)
}
