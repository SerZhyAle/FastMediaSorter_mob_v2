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
}
