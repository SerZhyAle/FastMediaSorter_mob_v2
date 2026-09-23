package com.sza.fastmediasorter.ui.common.widget.dimclock.di

import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.unit.UnitSystemProvider
import com.sza.fastmediasorter.ui.common.widget.DimHeadingProvider
import com.sza.fastmediasorter.ui.common.widget.dimclock.DimChipActionRouter
import com.sza.fastmediasorter.ui.common.widget.dimclock.DimChipIconLoader
import com.sza.fastmediasorter.ui.common.widget.dimclock.DimClockInteractionHandler
import com.sza.fastmediasorter.ui.common.widget.dimclock.DimClockStyleProvider
import com.sza.fastmediasorter.ui.common.widget.dimclock.DimStatusContentProvider
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * S3256: Hilt EntryPoint for DimClock dependencies required in View / Manager contexts.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface DimClockEntryPoint {
    fun settingsRepository(): SettingsRepository
    fun dimClockStyleProvider(): DimClockStyleProvider
    fun dimStatusContentProvider(): DimStatusContentProvider
    fun unitSystemProvider(): UnitSystemProvider
    fun dimChipIconLoader(): DimChipIconLoader
    fun dimChipActionRouter(): DimChipActionRouter
    fun dimClockInteractionHandler(): DimClockInteractionHandler

    /** S3370: the heading cache for the dim overlay's spark pair, shared process-wide. */
    fun dimHeadingProvider(): DimHeadingProvider
}
