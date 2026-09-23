package com.sza.fastmediasorter.domain.mutation

import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.path.PathNormalizer
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S3376: builds and files a [Mutation] for a caller that has just changed user content.
 *
 * Why this exists rather than each caller talking to [MutationJournal] directly: a journal entry needs a
 * resource id, a canonical path, a fresh op id and a timestamp, and three of those four are boilerplate
 * every producer would otherwise repeat - including the [PathNormalizer] call, which has to agree across
 * producers or the reconciler compares two spellings of the same file and matches neither.
 *
 * Why it is not in the transfer strategy layer: the journal is keyed by a resource, and that layer
 * addresses a path and credentials and has no resource concept at all. Registration therefore belongs to
 * the caller that owns the resource, which is what strategic ADR-1 of S3376 decided.
 *
 * A record is filed only where the change has already happened on disk. An entry for an operation that
 * failed, was skipped or was refused would tell Browse to drop a row whose file is still there.
 */
@Singleton
class MutationRecorder @Inject constructor(
    private val journal: MutationJournal,
    private val pathNormalizer: PathNormalizer
) {

    /** Files a [Mutation.Delete] for a file removed from [resourceId]. */
    fun recordDelete(resourceId: Long, rawPath: String, resourceType: ResourceType) {
        journal.record(
            Mutation.Delete(
                resourceId = resourceId,
                canonicalPath = pathNormalizer.canonical(rawPath, resourceType),
                opId = UUID.randomUUID().toString(),
                timestampMs = System.currentTimeMillis()
            )
        )
    }

    /**
     * Files a [Mutation.Move] for a file moved out of [srcResourceId] into [dstResourceId].
     *
     * The two ids may be equal - a move within one resource is still a move, and the journal keeps
     * `resourceId` aliased to the source so the entry is found when Browse reconciles the list the file
     * left.
     */
    fun recordMove(
        srcResourceId: Long,
        srcResourceType: ResourceType,
        srcRawPath: String,
        dstResourceId: Long,
        dstResourceType: ResourceType,
        dstRawPath: String
    ) {
        journal.record(
            Mutation.Move(
                resourceId = srcResourceId,
                srcResourceId = srcResourceId,
                dstResourceId = dstResourceId,
                oldCanonicalPath = pathNormalizer.canonical(srcRawPath, srcResourceType),
                newCanonicalPath = pathNormalizer.canonical(dstRawPath, dstResourceType),
                opId = UUID.randomUUID().toString(),
                timestampMs = System.currentTimeMillis()
            )
        )
    }
}
