package com.sza.fastmediasorter.domain.model

/**
 * One record of the Drive channel of contract DEVICE-EXCHANGE section 8, as read from or written to one
 * `fmsx-*.json` file. [writtenAtMs] and [ttlSeconds] come from the section 8.1 wrapper and are shared by
 * every record type; the body members are those of sections 6.1, 6.2, 6.3 and 8.3.
 */
sealed interface SftpRendezvousRecord {
    val writtenAtMs: Long
    val ttlSeconds: Long
}

/** DEVICE-EXCHANGE 6.1: who a device is and whether it is up right now. */
data class SftpRendezvousDevice(
    val deviceId: String,
    val deviceName: String,
    val product: String,
    val productVersion: String?,
    val platform: String?,
    val roles: List<String>,
    val presence: String,
    val updatedAtMs: Long,
    override val writtenAtMs: Long,
    override val ttlSeconds: Long,
    val receiver: SftpRendezvousReceiver? = null,
) : SftpRendezvousRecord {

    val isOnline: Boolean get() = presence == PRESENCE_ONLINE

    companion object {
        const val PRODUCT_FMS_ANDROID = "fms-android"
        const val PLATFORM_ANDROID = "android"
        const val ROLE_RESOURCE_PRODUCER = "resource-producer"
        const val ROLE_RESOURCE_CONSUMER = "resource-consumer"
        const val ROLE_BROADCASTER = "broadcaster"
        const val ROLE_RECEIVER = "receiver"
        const val PRESENCE_ONLINE = "online"
        const val PRESENCE_OFFLINE = "offline"
    }
}

/**
 * DEVICE-EXCHANGE 6.1 with amendment E: what a receiver plays. When [plays] is present a broadcaster covers
 * an endpoint only if its pair is listed; [modes] and [transports] stay the summary an older reader uses.
 */
data class SftpRendezvousReceiver(
    val modes: List<String>,
    val transports: List<String>,
    val plays: List<SftpRendezvousPlayPair>? = null,
)

/** One `{mode, transport}` pair of [SftpRendezvousReceiver.plays]. */
data class SftpRendezvousPlayPair(val mode: String, val transport: String)

/**
 * DEVICE-EXCHANGE 6.2 for kind `sftp-share`. [descriptor] is an `FMSSFTP1` or `FMSSFTP2` code and carries
 * the share's password by design, so it is never logged; [shareId] is the outsider capability of 5.4 and
 * stays out of logs for the same reason. [root] is `access.root` of amendment O, [publicPort] the
 * third-party port of `ANYWHERE-ACCESS` 5.5, [presence] the producing device's.
 */
data class SftpRendezvousResource(
    val resourceId: String,
    val deviceId: String,
    val kind: String,
    val name: String,
    val descriptor: String,
    val updatedAtMs: Long,
    override val writtenAtMs: Long,
    override val ttlSeconds: Long,
    val shareId: String? = null,
    val publicPort: Int? = null,
    val root: String? = null,
    val presence: String? = null,
) : SftpRendezvousRecord {

    override fun toString(): String =
        "SftpRendezvousResource(resourceId=$resourceId, deviceId=$deviceId, kind=$kind, name=$name)"

    companion object {
        const val KIND_SFTP_SHARE = "sftp-share"
    }
}

/**
 * DEVICE-EXCHANGE 6.3: a broadcast live right now. [descriptorJson] is the `LIVE-BROADCAST` descriptor as
 * one compact JSON object, kept verbatim so a receiver imports exactly what the broadcaster wrote. The
 * broadcast id is the capability of the secret-link model, so neither it nor the descriptor is logged.
 */
data class SftpRendezvousBroadcast(
    val broadcastId: String,
    val deviceId: String,
    val title: String?,
    val mode: String,
    val startedAtMs: Long?,
    val updatedAtMs: Long,
    val descriptorJson: String,
    override val writtenAtMs: Long,
    override val ttlSeconds: Long,
) : SftpRendezvousRecord {

    override fun toString(): String = "SftpRendezvousBroadcast(deviceId=$deviceId, mode=$mode)"
}

/**
 * DEVICE-EXCHANGE 8.3: a request addressed to the device [toDeviceId]. [fromDeviceId] is an additive member
 * of this app: section 8.1 lets a device delete only its own files, and the file name carries no writer.
 */
data class SftpRendezvousRequest(
    val toDeviceId: String,
    val action: String,
    val fromDeviceId: String?,
    override val writtenAtMs: Long,
    override val ttlSeconds: Long,
) : SftpRendezvousRecord {

    companion object {
        /** Re-write the device and resource records now. */
        const val ACTION_ANNOUNCE = "announce"
    }
}

/**
 * Expiry of a Drive record. Amendment 0.12 C of DEVICE-EXCHANGE judges it on the file's Drive
 * `modifiedTime`; [modifiedMs] is that time when the listing carried it, and the wrapper's `writtenAt`
 * stands in only when it did not.
 */
fun SftpRendezvousRecord.isFreshAt(nowMs: Long, modifiedMs: Long = 0L): Boolean {
    val since = if (modifiedMs > 0L) modifiedMs else writtenAtMs
    return nowMs - since <= ttlSeconds * MILLIS_PER_SECOND
}

private const val MILLIS_PER_SECOND = 1_000L
