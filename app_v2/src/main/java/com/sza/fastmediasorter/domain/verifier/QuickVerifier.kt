package com.sza.fastmediasorter.domain.verifier

import com.sza.fastmediasorter.domain.model.ResourceType

/**
 * S0242 Phase 04 - background existence probe contract.
 *
 * Implementations check whether a (small) set of files is still present on the source
 * after the Reconciler has folded all pending Player-side mutations. The probe is the
 * "second line of defence" - it catches deletions that happened outside the app (file
 * manager, desktop client, server rule) which the Player journal cannot see.
 *
 * Contract:
 * - MUST NOT throw. Any exception (network, auth, parse) MUST be swallowed and turned
 *   into an empty result - a transient network failure must never produce a false
 *   `Mutation.Delete` against a real, still-present file.
 * - Returns the subset of [paths] that the source confirms ARE NOT present. Paths the
 *   probe cannot make a confident statement about (errors, timeouts) stay out of the
 *   returned list.
 * - Paths passed in are the raw `MediaFile.path` form expected by the underlying
 *   source-specific client. The caller (QuickVerifierDispatcher → Reconciler) is
 *   responsible for canonicalizing the returned missing paths before journaling them.
 *
 * Threading: the call is `suspend`; concrete implementations switch to `Dispatchers.IO`
 * and wrap network protocols (SMB/SFTP/Cloud) in `ConnectionThrottleManager.withThrottle`
 * to share connection budget with the active scanner / thumbnail pipeline.
 */
interface QuickVerifier {

    /**
     * Probe existence of [paths] on the source identified by [resourceId].
     * @return paths that the source confirms are missing. Empty on any error.
     */
    suspend fun missingFiles(resourceId: Long, paths: List<String>): List<String>
}

/**
 * Multibinding key (reserved for future use). Currently `QuickVerifierDispatcher`
 * holds the four strategies as concrete-type dependencies - see Step 04.4 for the
 * rationale this key is defined but no `Map<QuickVerifierKey, QuickVerifier>` binding
 * is wired up yet.
 */
data class QuickVerifierKey(val type: ResourceType)
