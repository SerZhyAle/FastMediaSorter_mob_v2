package com.sza.fastmediasorter.domain.transfer

import com.sza.fastmediasorter.domain.model.transfer.CrossDevicePacketManifest

/**
 * S3040: the cross-device packet queue, as the domain sees it.
 *
 * Every call is a whole cloud round trip, so the contract is deliberately coarse: the caller never
 * composes two of these to reach one outcome, which keeps a half-published packet - payload files
 * uploaded, manifest missing - out of the queue the receiver reads.
 */
interface CrossDeviceTransferRepository {

    /**
     * Upload [payloadBytes] keyed by file name, then the manifest that makes the packet visible.
     *
     * The manifest is written last on purpose: a packet without it is invisible to
     * [listPendingPackets] and is reclaimed by [purgeExpiredPackets] rather than half-delivered.
     */
    suspend fun publishPacket(
        manifest: CrossDevicePacketManifest,
        payloadBytes: Map<String, ByteArray>
    ): Result<Unit>

    /** Every packet in the queue that is still `PENDING` and not past its TTL. */
    suspend fun listPendingPackets(): Result<List<CrossDevicePacketManifest>>

    /** Download one payload file of [packetId] by its manifest-declared [fileName]. */
    suspend fun fetchPayload(packetId: String, fileName: String): Result<ByteArray>

    /**
     * Mark [packetId] taken: delete it from the cloud when [deleteFromCloud], otherwise rewrite its
     * manifest as `CLAIMED` so another device does not offer it again.
     */
    suspend fun claimPacket(packetId: String, deleteFromCloud: Boolean): Result<Unit>

    /** Delete every packet older than [maxAgeMs]; returns how many were removed. */
    suspend fun purgeExpiredPackets(maxAgeMs: Long): Result<Int>
}
