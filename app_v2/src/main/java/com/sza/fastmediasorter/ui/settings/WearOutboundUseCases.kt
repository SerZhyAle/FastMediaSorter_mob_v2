package com.sza.fastmediasorter.ui.settings

import com.sza.fastmediasorter.domain.usecase.PushWearSettingsUseCase
import com.sza.fastmediasorter.domain.usecase.PushWearStreamPinsUseCase
import com.sza.fastmediasorter.domain.usecase.SendPlaybackCommandUseCase
import com.sza.fastmediasorter.domain.usecase.SendResourcesToWatchUseCase
import com.sza.fastmediasorter.domain.usecase.StartWatchListeningUseCase
import com.sza.fastmediasorter.domain.usecase.StopWatchListeningUseCase
import com.sza.fastmediasorter.domain.usecase.SyncWithWatchUseCase
import com.sza.fastmediasorter.domain.usecase.wear.RequestWatchScreenshotUseCase
import com.sza.fastmediasorter.domain.usecase.wear.SendClipboardTextToWatchUseCase
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
    val pushStreamPins: PushWearStreamPinsUseCase,
    // S2484: the unified exchange composes the two legs above rather than adding a third, so it
    // belongs to the same group; joining it here also keeps WearSyncViewModel off the constructor
    // ceiling this holder was created to stay under.
    val syncEverything: SyncWithWatchUseCase,
    // S2550: the listen pair shares this group's direction and its failure mode - an unreachable
    // watch - and joining it here is what keeps the view model off the ceiling above.
    val startListening: StartWatchListeningUseCase,
    val stopListening: StopWatchListeningUseCase,
    // S3109: the clipboard push shares this group's direction and its failure mode - an unreachable
    // watch - so it joins here rather than taking the view model back onto the constructor ceiling.
    val sendClipboardText: SendClipboardTextToWatchUseCase,
    // S3110: the screenshot ask shares this group's direction and its failure mode - an unreachable
    // watch - so it joins here for the same reason the clipboard push did.
    val requestWatchScreenshot: RequestWatchScreenshotUseCase
)
