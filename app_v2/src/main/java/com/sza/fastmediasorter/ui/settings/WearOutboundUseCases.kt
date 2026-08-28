package com.sza.fastmediasorter.ui.settings

import com.sza.fastmediasorter.domain.usecase.PushWearSettingsUseCase
import com.sza.fastmediasorter.domain.usecase.PushWearStreamPinsUseCase
import com.sza.fastmediasorter.domain.usecase.SendPlaybackCommandUseCase
import com.sza.fastmediasorter.domain.usecase.SendResourcesToWatchUseCase
import javax.inject.Inject

/**
 * S2034: everything the companion window sends outward over the Data Layer, as one collaborator.
 *
 * The three share a failure mode - an unreachable watch - and a direction, which is what makes them a
 * group rather than a bag; the inbound import and the resource registry stay outside deliberately.
 * Grouped because [WearSyncViewModel] had reached detekt's constructor ceiling, and the shape the
 * repo already uses for this is a named dependency holder (`BrowseStateSyncUseCases`), not a
 * suppression.
 */
class WearOutboundUseCases @Inject constructor(
    val sendResources: SendResourcesToWatchUseCase,
    val pushSettings: PushWearSettingsUseCase,
    val sendPlaybackCommand: SendPlaybackCommandUseCase,
    val pushStreamPins: PushWearStreamPinsUseCase
)
