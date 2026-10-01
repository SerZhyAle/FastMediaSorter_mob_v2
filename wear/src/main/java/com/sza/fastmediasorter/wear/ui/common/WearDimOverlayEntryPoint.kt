package com.sza.fastmediasorter.wear.ui.common

import com.sza.fastmediasorter.wear.domain.usecase.ObserveHeadingUseCase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Hilt EntryPoint for [WearDimOverlay], which is raised outside any Hilt ViewModel. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WearDimOverlayEntryPoint {
    /** S3370: live device heading for the dim overlay's spark pair. */
    fun heading(): ObserveHeadingUseCase
}
