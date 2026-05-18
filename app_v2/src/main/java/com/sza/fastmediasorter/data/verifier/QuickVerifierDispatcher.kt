package com.sza.fastmediasorter.data.verifier

import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.verifier.QuickVerifier
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S0242 Phase 04 - fan-out façade over the per-`ResourceType` [QuickVerifier] strategies.
 *
 * Responsibilities:
 * 1. Resolve `resourceId` → [com.sza.fastmediasorter.domain.model.MediaResource] to read
 *    `type`. The dispatcher does not store the type because Reconciler / Browse already
 *    has the resource entity loaded - but persisting it here would require duplicating
 *    state across two managers, so a one-shot lookup is the cleaner trade-off.
 * 2. Select the matching strategy. `ResourceType.FTP` is intentionally a no-op per
 *    strategic §6 Item 3 resolution - Player journal + pull-to-refresh cover FTP.
 * 3. Take the first [n] visible files and ask the strategy which ones the source
 *    confirms are missing.
 *
 * Path form: `MediaFile.path` is the raw form the source-specific client expects
 * (`smb://server/share/...`, `sftp://host/...`, `cloud://provider/folderId/fileId`,
 * absolute filesystem path for LOCAL). The dispatcher does not canonicalize - that's
 * the caller's job (the Reconciler converts the returned missing paths into
 * canonical form before journaling them as `Mutation.Delete`).
 *
 * Note on `ResourceType` enum: the project enum is flat - `LOCAL / SMB / SFTP / FTP /
 * CLOUD`. Cloud providers (Drive / Dropbox / OneDrive) are distinguished via
 * `MediaResource.cloudProvider`, handled inside [CloudQuickVerifier] /
 * `CloudOperationStrategy`. A single `ResourceType.CLOUD` branch covers all three.
 */
@Singleton
class QuickVerifierDispatcher @Inject constructor(
    private val local: LocalQuickVerifier,
    private val smb: SmbQuickVerifier,
    private val sftp: SftpQuickVerifier,
    private val cloud: CloudQuickVerifier,
    private val resourceRepository: ResourceRepository
) {

    /**
     * Probe the first [n] of [candidates] for existence on the source.
     *
     * @return paths (in the same raw form as `MediaFile.path`) that the source confirms
     *         are missing. Empty when the resource is unknown, the type has no
     *         verifier (e.g. FTP), or the probe hit an error.
     */
    suspend fun probe(
        resourceId: Long,
        candidates: List<MediaFile>,
        n: Int = 10
    ): List<String> {
        if (candidates.isEmpty() || n <= 0) return emptyList()
        val resource = resourceRepository.getResourceById(resourceId)
        if (resource == null) {
            Timber.d("QuickVerifierDispatcher: resource=%d not found, skipping probe", resourceId)
            return emptyList()
        }
        val verifier: QuickVerifier = when (resource.type) {
            ResourceType.LOCAL -> local
            ResourceType.SMB -> smb
            ResourceType.SFTP -> sftp
            ResourceType.FTP -> {
                // S0242 §6 Item 3: FTP intentionally excluded from background probe -
                // legacy protocol semantics + low usage make the cost/benefit negative.
                // Player journal + pull-to-refresh cover FTP resources.
                Timber.d("QuickVerifierDispatcher: resource=%d type=FTP - skipped per §6 Item 3", resourceId)
                return emptyList()
            }
            ResourceType.CLOUD -> cloud
        }

        val firstN = candidates.take(n).map { it.path }
        val missing = verifier.missingFiles(resourceId, firstN)
        Timber.d(
            "QuickVerifierDispatcher: resource=%d type=%s probed=%d missing=%d",
            resourceId,
            resource.type,
            firstN.size,
            missing.size
        )
        return missing
    }
}
