package com.sza.fastmediasorter.domain.model

import com.sza.fastmediasorter.data.local.db.StreamingCacheEntry
import com.sza.fastmediasorter.domain.usecase.StreamOffloadUseCase.OffloadRequest

/**
 * One-shot event emitted by `PlayerViewModel.offloadOffer` when a media session is
 * classified [StreamViabilityState.NOT_VIABLE] on open or becomes non-viable via
 * escalation mid-session. The dialog consumes this verbatim — all values are
 * already formatted-ready (VM owns the numbers, dialog owns presentation).
 *
 * See spec: PLAN/spec_adaptive-playback-strategy.md §5.6.
 *
 * [hasEnoughSpace] is false when `fileSizeBytes × 1.1 > freeStorageBytes`; the
 * dialog disables the "Download and play" primary action in that case.
 *
 * [request] is the payload [PlayerViewModel] will feed into
 * [com.sza.fastmediasorter.domain.usecase.StreamOffloadUseCase.run] when the user
 * accepts the offer.
 */
data class OffloadOffer(
    val fileSizeBytes: Long,
    val speedMbps: Double?,
    val requiredMbps: Double?,
    val estDownloadSec: Long?,
    val freeStorageBytes: Long,
    val hasEnoughSpace: Boolean,
    val destinationLabel: String,
    val request: OffloadRequest
)

/**
 * One-shot event emitted by `PlayerViewModel.cleanupPrompt` when the player exits
 * (or switches file) while a local copy exists and the user hasn't suppressed the
 * prompt via `StreamingCacheCleanupMode.AUTO_DELETE` / `AUTO_KEEP`.
 *
 * The helper shows a dialog with Keep / Delete / Don't ask again; the dialog
 * response feeds back into [com.sza.fastmediasorter.domain.repository.StreamingCacheRepository.delete]
 * or a settings update to switch the cleanup mode.
 */
data class CleanupPromptRequest(
    val entry: StreamingCacheEntry
)
