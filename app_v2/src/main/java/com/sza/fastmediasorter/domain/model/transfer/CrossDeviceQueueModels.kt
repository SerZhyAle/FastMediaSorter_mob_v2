package com.sza.fastmediasorter.domain.model.transfer

import java.io.InputStream

/** S4231: one file to send, opened only when its upload starts so a large file is never held in memory. */
data class OutgoingPacketFile(
    val name: String,
    val size: Long,
    val mimeType: String = CrossDevicePacketFile.DEFAULT_MIME_TYPE,
    val clipKind: ClipKind? = null,
    val open: () -> InputStream
)

/** S4231: who a packet is for; a null target in the API means any own device. */
data class CrossDeviceTarget(val deviceId: String, val displayName: String)

/**
 * S4231: one committed packet as the queue lists it. [createdTimeMs] is the packet folder's Drive
 * `createdTime`, the clock expiry and "latest" are judged on; [folderId] is opaque to the domain.
 */
data class CrossDevicePacketEntry(
    val manifest: CrossDevicePacketManifest,
    val folderId: String,
    val createdTimeMs: Long
) {
    val packetId: String get() = manifest.packetId
}

/** S4231: one listing of the queue and Drive's clock when it was taken. */
data class CrossDeviceQueueSnapshot(
    val entries: List<CrossDevicePacketEntry>,
    val abandonedFolderIds: List<String>,
    val serverNowMs: Long
)

/** S4231: what this device sees in the queue - packets offered to it, and its own sends still waiting. */
data class CrossDeviceQueueView(
    val incoming: List<CrossDevicePacketEntry>,
    val outgoing: List<CrossDevicePacketEntry>
)

/** S4231: `about.storageQuota` of the account; a null [limitBytes] means no limit. */
data class DriveQuota(val limitBytes: Long?, val usageBytes: Long)
