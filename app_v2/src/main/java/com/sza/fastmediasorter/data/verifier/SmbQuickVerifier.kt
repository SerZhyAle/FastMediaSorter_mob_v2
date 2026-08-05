package com.sza.fastmediasorter.data.verifier

import com.sza.fastmediasorter.core.util.rethrowIfCancellation
import com.sza.fastmediasorter.data.network.ConnectionThrottleManager
import com.sza.fastmediasorter.data.transfer.strategy.SmbOperationStrategy
import com.sza.fastmediasorter.domain.verifier.QuickVerifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S0242 Phase 04 - SMB existence probe.
 *
 * Delegates to [SmbOperationStrategy.exists] which already handles:
 * - `SmbPathUtils.parseSmbPath` (server/share/remotePath split),
 * - credential resolution via `NetworkCredentialsRepository`,
 * - `SmbClient.exists` (returns `SmbResult.Success(false)` for missing files,
 *   `SmbResult.Error` for transient failures).
 *
 * Reusing the strategy avoids duplicating ~100 LOC of parsing + credential lookup
 * inside this file and keeps the SMB-specific behaviour in a single owner.
 *
 * Probe budget: each path call is wrapped in `ConnectionThrottleManager.withThrottle`
 * with `ProtocolLimits.SMB` (max 2 concurrent). All probes for one resource share the
 * same `resourceKey` so they queue against the SMB scanner / thumbnail loaders for
 * the same share. `highPriority = false` so the player keeps priority.
 *
 * Error policy: any failure (parse error, `Result.failure`, exception) is silently
 * dropped - the file stays out of the "missing" set. False-negative on Quick Verifier
 * is harmless (next pull-to-refresh catches it); false-positive deletes data the user
 * still has.
 */
@Singleton
class SmbQuickVerifier @Inject constructor(
    private val smbStrategy: SmbOperationStrategy
) : QuickVerifier {

    override suspend fun missingFiles(resourceId: Long, paths: List<String>): List<String> =
        withContext(Dispatchers.IO) {
            if (paths.isEmpty()) return@withContext emptyList()
            val resourceKey = buildResourceKey(resourceId, paths)
            try {
                ConnectionThrottleManager.withThrottle(
                    protocol = ConnectionThrottleManager.ProtocolLimits.SMB,
                    resourceKey = resourceKey,
                    highPriority = false
                ) {
                    val missing = ArrayList<String>()
                    for (path in paths) {
                        val result = runCatching { smbStrategy.exists(path) }.getOrNull()
                        // Only treat a CONFIRMED non-existence as "missing". `Result.failure`,
                        // null, or any exception leaves the path in the visible set.
                        if (result != null && result.isSuccess && result.getOrNull() == false) {
                            missing.add(path)
                        }
                    }
                    missing
                }
            } catch (e: Exception) {
                e.rethrowIfCancellation()
                Timber.w(
                    e,
                    "QuickVerifier(SMB): probe error for resource=%d, returning no-op",
                    resourceId
                )
                emptyList()
            }
        }

    /**
     * Throttle scope key. Prefer the share root from the first SMB path (so we share the
     * semaphore with `SmbMediaScanner` / `SmbClient` calls against the same share);
     * fall back to a resource-scoped key when the first path is not parseable.
     */
    private fun buildResourceKey(resourceId: Long, paths: List<String>): String {
        val sample = paths.firstOrNull { it.startsWith("smb:", ignoreCase = true) }
        if (sample != null) {
            val info = com.sza.fastmediasorter.utils.SmbPathUtils.parseSmbPath(sample)
            if (info != null) {
                return "smb://${info.connectionInfo.server}:${info.connectionInfo.port}/${info.connectionInfo.shareName}"
            }
        }
        return "verify://smb/$resourceId"
    }
}
