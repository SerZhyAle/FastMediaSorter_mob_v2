package com.sza.fastmediasorter.data.remote.exchange

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.sza.fastmediasorter.data.remote.sftp.anywhere.SftpRendezvousCodec
import com.sza.fastmediasorter.domain.model.SftpRendezvousBroadcast
import com.sza.fastmediasorter.domain.model.SftpRendezvousRecord
import timber.log.Timber

/**
 * The log of the broadcasts half of the account directory (S4441), in one vocabulary so a joint run with the
 * other product can match a record across both logs. A record is named by the first 8 characters of its broadcast
 * and device ids, its mode, the part of its descriptor `sourceId` after the last `:` and whether it came sealed.
 * The full ids are the capability of the secret-link model and the descriptor and the box carry keys, so none of
 * them is ever written here.
 */
internal object ExchangeBroadcastDiagnostics {

    /** One tag for every line, so a single filter shows the whole path from the wire to the list. */
    const val TAG = "ExchangeBroadcasts:"

    private const val ID_PREFIX_LENGTH = 8
    private const val FIELD_MAX_LENGTH = 32
    private const val NONE = "-"
    private const val KEY_SOURCE_ID = "sourceId"
    private const val SOURCE_SEPARATOR = ':'

    fun prefix(id: String?): String = id?.take(ID_PREFIX_LENGTH)?.takeIf { it.isNotEmpty() } ?: NONE

    fun describe(record: SftpRendezvousBroadcast): String =
        "broadcast=${prefix(record.broadcastId)} device=${prefix(record.deviceId)} " +
            "mode=${record.mode.take(FIELD_MAX_LENGTH)} source=${sourceSuffix(record.descriptorJson)} " +
            "sealed=${if (record.sealed != null) "yes" else "no"}"

    /**
     * Reads one record of a `directory` or `changed` frame; a broadcast this build cannot read is dropped alone
     * and logs why, so a record the phone received never disappears without a trace.
     */
    fun decode(type: String, element: JsonElement): SftpRendezvousRecord? {
        val body = element as? JsonObject
        val record = body?.let { SftpRendezvousCodec.decodeBody(type, it) }
        if (record == null && type == SftpRendezvousCodec.TYPE_BROADCAST) {
            Timber.w("%s dropped a record from the wire (%s)", TAG, rejection(body))
        }
        return record
    }

    fun logDirectory(directory: DeviceExchangeDirectory) {
        Timber.i(
            "%s directory rev=%d devices=%d resources=%d broadcasts=%d",
            TAG,
            directory.revision,
            directory.devices.size,
            directory.resources.size,
            directory.broadcasts.size,
        )
        directory.broadcasts.forEach { Timber.i("%s directory rev=%d %s", TAG, directory.revision, describe(it)) }
    }

    fun logChanged(changed: DeviceExchangeChanged, heldRevision: Long?, outcome: String) {
        Timber.i(
            "%s changed rev=%d held=%s outcome=%s upserted=%d removed=%d",
            TAG,
            changed.revision,
            heldRevision?.toString() ?: NONE,
            outcome,
            changed.broadcasts.size,
            changed.removedBroadcastIds.size,
        )
        changed.broadcasts.forEach { Timber.i("%s changed rev=%d upsert %s", TAG, changed.revision, describe(it)) }
        changed.removedBroadcastIds.forEach {
            Timber.i("%s changed rev=%d removed broadcast=%s", TAG, changed.revision, prefix(it))
        }
    }

    fun label(outcome: DeviceExchangeDirectoryState.Outcome): String = when (outcome) {
        is DeviceExchangeDirectoryState.Outcome.Applied -> "applied"
        DeviceExchangeDirectoryState.Outcome.Gap -> "gap"
        DeviceExchangeDirectoryState.Outcome.Stale -> "stale"
    }

    /** What the opener made of a record; [problem] outcomes are warnings, the rest are plain information. */
    fun logOutcome(source: SftpRendezvousBroadcast, outcome: String, problem: Boolean) {
        if (problem) {
            Timber.w("%s opener %s outcome=%s", TAG, describe(source), outcome)
        } else {
            Timber.i("%s opener %s outcome=%s", TAG, describe(source), outcome)
        }
    }

    private fun rejection(body: JsonObject?): String {
        if (body == null) return "not-an-object"
        val (broadcastId, deviceId) = SftpRendezvousCodec.broadcastIdsOf(body)
        val reason = SftpRendezvousCodec.broadcastRejection(body) ?: "unreadable"
        return "$reason broadcast=${prefix(broadcastId)} device=${prefix(deviceId)}"
    }

    private fun sourceSuffix(descriptorJson: String): String {
        val sourceId = runCatching { JsonParser.parseString(descriptorJson).asJsonObject.get(KEY_SOURCE_ID) }
            .getOrNull()?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
        return sourceId?.substringAfterLast(SOURCE_SEPARATOR)?.take(FIELD_MAX_LENGTH)
            ?.takeIf { it.isNotEmpty() } ?: NONE
    }
}
