package com.sza.fastmediasorter.domain.port

import java.io.InputStream

/** S4231: what one received file came to - placed under [path], or refused with nothing left behind. */
sealed interface IncomingFileResult {
    data class Placed(val path: String) : IncomingFileResult

    data class Rejected(val reason: String) : IncomingFileResult
}

/**
 * S3040: where a media file accepted from another device lands.
 *
 * Declared in the domain layer so the receiving use case never names a filesystem path, and
 * implemented once in the data layer - the destination is a product decision, not a per-caller one.
 *
 * S4231: a file arrives as a stream and is placed only after every byte matched its manifest - contract
 * DEVICE-EXCHANGE item CN never lets a partial or altered file appear, and never overwrites one.
 */
interface IncomingTransferFileSink {

    /**
     * Copy [source] into the incoming folder under [fileName], checking [expectedSize] and [expectedSha256]
     * when known. [fileName] arrives from another device's manifest, so the implementation owns refusing
     * anything that is not a plain file name.
     */
    suspend fun receive(
        fileName: String,
        expectedSize: Long?,
        expectedSha256: String?,
        source: InputStream
    ): IncomingFileResult

    /** Remove a file this sink placed, when a later file of the same packet failed its check. */
    suspend fun discard(path: String)
}
