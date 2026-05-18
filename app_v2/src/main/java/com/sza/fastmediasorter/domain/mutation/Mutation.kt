package com.sza.fastmediasorter.domain.mutation

/**
 * Atomic record of a successful media operation produced by the Player.
 *
 * Each variant carries the resource identifier, an [opId] (UUID assigned by the producer)
 * and a [timestampMs] captured at the moment the source confirmed the operation.
 *
 * The journal is in-memory only - no serialization, no persistence (S0242 §6 Item 4).
 */
sealed class Mutation {

    abstract val opId: String
    abstract val timestampMs: Long

    /** Single-resource deletion. */
    data class Delete(
        val resourceId: Long,
        val canonicalPath: String,
        override val opId: String,
        override val timestampMs: Long
    ) : Mutation()

    /**
     * Move between resources. [srcResourceId] is an alias of [resourceId] kept explicit
     * so the journal can be filtered by either source or destination resource.
     */
    data class Move(
        val resourceId: Long,
        val srcResourceId: Long,
        val dstResourceId: Long,
        val oldCanonicalPath: String,
        val newCanonicalPath: String,
        override val opId: String,
        override val timestampMs: Long
    ) : Mutation()

    /** Same-resource rename - only the file name changes. */
    data class Rename(
        val resourceId: Long,
        val oldCanonicalPath: String,
        val newCanonicalPath: String,
        override val opId: String,
        override val timestampMs: Long
    ) : Mutation()

    /**
     * Batch deletion produced by a single `MediaStore.createDeleteRequest` confirmation -
     * the system returns one result for many files, so the journal records them together.
     */
    data class BatchDelete(
        val resourceId: Long,
        val canonicalPaths: List<String>,
        override val opId: String,
        override val timestampMs: Long
    ) : Mutation()
}
