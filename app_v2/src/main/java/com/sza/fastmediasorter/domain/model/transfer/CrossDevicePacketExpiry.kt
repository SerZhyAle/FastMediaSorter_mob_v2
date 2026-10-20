package com.sza.fastmediasorter.domain.model.transfer

import java.util.concurrent.TimeUnit

/**
 * S4231: when a packet is past its life, judged on Drive's clock (contract DEVICE-EXCHANGE item CR) - the
 * packet folder's Drive `createdTime` against the `Date` of Drive's response. A device clock never enters
 * the decision, so two phones with different clocks expire the same packet at the same moment.
 */
object CrossDevicePacketExpiry {

    const val REQUEST_TTL_SECONDS: Long = 900L
    const val REPLY_TTL_SECONDS: Long = 86_400L
    const val FILES_TTL_DAYS: Int = 7

    /** A read of the clipboard value in progress is not cut by a newer value for this long (item CP). */
    val CLIPBOARD_GRACE_MS: Long = TimeUnit.MINUTES.toMillis(CLIPBOARD_GRACE_MINUTES)

    /** A packet folder without a manifest is an upload that never finished once it is this old. */
    val ABANDONED_UPLOAD_MS: Long = TimeUnit.DAYS.toMillis(FILES_TTL_DAYS.toLong())

    fun isExpired(
        kind: CrossDevicePayloadKind,
        createdTimeMs: Long,
        serverNowMs: Long,
        ttlSeconds: Long?,
        ttlDays: Int
    ): Boolean {
        if (kind == CrossDevicePayloadKind.CLIPBOARD) return false
        val lifeMs = ttlSeconds?.let(TimeUnit.SECONDS::toMillis) ?: TimeUnit.DAYS.toMillis(ttlDays.toLong())
        return serverNowMs - createdTimeMs > lifeMs
    }

    fun isExpired(entry: CrossDevicePacketEntry, serverNowMs: Long): Boolean = isExpired(
        kind = entry.manifest.payloadKind,
        createdTimeMs = entry.createdTimeMs,
        serverNowMs = serverNowMs,
        ttlSeconds = entry.manifest.ttlSeconds,
        ttlDays = entry.manifest.ttlDays
    )

    fun isAbandonedUpload(createdTimeMs: Long, serverNowMs: Long): Boolean =
        serverNowMs - createdTimeMs > ABANDONED_UPLOAD_MS
}

private const val CLIPBOARD_GRACE_MINUTES = 10L
