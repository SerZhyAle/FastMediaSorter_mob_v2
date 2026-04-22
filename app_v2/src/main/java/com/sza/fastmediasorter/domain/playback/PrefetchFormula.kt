package com.sza.fastmediasorter.domain.playback

import com.sza.fastmediasorter.domain.model.PrefetchCacheMultiplier
import com.sza.fastmediasorter.domain.model.PrefetchDerivation
import com.sza.fastmediasorter.domain.model.PrefetchPlan
import com.sza.fastmediasorter.domain.model.Protocol
import com.sza.fastmediasorter.domain.model.StreamViabilityState
import kotlin.math.roundToInt

/**
 * Pure, side-effect-free producer of a [PrefetchPlan] from session inputs.
 *
 * The formula is the physics of progressive streaming:
 * `targetSec = safetyMargin(protocol) / (1 - bitrate/speed)`.
 *
 * When the ratio >= 0.9, streaming is classified as [StreamViabilityState.NOT_VIABLE]
 * and the caller is expected to offer a local-offload flow instead of trying to play.
 * The returned plan still contains a safe fallback `targetPrefetchSec` (minimum floor)
 * so consumers that ignore viability do not crash.
 *
 * See spec: PLAN/spec_adaptive-playback-strategy.md section 5.4.
 */
object PrefetchFormula {

    const val FORMULA_VERSION = 2

    // Viability thresholds on the bitrate/speed ratio.
    private const val RATIO_MARGINAL = 0.7
    private const val RATIO_NOT_VIABLE = 0.9

    // Final hard clamps on the target (seconds of movie).
    private const val MIN_TARGET_SEC = 2
    private const val MAX_TARGET_SEC = 90

    // Floor on (1 - ratio) to keep arithmetic stable at the boundary.
    private const val DIVISOR_FLOOR = 0.1

    // User-multiplier tuning.
    private const val LESS_FACTOR = 0.7
    private const val MORE_HEADROOM_SEC = 10.0

    // Derivation fractions for min/rebuffer/max from target.
    private const val MIN_PREFETCH_FRACTION = 0.35
    private const val REBUFFER_PREFETCH_FRACTION = 0.55
    private const val MAX_BUFFER_MULTIPLIER = 3.0

    /**
     * Computes a plan from the given inputs.
     *
     * @param speedMbps measured read speed for the resource, or `null` if no speed
     *   test has been run yet — in which case [protocol]'s baseline speed is assumed.
     * @param bitrateKbps file video bitrate in Kbps, or `null` before `onTracksChanged`
     *   — in which case [protocol]'s baseline ratio is assumed.
     * @param cacheBudgetBytes device byte budget from
     *   [com.sza.fastmediasorter.core.util.DeviceCapabilityProbe.currentBudget].
     * @param fileDurationSec total file duration in seconds, or `null` if unknown
     *   (e.g., before metadata loads). When known, caps the target at 80% of remaining.
     * @param currentPositionSec playhead position in seconds. Ignored when
     *   [fileDurationSec] is `null`.
     * @param protocol source protocol (affects safety margin + baselines).
     * @param userMultiplier user's Less/Auto/More override from Settings.
     */
    @Suppress("LongParameterList")
    fun compute(
        speedMbps: Double?,
        bitrateKbps: Int?,
        cacheBudgetBytes: Long,
        fileDurationSec: Long?,
        currentPositionSec: Long,
        protocol: Protocol,
        userMultiplier: PrefetchCacheMultiplier
    ): PrefetchPlan {
        val effectiveSpeed = speedMbps ?: baselineMbps(protocol)
        val effectiveBitrate = bitrateKbps

        // Step 1: ratio. If both speed and bitrate are present, compute from them;
        // otherwise fall back to the protocol baseline ratio.
        val ratio: Double = when {
            effectiveSpeed <= 0.0 -> RATIO_NOT_VIABLE + 0.1
            effectiveBitrate == null -> baselineRatio(protocol)
            else -> effectiveBitrate / (effectiveSpeed * 1000.0)
        }

        // Step 2: viability.
        val viability = classifyViability(ratio)

        // Step 5: device byte budget → max sec at this bitrate.
        // Uses baseline bitrate when unknown so the cap is still meaningful.
        val bitrateForBudget = effectiveBitrate ?: baselineBitrateKbps(protocol)
        val maxBufferSecByDevice = bytesToSeconds(cacheBudgetBytes, bitrateForBudget)

        // Step 6: file duration ceiling. Unknown → MAX_VALUE (no ceiling).
        val maxBufferSecByFile = if (fileDurationSec != null && fileDurationSec > 0) {
            val remaining = (fileDurationSec - currentPositionSec).coerceAtLeast(0L)
            (remaining.toDouble() * 0.8).roundToInt().coerceAtLeast(MIN_TARGET_SEC)
        } else {
            Int.MAX_VALUE
        }

        // Step 3: base target from physics — only meaningful for VIABLE/MARGINAL.
        // For NOT_VIABLE we still compute a safe floor so callers that ignore
        // viability get a defensible number.
        val safetyMargin = protocolSafetyMargin(protocol)
        val divisor = (1.0 - ratio).coerceAtLeast(DIVISOR_FLOOR)
        val physicsTarget = safetyMargin / divisor

        // Step 4: user override.
        val adjustedTarget = when (userMultiplier) {
            PrefetchCacheMultiplier.LESS -> physicsTarget * LESS_FACTOR
            PrefetchCacheMultiplier.AUTO -> physicsTarget
            PrefetchCacheMultiplier.MORE -> physicsTarget + MORE_HEADROOM_SEC
        }

        // Step 7: final clamp.
        val upperBound = minOf(maxBufferSecByDevice, maxBufferSecByFile, MAX_TARGET_SEC)
            .coerceAtLeast(MIN_TARGET_SEC)
        val targetPrefetchSec = adjustedTarget.roundToInt()
            .coerceIn(MIN_TARGET_SEC, upperBound)

        val minPrefetchSec = (targetPrefetchSec * MIN_PREFETCH_FRACTION)
            .roundToInt().coerceAtLeast(1)
        val rebufferPrefetchSec = (targetPrefetchSec * REBUFFER_PREFETCH_FRACTION)
            .roundToInt().coerceAtLeast(2)
        val maxBufferSec = (targetPrefetchSec * MAX_BUFFER_MULTIPLIER).roundToInt()
            .coerceAtMost(maxBufferSecByDevice)
            .coerceAtLeast(targetPrefetchSec)

        val derivation = PrefetchDerivation(
            speedMbps = speedMbps,
            bitrateKbps = bitrateKbps,
            ratio = ratio,
            cacheBudgetBytes = cacheBudgetBytes,
            maxBufferSecByDevice = maxBufferSecByDevice,
            maxBufferSecByFile = maxBufferSecByFile,
            protocol = protocol,
            userMultiplier = userMultiplier,
            formulaVersion = FORMULA_VERSION
        )

        return PrefetchPlan(
            viability = viability,
            targetPrefetchSec = targetPrefetchSec,
            minPrefetchSec = minPrefetchSec,
            rebufferPrefetchSec = rebufferPrefetchSec,
            maxBufferSec = maxBufferSec,
            sourceLabel = protocol.label(),
            derivation = derivation
        )
    }

    /**
     * Classify a bitrate/speed ratio into [StreamViabilityState].
     * Exposed so the progress tracker can reuse the exact same boundaries
     * when escalating mid-session.
     */
    fun classifyViability(ratio: Double): StreamViabilityState = when {
        ratio < RATIO_MARGINAL -> StreamViabilityState.VIABLE
        ratio < RATIO_NOT_VIABLE -> StreamViabilityState.MARGINAL
        else -> StreamViabilityState.NOT_VIABLE
    }

    /**
     * Public helper for UIs that want to display the exact "how long to play 1 s"
     * ratio without re-deriving it.
     */
    fun ratioOf(speedMbps: Double?, bitrateKbps: Int?, protocol: Protocol): Double {
        val speed = speedMbps ?: baselineMbps(protocol)
        if (speed <= 0.0) return RATIO_NOT_VIABLE + 0.1
        return bitrateKbps?.let { it / (speed * 1000.0) } ?: baselineRatio(protocol)
    }

    private fun bytesToSeconds(bytes: Long, bitrateKbps: Int): Int {
        if (bitrateKbps <= 0) return MAX_TARGET_SEC
        // bytes / (kbps * 125) — 125 = 1000/8, converting kilobits to bytes.
        val sec = bytes.toDouble() / (bitrateKbps * 125.0)
        return sec.roundToInt().coerceAtLeast(MIN_TARGET_SEC)
    }

    private fun protocolSafetyMargin(protocol: Protocol): Double = when (protocol) {
        Protocol.LOCAL -> 2.0
        Protocol.SMB -> 4.0
        Protocol.SFTP -> 5.0
        Protocol.FTP -> 6.0
        Protocol.CLOUD -> 7.0
    }

    private fun baselineMbps(protocol: Protocol): Double = when (protocol) {
        Protocol.LOCAL -> 1000.0
        Protocol.SMB -> 50.0
        Protocol.SFTP -> 40.0
        Protocol.FTP -> 30.0
        Protocol.CLOUD -> 25.0
    }

    private fun baselineRatio(protocol: Protocol): Double = when (protocol) {
        Protocol.LOCAL -> 0.05
        Protocol.SMB -> 0.3
        Protocol.SFTP -> 0.35
        Protocol.FTP -> 0.4
        Protocol.CLOUD -> 0.45
    }

    private fun baselineBitrateKbps(protocol: Protocol): Int = when (protocol) {
        Protocol.LOCAL -> 8_000
        Protocol.SMB -> 5_000
        Protocol.SFTP -> 5_000
        Protocol.FTP -> 4_000
        Protocol.CLOUD -> 4_000
    }

    private fun Protocol.label(): String = when (this) {
        Protocol.LOCAL -> "Local"
        Protocol.SMB -> "SMB"
        Protocol.SFTP -> "SFTP"
        Protocol.FTP -> "FTP"
        Protocol.CLOUD -> "Cloud"
    }
}
