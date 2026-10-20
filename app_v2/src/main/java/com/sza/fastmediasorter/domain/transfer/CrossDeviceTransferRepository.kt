package com.sza.fastmediasorter.domain.transfer

import com.sza.fastmediasorter.domain.model.transfer.CrossDevicePacketEntry
import com.sza.fastmediasorter.domain.model.transfer.CrossDevicePacketManifest
import com.sza.fastmediasorter.domain.model.transfer.CrossDeviceQueueSnapshot
import com.sza.fastmediasorter.domain.model.transfer.DriveQuota
import com.sza.fastmediasorter.domain.model.transfer.OutgoingPacketFile
import java.io.InputStream

/**
 * S3040: the cross-device packet queue, as the domain sees it.
 *
 * Every call is a whole cloud round trip, so the contract is deliberately coarse: the caller never
 * composes two of these to reach one outcome, which keeps a half-published packet - payload files
 * uploaded, manifest missing - out of the queue the receiver reads.
 *
 * S4231: payloads move as streams, times come from Drive, and nothing here rewrites another device's
 * manifest (contract DEVICE-EXCHANGE items CN and CR).
 */
interface CrossDeviceTransferRepository {

    /**
     * Upload [files] as streams, then the manifest that makes the packet visible; returns the manifest
     * written, its `files` carrying each size and SHA-256.
     *
     * The manifest is written last on purpose: a packet without it is invisible to [listQueue]. A failed
     * upload deletes this packet's own folder before the failure is returned.
     */
    suspend fun publishPacket(
        manifest: CrossDevicePacketManifest,
        files: List<OutgoingPacketFile>,
        onProgress: ((Long) -> Unit)? = null
    ): Result<CrossDevicePacketManifest>

    /** Every committed packet whose manifest this build can read, plus abandoned uploads, on Drive's clock. */
    suspend fun listQueue(): Result<CrossDeviceQueueSnapshot>

    /** Open one payload file of [entry] by its manifest-declared [fileName]; the caller closes it. */
    suspend fun openPayload(entry: CrossDevicePacketEntry, fileName: String): Result<InputStream>

    /** Permanently delete one packet folder - payload and manifest together. */
    suspend fun deletePacket(entry: CrossDevicePacketEntry): Result<Unit>

    /** Delete the packets of [snapshot] past their expiry and its abandoned uploads; returns how many went. */
    suspend fun purgeExpired(snapshot: CrossDeviceQueueSnapshot): Result<Int>

    suspend fun readStorageQuota(): Result<DriveQuota>
}
