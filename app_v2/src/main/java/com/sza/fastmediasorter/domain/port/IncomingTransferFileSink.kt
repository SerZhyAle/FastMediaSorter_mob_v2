package com.sza.fastmediasorter.domain.port

/**
 * S3040: where a media file accepted from another device lands.
 *
 * Declared in the domain layer so the receiving use case never names a filesystem path, and
 * implemented once in the data layer - the destination is a product decision, not a per-caller one.
 */
interface IncomingTransferFileSink {

    /**
     * Write [bytes] under [fileName], returning the readable path on success and `null` on refusal.
     *
     * [fileName] arrives from another device's manifest, so the implementation owns rejecting
     * anything that is not a plain file name.
     */
    suspend fun write(fileName: String, bytes: ByteArray): String?
}
