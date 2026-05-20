package com.sza.fastmediasorter.domain.model

/**
 * Source protocol classification used by the pre-cache formula.
 * Affects the per-protocol safety margin and baseline values used
 * before real speed-test or bitrate data is available.
 */
enum class Protocol {
    LOCAL,
    SMB,
    SFTP,
    FTP,
    CLOUD
}

/**
 * Classification of the current playback session's ability to stream smoothly.
 *
 * - [VIABLE]: ratio < 0.7 - comfortable headroom, normal pre-cache
 * - [MARGINAL]: ratio in [0.7, 0.9) - tight, needs larger pre-cache
 * - [NOT_VIABLE]: ratio >= 0.9 - streaming will stall indefinitely;
 *   the app should offer a local download (offload) instead.
 */
enum class StreamViabilityState {
    VIABLE,
    MARGINAL,
    NOT_VIABLE
}

/**
 * Audit trail of inputs and intermediate values that produced a [PrefetchPlan].
 *
 * Shown to the user in the derivation-info dialog. Also logged via Timber
 * so that future bug reports can include the exact numbers used.
 *
 * [ratio] is bitrate / network_speed expressed as a unit-less fraction:
 * how much of one second of network capacity is needed to play one second of movie.
 */
data class PrefetchDerivation(
    val speedMbps: Double?,
    val bitrateKbps: Int?,
    val ratio: Double,
    val cacheBudgetBytes: Long,
    val maxBufferSecByDevice: Int,
    val maxBufferSecByFile: Int,
    val protocol: Protocol,
    val userMultiplier: PrefetchCacheMultiplier,
    val formulaVersion: Int = 2
)

/**
 * Plan for how much media to pre-cache ahead of the playhead during a single
 * playback session, produced by [PrefetchFormula.compute].
 *
 * All _Sec fields are in seconds-of-movie, not seconds-of-wall-clock or bytes.
 * Consumers translate to ExoPlayer `DefaultLoadControl` milliseconds by `* 1000`.
 *
 * When [viability] is [StreamViabilityState.NOT_VIABLE], the helpers that build
 * a `DefaultLoadControl` MUST NOT apply the plan directly - instead, the player
 * surfaces a streaming-offload offer via `PlayerViewModel.offloadOffer`.
 */
data class PrefetchPlan(
    val viability: StreamViabilityState,
    val targetPrefetchSec: Int,
    val minPrefetchSec: Int,
    val rebufferPrefetchSec: Int,
    val maxBufferSec: Int,
    val sourceLabel: String,
    val derivation: PrefetchDerivation
)
