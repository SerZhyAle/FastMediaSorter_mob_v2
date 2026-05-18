package com.sza.fastmediasorter.data.transfer.local

import java.io.OutputStream

/**
 * Open, write-once sink to a local destination.
 *
 * **Lifecycle contract** - caller must invoke **exactly one** of [commit] or
 * [abort] after writing finishes (success or failure). The sink closes
 * [outputStream] internally during `commit`/`abort` - the caller does not
 * close it directly.
 *
 * **Atomicity** - external observers (MediaStore, gallery, media player)
 * must not see the destination until [commit] succeeds. For MediaStore-backed
 * sinks this is realized via `IS_PENDING = 1` on insert and `IS_PENDING = 0`
 * on commit. For raw filesystem sinks atomicity is the caller's concern
 * (typically wrapped in [com.sza.fastmediasorter.data.transfer.AtomicFileOperationStrategy]).
 *
 * See strategic spec S0231 §5.1 (writer/sink pair) and ADR-2.
 */
interface LocalSink {

    /** Stream the caller writes bytes into. Closed by the sink. */
    val outputStream: OutputStream

    /**
     * Publish the sink. Closes [outputStream], makes the destination visible
     * to external observers, and returns the final user-visible path/URI.
     */
    suspend fun commit(): Result<String>

    /**
     * Discard the sink. Best-effort cleanup: closes [outputStream] (swallowing
     * exceptions), removes any partial filesystem entry, and deletes any
     * pending MediaStore record.
     */
    suspend fun abort()
}
