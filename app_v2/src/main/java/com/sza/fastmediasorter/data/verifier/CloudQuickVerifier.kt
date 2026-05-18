package com.sza.fastmediasorter.data.verifier

import com.sza.fastmediasorter.data.network.ConnectionThrottleManager
import com.sza.fastmediasorter.data.transfer.strategy.CloudOperationStrategy
import com.sza.fastmediasorter.domain.verifier.QuickVerifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S0242 Phase 04 - cloud existence probe (Google Drive / Dropbox / OneDrive).
 *
 * Delegates to [CloudOperationStrategy.exists] which already encapsulates:
 * - `CloudPathParser.parseCloudPath` (cloud://provider/folderId/fileId → provider + ids),
 * - per-provider client dispatch (`googleDriveClient` / `dropboxClient` / `oneDriveRestClient`),
 * - the `fileExists(name, parentId)` cheap probe with a `getFileMetadata(fileId)` fallback
 *   when the name cannot be inferred from the path.
 *
 * Why delegate rather than reach for `CloudStorageClient.fileExists` directly: the
 * cloud surface area (auth state, provider dispatch, path parsing, name splitting) is
 * already non-trivial inside `CloudOperationStrategy` and the per-provider quirks
 * (Dropbox `/path` vs Drive `folderId/filename`) live there. Reimplementing it in the
 * verifier would double the maintenance cost.
 *
 * Throttled with `ProtocolLimits.CLOUD` (max 8 concurrent - the upstream SDKs handle
 * batching well). `resourceKey` is `cloud://<provider>` derived from the first path so
 * the probe shares the per-provider semaphore with `CloudMediaScanner` calls.
 *
 * Error policy: identical to the SMB/SFTP verifiers - only confirmed
 * `Result.success(false)` adds to the missing list.
 */
@Singleton
class CloudQuickVerifier @Inject constructor(
    private val cloudStrategy: CloudOperationStrategy
) : QuickVerifier {

    override suspend fun missingFiles(resourceId: Long, paths: List<String>): List<String> =
        withContext(Dispatchers.IO) {
            if (paths.isEmpty()) return@withContext emptyList()
            val resourceKey = buildResourceKey(resourceId, paths)
            try {
                ConnectionThrottleManager.withThrottle(
                    protocol = ConnectionThrottleManager.ProtocolLimits.CLOUD,
                    resourceKey = resourceKey,
                    highPriority = false
                ) {
                    val missing = ArrayList<String>()
                    for (path in paths) {
                        val result = runCatching { cloudStrategy.exists(path) }.getOrNull()
                        if (result != null && result.isSuccess && result.getOrNull() == false) {
                            missing.add(path)
                        }
                    }
                    missing
                }
            } catch (e: Exception) {
                Timber.w(
                    e,
                    "QuickVerifier(CLOUD): probe error for resource=%d, returning no-op",
                    resourceId
                )
                emptyList()
            }
        }

    /**
     * Throttle scope key. Derived from the first `cloud://provider/...` path so the
     * probe shares the per-provider semaphore with `CloudMediaScanner`.
     */
    private fun buildResourceKey(resourceId: Long, paths: List<String>): String {
        val sample = paths.firstOrNull { it.startsWith("cloud:", ignoreCase = true) }
            ?: return "verify://cloud/$resourceId"
        return try {
            val withoutProtocol = sample
                .replaceFirst("cloud:/", "cloud://") // tolerate legacy single-slash
                .removePrefix("cloud://")
            val provider = withoutProtocol.substringBefore("/").lowercase()
            if (provider.isEmpty()) "verify://cloud/$resourceId" else "cloud://$provider"
        } catch (e: Exception) {
            "verify://cloud/$resourceId"
        }
    }
}
