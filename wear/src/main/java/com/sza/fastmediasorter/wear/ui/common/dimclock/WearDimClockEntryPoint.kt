package com.sza.fastmediasorter.wear.ui.common.dimclock

import com.sza.fastmediasorter.wear.data.power.WearPowerStateObserver
import com.sza.fastmediasorter.wear.domain.repository.WearPreferencesRepository
import com.sza.fastmediasorter.wear.domain.repository.WearSystemInfoDataSource
import com.sza.fastmediasorter.wear.domain.usecase.ObserveHeadingUseCase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * S3256: Hilt EntryPoint for WearDimClock dependencies needed inside WearDimOverlay.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WearDimClockEntryPoint {
    fun preferencesRepository(): WearPreferencesRepository
    fun powerStateObserver(): WearPowerStateObserver
    fun systemInfoDataSource(): WearSystemInfoDataSource

    /** S3370: live device heading for the dim overlay's spark pair. */
    fun heading(): ObserveHeadingUseCase
}
