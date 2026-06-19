package com.sza.fastmediasorter.ui.player.helpers

import android.net.Uri
import com.sza.fastmediasorter.core.util.DeviceCapabilityProbe
import com.sza.fastmediasorter.data.network.ConnectionThrottleManager
import com.sza.fastmediasorter.domain.model.PrefetchCacheMultiplier
import com.sza.fastmediasorter.domain.model.PrefetchPlan
import com.sza.fastmediasorter.domain.model.Protocol
import com.sza.fastmediasorter.domain.playback.PrefetchFormula
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin orchestrator that gathers pre-cache inputs (network speed, device budget,
 * user multiplier, protocol) and delegates arithmetic to [PrefetchFormula].
 *
 * Ownership split:
 * - [PrefetchFormula] is a pure function - no side effects, easily tested.
 * - [PrefetchPolicyManager] is a Hilt-injected `@Singleton` that sources the
 *   runtime context: settings flow, speed cache, device probe. It exists so
 *   callers (PlayerViewModel, *PlaybackHelper) do not need to know how to
 *   assemble a [com.sza.fastmediasorter.domain.model.PrefetchDerivation].
 *
 * See spec: PLAN/spec_adaptive-playback-strategy.md §5 and ADR-1.
 */
@Singleton
class PrefetchPolicyManager @Inject constructor(
    private val deviceCapabilityProbe: DeviceCapabilityProbe,
    private val settingsRepository: SettingsRepository
) {

    /**
     * Compose a fresh plan from the given session inputs.
     *
     * @param resourceKey protocol-normalized key used to look up the last speed
     *   measurement. Must match the key used by [ConnectionThrottleManager.setLastSpeedMbps].
     * @param uri media URI - protocol is detected from its scheme.
     * @param bitrateKbps measured bitrate from ExoPlayer's `onTracksChanged`, or
     *   `null` if metadata has not loaded yet.
     * @param fileDurationSec total file duration or `null` when unknown.
     * @param currentPositionSec playhead position in seconds (ignored when duration is null).
     */
    suspend fun computePlan(
        resourceKey: String,
        uri: Uri,
        bitrateKbps: Int?,
        fileDurationSec: Long?,
        currentPositionSec: Long
    ): PrefetchPlan {
        val settings = settingsRepository.getSettings().first()
        val budget = deviceCapabilityProbe.currentBudget()
        val protocol = detectProtocol(uri)
        val speedMbps = ConnectionThrottleManager.getLastSpeedMbps(resourceKey)

        val plan = PrefetchFormula.compute(
            speedMbps = speedMbps,
            bitrateKbps = bitrateKbps,
            cacheBudgetBytes = budget.cacheBudgetBytes,
            fileDurationSec = fileDurationSec,
            currentPositionSec = currentPositionSec,
            protocol = protocol,
            userMultiplier = settings.prefetchCacheMultiplier
        )

        Timber.d(
            "PrefetchPolicy: proto=%s speed=%s bitrate=%s target=%ds min=%ds max=%ds viability=%s",
            protocol, speedMbps?.toString() ?: "n/a", bitrateKbps?.toString() ?: "n/a",
            plan.targetPrefetchSec, plan.minPrefetchSec, plan.maxBufferSec, plan.viability
        )
        return plan
    }

    /**
     * Convenience variant for callers that don't yet know the user multiplier and
     * want a default plan (used before settings have loaded).
     */
    fun computePlanWithDefaults(
        uri: Uri,
        bitrateKbps: Int?,
        fileDurationSec: Long?,
        currentPositionSec: Long,
        speedMbps: Double?
    ): PrefetchPlan {
        val budget = deviceCapabilityProbe.currentBudget()
        val protocol = detectProtocol(uri)
        return PrefetchFormula.compute(
            speedMbps = speedMbps,
            bitrateKbps = bitrateKbps,
            cacheBudgetBytes = budget.cacheBudgetBytes,
            fileDurationSec = fileDurationSec,
            currentPositionSec = currentPositionSec,
            protocol = protocol,
            userMultiplier = PrefetchCacheMultiplier.DEFAULT
        )
    }

    /**
     * Protocol classification from URI scheme. `content://` and `file://` are
     * treated as [Protocol.LOCAL]; unknown schemes default to [Protocol.LOCAL]
     * since those mostly come from MediaStore / DocumentsProvider content URIs.
     */
    fun detectProtocol(uri: Uri): Protocol = when (uri.scheme?.lowercase()) {
        "smb" -> Protocol.SMB
        "sftp", "ssh" -> Protocol.SFTP
        "ftp", "ftps" -> Protocol.FTP
        "cloud" -> Protocol.CLOUD
        "file", "content", null -> Protocol.LOCAL
        else -> Protocol.LOCAL
    }

    companion object {
        /** Generous default transfer budget for network audio when connection speed is unknown. */
        const val DEFAULT_NETWORK_AUDIO_PRECACHE_TIMEOUT_MS = 20_000L
        /** Short, fixed connect bound: an unreachable server fails fast instead of holding the user. */
        const val NETWORK_AUDIO_CONNECT_TIMEOUT_MS = 5_000L
        /** Upper bound on the adaptive transfer budget so a very slow link cannot wait unboundedly. */
        const val MAX_NETWORK_AUDIO_PRECACHE_TIMEOUT_MS = 120_000L
        /** Safety multiplier over the estimated transfer time (rough, intentionally not over-tuned). */
        private const val TRANSFER_SAFETY_FACTOR = 3.0
        /** Bytes/sec per Mbps (1 Mbps = 125000 bytes/sec). */
        private const val BYTES_PER_SEC_PER_MBPS = 125_000.0

        /**
         * Adaptive transfer budget from the last measured connection speed and file size. When the
         * speed or size is unknown, returns the generous default (the server has already answered the
         * connect probe, so a live-but-slow link deserves room). Clamped to a sane floor/ceiling.
         */
        fun transferTimeoutMsFor(speedMbps: Double?, fileSizeBytes: Long): Long {
            if (speedMbps == null || speedMbps <= 0.0 || fileSizeBytes <= 0L) {
                return DEFAULT_NETWORK_AUDIO_PRECACHE_TIMEOUT_MS
            }
            val estSeconds = fileSizeBytes / (speedMbps * BYTES_PER_SEC_PER_MBPS)
            val estMs = (estSeconds * 1000.0 * TRANSFER_SAFETY_FACTOR).toLong()
            return estMs.coerceIn(DEFAULT_NETWORK_AUDIO_PRECACHE_TIMEOUT_MS, MAX_NETWORK_AUDIO_PRECACHE_TIMEOUT_MS)
        }

        fun audioStartupPolicyFor(
            path: String,
            isAudio: Boolean,
            speedMbps: Double? = null,
            fileSizeBytes: Long = 0L
        ): AudioStartupPreCachePolicy {
            val sourceType = AudioPreCacheSourceType.fromPath(path)
            val useEarlyFallback = isAudio && sourceType == AudioPreCacheSourceType.SFTP
            return AudioStartupPreCachePolicy(
                sourceType = sourceType,
                connectTimeoutMs = NETWORK_AUDIO_CONNECT_TIMEOUT_MS,
                transferTimeoutMs = transferTimeoutMsFor(speedMbps, fileSizeBytes),
                reason = if (useEarlyFallback) {
                    "sftp-audio-early-direct-stream"
                } else {
                    "network-audio-default"
                },
                directStreamFallback = useEarlyFallback
            )
        }

        fun nextTrackPrefetchRecovery(path: String): AudioNextTrackPrefetchRecovery {
            val sourceType = AudioPreCacheSourceType.fromPath(path)
            return AudioNextTrackPrefetchRecovery(
                sourceType = sourceType,
                currentTrackUnaffected = true,
                retryOnDemand = true,
                reason = "${sourceType.logLabel}-next-audio-prefetch-degraded"
            )
        }
    }
}

enum class AudioPreCacheSourceType(val logLabel: String) {
    SMB("smb"),
    SFTP("sftp"),
    FTP("ftp"),
    CLOUD("cloud"),
    LOCAL("local"),
    OTHER("other");

    companion object {
        fun fromPath(path: String): AudioPreCacheSourceType = when {
            path.startsWith("smb://", ignoreCase = true) -> SMB
            path.startsWith("sftp://", ignoreCase = true) -> SFTP
            path.startsWith("ftp://", ignoreCase = true) -> FTP
            path.startsWith("cloud://", ignoreCase = true) -> CLOUD
            path.startsWith("file://", ignoreCase = true) -> LOCAL
            else -> OTHER
        }
    }
}

data class AudioStartupPreCachePolicy(
    val sourceType: AudioPreCacheSourceType,
    /** Short bound on establishing the connection - detects an unreachable server fast. */
    val connectTimeoutMs: Long,
    /** Generous/adaptive bound on the body transfer once the server has answered. */
    val transferTimeoutMs: Long,
    val reason: String,
    val directStreamFallback: Boolean
)

data class AudioNextTrackPrefetchRecovery(
    val sourceType: AudioPreCacheSourceType,
    val currentTrackUnaffected: Boolean,
    val retryOnDemand: Boolean,
    val reason: String
)
