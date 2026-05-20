package com.sza.fastmediasorter.domain.mutation

/**
 * Append-only per-resource journal of [Mutation] records produced by the Player.
 *
 * Browse reads pending entries on `onResume` (Reconciler - Phase 03) and marks them
 * applied; entries persist in the journal until [clearResource] is called by an
 * explicit pull-to-refresh.
 *
 * Implementations must be thread-safe - the Player produces from a background dispatcher
 * while Browse consumes from the main thread.
 */
interface MutationJournal {

    /** Appends [mutation] under its resource key, assigning a fresh per-resource seq. */
    fun record(mutation: Mutation)

    /**
     * Returns unapplied entries for [resourceId] with seq strictly greater than
     * [sinceAppliedSeq], in ascending seq order.
     */
    fun pendingFor(resourceId: Long, sinceAppliedSeq: Long): List<MutationEntry>

    /**
     * Flips `applied = true` for every entry whose [Mutation.opId] is in [opIds]
     * and advances `lastAppliedSeq` to the maximum seq among the flipped entries.
     */
    fun markApplied(resourceId: Long, opIds: Collection<String>)

    /** Highest seq value already applied for [resourceId]; `0L` if none. */
    fun lastAppliedSeq(resourceId: Long): Long

    /** Drops every entry for [resourceId] and resets seq to 0 (pull-to-refresh contract). */
    fun clearResource(resourceId: Long)
}

/**
 * One position in the per-resource journal. [seq] is monotonically increasing
 * per resource; [applied] is flipped by [MutationJournal.markApplied].
 */
data class MutationEntry(
    val seq: Long,
    val mutation: Mutation,
    val applied: Boolean
)
