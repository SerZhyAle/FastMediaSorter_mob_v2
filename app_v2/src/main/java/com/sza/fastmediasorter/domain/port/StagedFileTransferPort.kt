package com.sza.fastmediasorter.domain.port

import android.net.Uri
import com.sza.fastmediasorter.domain.model.transfer.TransferDataKind

/**
 * S1565: the bridge for the two data kinds whose shipped flow reads a file rather than bytes.
 *
 * `ImportFavoritesUseCase` and the resource-share importer both take a `Uri`, and both own a
 * preview the user answers before anything is written. Strategic §5.1 requires the import from a
 * device file and from Google Drive to use the same preview, warning and application rules, so the
 * Drive path must reach those same use cases rather than re-implement them: this port stages the
 * downloaded bytes into a private file and hands back a `Uri` the shipped flow consumes unchanged.
 *
 * Declared in the domain layer and implemented in the UI layer, because the resource importer lives
 * there - the domain declares, the UI implements.
 */
interface StagedFileTransferPort {

    /** Serialize every transferable resource, or `null` when the format could not be produced. */
    suspend fun exportResources(): ByteArray?

    /**
     * Write [bytes] to a private file this app can read back, named for [kind], and return its
     * `Uri`. Returns `null` when the file could not be written.
     */
    suspend fun stage(kind: TransferDataKind, bytes: ByteArray): Uri?

    /** Read the whole of a user-chosen document, or `null` when it could not be read. */
    suspend fun readFrom(source: Uri): ByteArray?

    /** Overwrite a user-chosen document with [bytes]. `false` when the write did not happen. */
    suspend fun writeTo(target: Uri, bytes: ByteArray): Boolean
}
