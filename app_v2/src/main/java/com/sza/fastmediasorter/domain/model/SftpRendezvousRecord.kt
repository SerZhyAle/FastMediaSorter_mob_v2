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
    /** 26 item DC: why the device went offline; written only with [PRESENCE_OFFLINE], null when unknown. */
    val offlineReason: String? = null,
    /** 17 item AG and 27 item DF: what the device declared at `enroll`. */
    val capabilities: List<String> = emptyList(),
    /** 27 item DF: `kem` of the device's published `e2eKey`, null when it has none or one of an unknown version. */
    val e2eKem: String? = null,
) : SftpRendezvousRecord {

    val isOnline: Boolean get() = presence == PRESENCE_ONLINE

    /** Holds the capability `e2e-v1` and a key, the two things a recipient needs besides trust (item DP). */
    val canReceiveSealed: Boolean get() = CAPABILITY_E2E_V1 in capabilities && e2eKem != null

    /** Android paused this device's sharing; the consumer shows `share-paused` (26 item DD) instead of offline. */
    val isSharePaused: Boolean get() = !isOnline && offlineReason == OFFLINE_REASON_TIME_LIMIT

    companion object {
        const val CAPABILITY_E2E_V1 = "e2e-v1"
        const val OFFLINE_REASON_TIME_LIMIT = "time-limit"
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
 * the share's password by design, so it is never logged. It is null when the producer has not opted in to
 * putting `access` on Drive (section 15 item X): such a share is listed but cannot be attached from the
 * record alone. [shareId] is the outsider capability of 5.4 and
 * stays out of logs for the same reason. [root] is `access.root` of amendment O, [publicPort] the
 * third-party port of `ANYWHERE-ACCESS` 5.5, [presence] the producing device's.
 */
data class SftpRendezvousResource(
    val resourceId: String,
    val deviceId: String,
    val kind: String,
    val name: String,
    val descriptor: String?,
    val updatedAtMs: Long,
    override val writtenAtMs: Long,
    override val ttlSeconds: Long,
    val shareId: String? = null,
    val publicPort: Int? = null,
    val root: String? = null,
    val presence: String? = null,
    /** The producer locked the root with its original PIN (17 item AG); the record carries the fact, never a key. */
    val pinProtected: Boolean = false,
    val protectionScheme: String? = null,
    /**
     * Item BA: the producer's host-key fingerprint and the root it shares, the resource's identity across every
     * channel. Not secrets, so they ride every store, a record without `access` included.
     */
    val hostKeyFingerprint: String? = null,
    val identityRoot: String? = null,
    /**
     * 27 item DI: `sealedAccess` as compact JSON - the box over `access` that only the account's own devices open.
     * A producer writes it in place of [descriptor]; a consumer replaces it with the opened [descriptor].
     */
    val sealedAccess: String? = null,
    /** 20 item BD: the grant that lists this record to a guest; its box's context is `grant:<grantId>` (27 item DJ). */
    val grantId: String? = null,
    /** Set by the consumer only: why a record from the server carries no usable access (27 item DR). */
    val sealIssue: ExchangeSealIssue? = null,
) : SftpRendezvousRecord {

    /**
     * The `Resource` field of the `pin-v1` proof: the virtual root name without its slashes. Null whenever this
     * record cannot be proven - not protected, a scheme this build does not know, no `access`, or no usable root.
     */
    val proofRoot: String?
        get() = root?.trim('/')?.takeIf {
            pinProtected && protectionScheme == PROTECTION_PIN_V1 && descriptor != null && it.isNotEmpty()
        }

    override fun toString(): String =
        "SftpRendezvousResource(resourceId=$resourceId, deviceId=$deviceId, kind=$kind, name=$name)"

    companion object {
        const val KIND_SFTP_SHARE = "sftp-share"

        /** The one protection scheme of DEVICE-EXCHANGE 17 item AG: the root demands its original PIN everywhere. */
        const val PROTECTION_PIN_V1 = "pin-v1"
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
    /** 27 item DK: the record's box as compact JSON, holding `relayKey`, `tunnelPin` and `credentials`. */
    val sealed: String? = null,
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
