package com.sza.fastmediasorter.domain.model

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

/**
 * S3108: the phone-side mirror of the watch's system-information report.
 *
 * Mirrored rather than shared because `app_v2` does not depend on `:wear` - the same reason
 * [WearLogReportPayload] exists twice. Field names are pinned on both sides, so the two copies agree
 * on the wire even after R8 renames one of them.
 */
data class WearSystemInfoReportPayload(
    @SerializedName("requestId") val requestId: String,
    @SerializedName("formatVersion") val formatVersion: Int,
    @SerializedName("appVersionName") val appVersionName: String,
    @SerializedName("appVersionCode") val appVersionCode: Long,
    @SerializedName("deviceModel") val deviceModel: String,
    @SerializedName("androidRelease") val androidRelease: String,
    @SerializedName("capturedAtEpochMillis") val capturedAtEpochMillis: Long,
    @SerializedName("reportText") val reportText: String
) {

    companion object {

        /** The one format this build understands; anything else is answered with a refusal. */
        const val FORMAT_VERSION: Int = 1
    }
}

/** The phone's answer to one system-information report. Mirrors the watch's own copy. */
data class WearSystemInfoReportAck(
    @SerializedName("requestId") val requestId: String,
    @SerializedName("accepted") val accepted: Boolean,
    @SerializedName("reason") val reason: String? = null
)

/** What parsing one received report produced. Parsing never throws at the caller. */
sealed interface WearSystemInfoReportParseResult {

    data class Parsed(val payload: WearSystemInfoReportPayload) : WearSystemInfoReportParseResult

    data class UnsupportedVersion(val version: Int) : WearSystemInfoReportParseResult

    data object Malformed : WearSystemInfoReportParseResult
}

/** S3108: reads the watch's system-information report and writes the phone's answer. */
object WearSystemInfoReportCodec {

    fun parse(bytes: ByteArray, gson: Gson): WearSystemInfoReportParseResult {
        val payload = runCatching {
            gson.fromJson(String(bytes, Charsets.UTF_8), WearSystemInfoReportPayload::class.java)
        }.getOrNull()

        // Gson fills fields by reflection and honours neither Kotlin nullability nor Kotlin defaults,
        // so a JSON object missing a key yields an instance whose non-null field is null.
        val reportText: String? = payload?.reportText
        val requestId: String? = payload?.requestId

        return when {
            payload == null || reportText == null || requestId == null ->
                WearSystemInfoReportParseResult.Malformed
            payload.formatVersion != WearSystemInfoReportPayload.FORMAT_VERSION ->
                WearSystemInfoReportParseResult.UnsupportedVersion(payload.formatVersion)
            else -> WearSystemInfoReportParseResult.Parsed(payload)
        }
    }

    fun serializeAck(ack: WearSystemInfoReportAck, gson: Gson): ByteArray =
        gson.newBuilder().disableHtmlEscaping().create().toJson(ack).toByteArray()
}
