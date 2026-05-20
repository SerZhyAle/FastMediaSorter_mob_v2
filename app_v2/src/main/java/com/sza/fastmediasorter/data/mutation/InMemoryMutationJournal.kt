package com.sza.fastmediasorter.data.mutation

import com.sza.fastmediasorter.domain.mutation.Mutation
import com.sza.fastmediasorter.domain.mutation.MutationEntry
import com.sza.fastmediasorter.domain.mutation.MutationJournal
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-scoped in-memory [MutationJournal] (S0242 §6 Item 4 - no persistence).
 *
 * Storage shape: one [ResourceJournal] per resource id, each carrying an append-only
 * list of [MutationEntry] plus a monotonic seq counter. All mutating ops on a single
 * resource are synchronized on that resource's journal instance so `record`,
 * `pendingFor`, and `markApplied` observe a consistent snapshot.
 *
 * Memory cap: [MAX_ENTRIES_PER_RESOURCE] applied entries. When exceeded, the oldest
 * already-applied entries are evicted first; pending entries are never dropped - losing
 * a pending entry would silently desync the Browse list.
 */
@Singleton
class InMemoryMutationJournal @Inject constructor() : MutationJournal {

    private val byResource = ConcurrentHashMap<Long, ResourceJournal>()

    override fun record(mutation: Mutation) {
        val resourceId = resourceIdOf(mutation)
        val journal = byResource.getOrPut(resourceId) { ResourceJournal() }
        synchronized(journal) {
            journal.seqCounter += 1L
            val entry = MutationEntry(seq = journal.seqCounter, mutation = mutation, applied = false)
            journal.entries.add(entry)
            enforceCap(resourceId, journal)
            Timber.d(
                "MutationJournal: record %s seq=%d resource=%d",
                mutation::class.simpleName,
                entry.seq,
                resourceId
            )
        }
    }

    override fun pendingFor(resourceId: Long, sinceAppliedSeq: Long): List<MutationEntry> {
        val journal = byResource[resourceId] ?: return emptyList()
        val snapshot = synchronized(journal) {
            journal.entries.filter { !it.applied && it.seq > sinceAppliedSeq }
                .sortedBy { it.seq }
        }
        Timber.d(
            "MutationJournal: pending %d entries for resource=%d sinceSeq=%d",
            snapshot.size,
            resourceId,
            sinceAppliedSeq
        )
        return snapshot
    }

    override fun markApplied(resourceId: Long, opIds: Collection<String>) {
        if (opIds.isEmpty()) return
        val journal = byResource[resourceId] ?: return
        val opIdSet = opIds.toHashSet()
        synchronized(journal) {
            var maxAppliedSeq = journal.lastAppliedSeq
            for (i in journal.entries.indices) {
                val entry = journal.entries[i]
                if (!entry.applied && entry.mutation.opId in opIdSet) {
                    journal.entries[i] = entry.copy(applied = true)
                    if (entry.seq > maxAppliedSeq) maxAppliedSeq = entry.seq
                }
            }
            journal.lastAppliedSeq = maxAppliedSeq
        }
    }

    override fun lastAppliedSeq(resourceId: Long): Long {
        val journal = byResource[resourceId] ?: return 0L
        return synchronized(journal) { journal.lastAppliedSeq }
    }

    override fun clearResource(resourceId: Long) {
        byResource.remove(resourceId)
    }

    /**
     * Evicts oldest applied entries until size fits the cap. Called under the journal
     * lock. Pending entries are preserved unconditionally - a stuck pending stream means
     * Reconciler never ran, which is a different bug to surface.
     */
    private fun enforceCap(resourceId: Long, journal: ResourceJournal) {
        if (journal.entries.size <= MAX_ENTRIES_PER_RESOURCE) return
        val iter = journal.entries.iterator()
        var evicted = 0
        while (iter.hasNext() && journal.entries.size - evicted > MAX_ENTRIES_PER_RESOURCE) {
            val entry = iter.next()
            if (entry.applied) {
                iter.remove()
                evicted += 1
            }
        }
        if (evicted > 0) {
            Timber.w(
                "MutationJournal: cap hit on resource=%d; evicted %d applied entries (kept %d)",
                resourceId,
                evicted,
                journal.entries.size
            )
        }
    }

    private fun resourceIdOf(mutation: Mutation): Long = when (mutation) {
        is Mutation.Delete -> mutation.resourceId
        is Mutation.Move -> mutation.resourceId
        is Mutation.Rename -> mutation.resourceId
        is Mutation.BatchDelete -> mutation.resourceId
    }

    private class ResourceJournal {
        val entries: MutableList<MutationEntry> = mutableListOf()
        var seqCounter: Long = 0L
        var lastAppliedSeq: Long = 0L
    }

    companion object {
        private const val MAX_ENTRIES_PER_RESOURCE = 512
    }
}
