package com.sza.fastmediasorter.domain.model

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

/**
 * S1802: the phone-side mirror of the watch's log report.
 *
 * Mirrored rather than shared because `app_v2` does not depend on `:wear` - the same reason
 * [WearSettingsPayload] exists twice. Field names are pinned on both sides, so the two copies agree
 * on the wire even after R8 renames one of them.
 */
data class WearLogReportPayload(
    @SerializedName("requestId") val requestId: String,
    @SerializedName("formatVersion") val formatVersion: Int,
    @SerializedName("appVersionName") val appVersionName: String,
    @SerializedName("appVersionCode") val appVersionCode: Long,
    @SerializedName("deviceModel") val deviceModel: String,
    @SerializedName("androidRelease") val androidRelease: String,
    @SerializedName("capturedAtEpochMillis") val capturedAtEpochMillis: Long,
    @SerializedName("logText") val logText: String
) {

    companion object {

        /** The one format this build understands; anything else is answered with a refusal. */
        const val FORMAT_VERSION: Int = 1
    }
}

/** The phone's answer to one report. Mirrors the watch's own copy. */
data class WearLogReportAck(
    @SerializedName("requestId") val requestId: String,
    @SerializedName("accepted") val accepted: Boolean,
    @SerializedName("reason") val reason: String? = null
)

/** What parsing one received report produced. Parsing never throws at the caller. */
sealed interface WearLogReportParseResult {

    data class Parsed(val payload: WearLogReportPayload) : WearLogReportParseResult

    data class UnsupportedVersion(val version: Int) : WearLogReportParseResult

    data object Malformed : WearLogReportParseResult
}

/** S1802: reads the watch's report and writes the phone's answer. */
object WearLogReportCodec {

    fun parse(bytes: ByteArray, gson: Gson): WearLogReportParseResult {
        val payload = runCatching {
            gson.fromJson(String(bytes, Charsets.UTF_8), WearLogReportPayload::class.java)
        }.getOrNull()

        // Gson fills fields by reflection and honours neither Kotlin nullability nor Kotlin defaults,
        // so a JSON object missing a key yields an instance whose non-null field is null.
        val logText: String? = payload?.logText
        val requestId: String? = payload?.requestId

        return when {
            payload == null || logText == null || requestId == null ->
                WearLogReportParseResult.Malformed
            payload.formatVersion != WearLogReportPayload.FORMAT_VERSION ->
                WearLogReportParseResult.UnsupportedVersion(payload.formatVersion)
            else -> WearLogReportParseResult.Parsed(payload)
        }
    }

    fun serializeAck(ack: WearLogReportAck, gson: Gson): ByteArray =
        gson.newBuilder().disableHtmlEscaping().create().toJson(ack).toByteArray()
}
