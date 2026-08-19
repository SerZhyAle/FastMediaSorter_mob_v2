package com.sza.fastmediasorter.wear.data.wear

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

/**
 * S1802: the log report one watch-to-phone message carries.
 *
 * The version travels inside the payload rather than in the path, so a phone that does not know a
 * newer shape can still answer - it parses the version, refuses by name and says why. A path-encoded
 * version would leave the older phone silent instead, which strategic 7 names as the failure mode
 * that makes the whole action look broken.
 */
data class WearLogReportPayload(
    @SerializedName("requestId") val requestId: String,
    @SerializedName("formatVersion") val formatVersion: Int = FORMAT_VERSION,
    @SerializedName("appVersionName") val appVersionName: String,
    @SerializedName("appVersionCode") val appVersionCode: Long,
    @SerializedName("deviceModel") val deviceModel: String,
    @SerializedName("androidRelease") val androidRelease: String,
    @SerializedName("capturedAtEpochMillis") val capturedAtEpochMillis: Long,
    @SerializedName("logText") val logText: String
) {

    companion object {

        /** Bumped whenever a field changes meaning; the phone refuses anything it does not know. */
        const val FORMAT_VERSION: Int = 1
    }
}

/** What parsing one received report produced. Parsing never throws at the caller. */
sealed interface WearLogReportParseResult {

    /** A report this build understands. */
    data class Parsed(val payload: WearLogReportPayload) : WearLogReportParseResult

    /** Well-formed, but written by a build newer or older than this one. */
    data class UnsupportedVersion(val version: Int) : WearLogReportParseResult

    /** Not a report at all - truncated, empty or not this shape. */
    data object Malformed : WearLogReportParseResult
}

/**
 * S1802: turns a report into the bytes of one Data Layer message and back.
 *
 * Gson is passed in rather than held here: the module injects a single configured instance, and a
 * second private one would drift from it the first time its configuration changes.
 */
object WearLogReportCodec {

    /**
     * HTML escaping off, because this is a wire format and not a web page.
     *
     * The injected Gson is a default one, which escapes `=`, `&`, `<`, `>` and `'` to a six-byte
     * unicode sequence. Log lines are full of `=` - every masked credential the tree writes ends in
     * one - so a snapshot at the buffer ceiling would serialise to six times its size and blow past
     * the Data Layer message limit. Derived from the caller's instance rather than built fresh, so
     * every other setting the module configures still applies.
     */
    private fun wireGson(gson: Gson): Gson = gson.newBuilder().disableHtmlEscaping().create()

    /** Stamps this build's [WearLogReportPayload.FORMAT_VERSION] rather than trusting the caller's. */
    fun serialize(payload: WearLogReportPayload, gson: Gson): ByteArray =
        wireGson(gson)
            .toJson(payload.copy(formatVersion = WearLogReportPayload.FORMAT_VERSION))
            .toByteArray()

    /**
     * Reads [bytes] back into a report.
     *
     * Every failure is a returned value, never an exception: the receiving side answers the watch
     * with the reason, and a throw crossing the Data Layer callback would be a silent drop instead.
     */
    fun parse(bytes: ByteArray, gson: Gson): WearLogReportParseResult {
        val payload = runCatching {
            gson.fromJson(String(bytes, Charsets.UTF_8), WearLogReportPayload::class.java)
        }.getOrNull()

        // Gson fills fields by reflection and honours neither Kotlin nullability nor Kotlin defaults,
        // so a JSON object missing "logText" yields an instance whose non-null field is null. The
        // nullable locals are what let that be checked without the compiler calling the test senseless.
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
}
