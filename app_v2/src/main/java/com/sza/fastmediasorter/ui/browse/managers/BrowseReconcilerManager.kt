package com.sza.fastmediasorter.ui.browse.managers

import com.sza.fastmediasorter.core.cache.MediaFilesCacheManager
import com.sza.fastmediasorter.core.di.ApplicationScope
import com.sza.fastmediasorter.data.verifier.QuickVerifierDispatcher
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.mutation.Mutation
import com.sza.fastmediasorter.domain.mutation.MutationEntry
import com.sza.fastmediasorter.domain.mutation.MutationJournal
import com.sza.fastmediasorter.domain.path.PathNormalizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S0242 Phase 03 - sole consumer of the [MutationJournal] on the Browse side.
 *
 * Reads pending entries on every Browse `onResume`, folds them into the visible list and
 * the shared [MediaFilesCacheManager], then marks the applied opIds in the journal so the
 * next resume only sees fresh records.
 *
 * Design notes:
 * - `MediaFilesCacheManager` is a Kotlin `object` (singleton, statically accessed) - it is
 *   intentionally NOT a constructor dependency.
 * - The visible list carries raw paths (`MediaFile.path`); the journal carries canonical
 *   paths produced by [PathNormalizer]. Matching is done in canonical space - each visible
 *   file is canonicalized on the fly inside the fold loop.
 * - `BrowseFileListMutationManager` is intentionally NOT a dependency: it is per-ViewModel
 *   (not a singleton) and mixes selection-manager notifications into its public API, which
 *   the Reconciler must not trigger. Reconciler returns the new list; the caller writes it
 *   back into ViewModel state.
 * - `ResourceType` is passed in by the caller (not stored on `MediaFile`) so the normalizer
 *   can dispatch to the correct per-protocol rule.
 *
 * Thread-safety: safe to call from the main thread. All journal operations are
 * synchronized internally; the cache is an [android.util.LruCache] which is thread-safe.
 */
@Singleton
class BrowseReconcilerManager @Inject constructor(
    private val journal: MutationJournal,
    private val pathNormalizer: PathNormalizer,
    private val quickVerifierDispatcher: QuickVerifierDispatcher,
    @ApplicationScope private val applicationScope: CoroutineScope
) {

    /**
     * Apply all pending mutations for [resourceId] to [currentVisible] and the RAM cache.
     * Returns a new list + a flag indicating whether the visible set changed (caller must
     * rebind the adapter only when [ReconcileResult.visibleChanged] is `true`).
     *
     * `resourceType` is required to canonicalize the visible-file paths so they can be
     * compared against journal entries (which are stored in canonical form).
     */
    fun reconcile(
        resourceId: Long,
        resourceType: ResourceType,
        currentVisible: List<MediaFile>
    ): ReconcileResult {
        Timber.d("S0242: Reconciler entry resource=%d type=%s visible=%d", resourceId, resourceType, currentVisible.size)
        val since = journal.lastAppliedSeq(resourceId)
        val pending = journal.pendingFor(resourceId, since)
        if (pending.isEmpty()) {
            // Strategic §2 п.6 - no rebind, no flicker when nothing changed.
            return ReconcileResult(currentVisible, applied = 0, visibleChanged = false)
        }

        // Pre-canonicalize visible files once per reconcile call; canonicalization is pure
        // (no IO) but non-trivial for local paths (File.canonicalPath resolves symlinks).
        val canonicalPaths = ArrayList<String>(currentVisible.size).apply {
            for (file in currentVisible) {
                add(pathNormalizer.canonical(file.path, resourceType))
            }
        }

        val updatedList = currentVisible.toMutableList()
        val updatedCanonical = canonicalPaths.toMutableList()
        var cacheMisses = 0
        val appliedOpIds = ArrayList<String>(pending.size)

        for (entry in pending) {
            val miss = applyMutation(
                entry = entry,
                resourceId = resourceId,
                resourceType = resourceType,
                visibleList = updatedList,
                visibleCanonical = updatedCanonical
            )
            cacheMisses += miss
            appliedOpIds += entry.mutation.opId
        }

        if (cacheMisses > 0) {
            // Soft inconsistency - Reconciler-driven cache state has drifted from the journal
            // claim. Strategic §2 п.6: log once per reconcile, don't fail.
            Timber.w(
                "Reconciler: resource=%d cache miss count=%d (journal claimed paths not in cache)",
                resourceId,
                cacheMisses
            )
        }

        journal.markApplied(resourceId, appliedOpIds)
        val visibleChanged = updatedList.size != currentVisible.size ||
            updatedList.zip(currentVisible).any { (a, b) -> a !== b }

        Timber.d(
            "Reconciler: resource=%d pending=%d applied=%d visibleChanged=%s",
            resourceId,
            pending.size,
            appliedOpIds.size,
            visibleChanged
        )

        return ReconcileResult(
            updatedList = if (visibleChanged) updatedList.toList() else currentVisible,
            applied = appliedOpIds.size,
            visibleChanged = visibleChanged
        )
    }

    /**
     * Drop pending journal state for [resourceId]. Called from pull-to-refresh, which
     * issues a full rescan - the rescan returns ground truth so any pending entry would
     * re-apply stale state to fresh data.
     */
    fun clearForResource(resourceId: Long) {
        Timber.d("Reconciler: clearForResource(%d)", resourceId)
        journal.clearResource(resourceId)
    }

    /**
     * S0242 Phase 04 - fire-and-forget background existence probe for the first N=10
     * visible files. Findings are routed back through the [MutationJournal] as
     * `Mutation.Delete` entries so the next `reconcile()` call applies them the same
     * way it applies Player-originated deletes - no special UI path.
     *
     * Why no immediate UI update: writing to the journal and letting the next `onResume`
     * pick it up keeps the Reconciler as the single owner of "list state changes" and
     * avoids racing the adapter rebind against the probe. The visible list stays stable
     * for this resume; a stale row only disappears on the next return to the list.
     *
     * Why fire-and-forget on `applicationScope`: the probe is best-effort and must
     * survive Activity destruction (rotation, brief background) - cancelling it on
     * `BrowseActivity` lifecycle would defeat its purpose. The `withThrottle` budget
     * caps concurrent probes, so leaking work into Application scope is bounded.
     *
     * Paths returned by the dispatcher are RAW (the form expected by each protocol's
     * client). They are canonicalized here before journaling because `Mutation.Delete`
     * is matched against canonical paths inside [reconcile].
     */
    fun scheduleQuickVerify(
        resourceId: Long,
        resourceType: ResourceType,
        visible: List<MediaFile>
    ) {
        if (visible.isEmpty()) return
        Timber.d("S0242: QuickVerifier scheduled resource=%d type=%s visible=%d", resourceId, resourceType, visible.size)
        applicationScope.launch {
            val missing = quickVerifierDispatcher.probe(resourceId, visible, n = 10)
            if (missing.isEmpty()) return@launch
            val now = System.currentTimeMillis()
            for (rawPath in missing) {
                val canonical = pathNormalizer.canonical(rawPath, resourceType)
                journal.record(
                    Mutation.Delete(
                        resourceId = resourceId,
                        canonicalPath = canonical,
                        opId = UUID.randomUUID().toString(),
                        timestampMs = now
                    )
                )
            }
            Timber.d(
                "Reconciler: scheduleQuickVerify resource=%d journaled %d missing paths",
                resourceId,
                missing.size
            )
        }
    }

    /**
     * Returns the number of cache misses produced while applying [entry] (0 or 1 per
     * single-file mutation; up to N for BatchDelete). Mutates both [visibleList] and
     * [visibleCanonical] in lockstep so on-the-fly canonical comparison stays cheap.
     */
    private fun applyMutation(
        entry: MutationEntry,
        resourceId: Long,
        resourceType: ResourceType,
        visibleList: MutableList<MediaFile>,
        visibleCanonical: MutableList<String>
    ): Int {
        return when (val mutation = entry.mutation) {
            is Mutation.Delete -> {
                removeByCanonical(visibleList, visibleCanonical, mutation.canonicalPath)
                if (!MediaFilesCacheManager.removeFile(resourceId, mutation.canonicalPath)) 1 else 0
            }
            is Mutation.BatchDelete -> {
                val pathsSet = mutation.canonicalPaths.toHashSet()
                removeManyByCanonical(visibleList, visibleCanonical, pathsSet)
                var miss = 0
                for (path in mutation.canonicalPaths) {
                    if (!MediaFilesCacheManager.removeFile(resourceId, path)) miss += 1
                }
                miss
            }
            is Mutation.Move -> {
                // Same-resource move (cross-folder rename inside the resource) → treat as
                // rename. Cross-resource move → drop source row from this resource view.
                if (mutation.srcResourceId == resourceId && mutation.dstResourceId == resourceId) {
                    renameInPlace(
                        visibleList = visibleList,
                        visibleCanonical = visibleCanonical,
                        oldCanonical = mutation.oldCanonicalPath,
                        newRawPath = mutation.newCanonicalPath,
                        resourceType = resourceType
                    )
                    // Cache: best-effort rename (replace stored entry).
                    val list = MediaFilesCacheManager.getCachedList(resourceId)
                    val cached = list?.firstOrNull { it.path == mutation.oldCanonicalPath }
                    if (cached != null) {
                        MediaFilesCacheManager.updateFile(
                            resourceId,
                            mutation.oldCanonicalPath,
                            cached.copy(path = mutation.newCanonicalPath, name = java.io.File(mutation.newCanonicalPath).name)
                        )
                        0
                    } else {
                        1
                    }
                } else if (mutation.srcResourceId == resourceId) {
                    removeByCanonical(visibleList, visibleCanonical, mutation.oldCanonicalPath)
                    if (!MediaFilesCacheManager.removeFile(resourceId, mutation.oldCanonicalPath)) 1 else 0
                } else {
                    // dstResourceId == resourceId path: moves INTO this resource are not
                    // surfaced as `Add` entries (Phase 04 - Quick Verifier covers the gap).
                    0
                }
            }
            is Mutation.Rename -> {
                renameInPlace(
                    visibleList = visibleList,
                    visibleCanonical = visibleCanonical,
                    oldCanonical = mutation.oldCanonicalPath,
                    newRawPath = mutation.newCanonicalPath,
                    resourceType = resourceType
                )
                val list = MediaFilesCacheManager.getCachedList(resourceId)
                val cached = list?.firstOrNull { it.path == mutation.oldCanonicalPath }
                if (cached != null) {
                    MediaFilesCacheManager.updateFile(
                        resourceId,
                        mutation.oldCanonicalPath,
                        cached.copy(path = mutation.newCanonicalPath, name = java.io.File(mutation.newCanonicalPath).name)
                    )
                    0
                } else {
                    1
                }
            }
        }
    }

    private fun removeByCanonical(
        visibleList: MutableList<MediaFile>,
        visibleCanonical: MutableList<String>,
        canonicalToRemove: String
    ) {
        val idx = visibleCanonical.indexOf(canonicalToRemove)
        if (idx >= 0) {
            visibleList.removeAt(idx)
            visibleCanonical.removeAt(idx)
        }
    }

    private fun removeManyByCanonical(
        visibleList: MutableList<MediaFile>,
        visibleCanonical: MutableList<String>,
        canonicalsToRemove: Set<String>
    ) {
        var i = 0
        while (i < visibleCanonical.size) {
            if (visibleCanonical[i] in canonicalsToRemove) {
                visibleList.removeAt(i)
                visibleCanonical.removeAt(i)
            } else {
                i += 1
            }
        }
    }

    private fun renameInPlace(
        visibleList: MutableList<MediaFile>,
        visibleCanonical: MutableList<String>,
        oldCanonical: String,
        newRawPath: String,
        resourceType: ResourceType
    ) {
        val idx = visibleCanonical.indexOf(oldCanonical)
        if (idx < 0) return
        val newCanonical = pathNormalizer.canonical(newRawPath, resourceType)
        val old = visibleList[idx]
        val newName = java.io.File(newRawPath).name
        visibleList[idx] = old.copy(path = newRawPath, name = newName)
        visibleCanonical[idx] = newCanonical
    }
}

/**
 * Result of one [BrowseReconcilerManager.reconcile] call.
 *
 * [updatedList] is the same reference as the input when nothing changed - callers can use
 * referential equality as a cheap skip-rebind signal in addition to [visibleChanged].
 */
data class ReconcileResult(
    val updatedList: List<MediaFile>,
    val applied: Int,
    val visibleChanged: Boolean
)
