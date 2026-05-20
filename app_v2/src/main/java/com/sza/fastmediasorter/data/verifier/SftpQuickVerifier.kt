package com.sza.fastmediasorter.data.verifier

import com.sza.fastmediasorter.data.network.ConnectionThrottleManager
import com.sza.fastmediasorter.data.transfer.strategy.SftpOperationStrategy
import com.sza.fastmediasorter.domain.verifier.QuickVerifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S0242 Phase 04 - SFTP existence probe.
 *
 * Delegates to [SftpOperationStrategy.exists] which already wraps the upstream
 * `SftpClient.exists` (returns `Result.success(false)` for `SSH_FX_NO_SUCH_FILE`,
 * propagates everything else). All credential resolution + path parsing is reused
 * from the strategy.
 *
 * Throttled with `ProtocolLimits.SFTP` (max 3 concurrent). `resourceKey` is the
 * `sftp://host:port` form used by `SftpMediaScanner` so probes share the connection
 * pool with scanner / thumbnail loaders on the same host.
 *
 * Error policy: same as [SmbQuickVerifier] - only a confirmed `Result.success(false)`
 * adds the path to the missing list; anything else is silently dropped.
 */
@Singleton
class SftpQuickVerifier @Inject constructor(
    private val sftpStrategy: SftpOperationStrategy
) : QuickVerifier {

    override suspend fun missingFiles(resourceId: Long, paths: List<String>): List<String> =
        withContext(Dispatchers.IO) {
            if (paths.isEmpty()) return@withContext emptyList()
            val resourceKey = buildResourceKey(resourceId, paths)
            try {
                ConnectionThrottleManager.withThrottle(
                    protocol = ConnectionThrottleManager.ProtocolLimits.SFTP,
                    resourceKey = resourceKey,
                    highPriority = false
                ) {
                    val missing = ArrayList<String>()
                    for (path in paths) {
                        val result = runCatching { sftpStrategy.exists(path) }.getOrNull()
                        if (result != null && result.isSuccess && result.getOrNull() == false) {
                            missing.add(path)
                        }
                    }
                    missing
                }
            } catch (e: Exception) {
                Timber.w(
                    e,
                    "QuickVerifier(SFTP): probe error for resource=%d, returning no-op",
                    resourceId
                )
                emptyList()
            }
        }

    /**
     * Throttle scope key. Format `sftp://host:port` - same as `SftpMediaScanner` /
     * `SftpClient` use, so the probe shares the per-host throttle semaphore.
     */
    private fun buildResourceKey(resourceId: Long, paths: List<String>): String {
        val sample = paths.firstOrNull { it.startsWith("sftp:", ignoreCase = true) }
            ?: return "verify://sftp/$resourceId"
        return try {
            val withoutProtocol = sample.substringAfter("sftp:", "").trimStart('/')
            val userHostPart = withoutProtocol.substringBefore("/")
            val hostPort = if (userHostPart.contains("@")) {
                userHostPart.substringAfter("@")
            } else {
                userHostPart
            }
            val host = if (hostPort.contains(":")) hostPort.substringBefore(":") else hostPort
            val port = if (hostPort.contains(":")) hostPort.substringAfter(":").toIntOrNull() ?: 22 else 22
            if (host.isEmpty()) "verify://sftp/$resourceId" else "sftp://$host:$port"
        } catch (e: Exception) {
            "verify://sftp/$resourceId"
        }
    }
}
