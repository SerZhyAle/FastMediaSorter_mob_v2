package com.sza.fastmediasorter.domain.model

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.annotations.SerializedName
import timber.log.Timber
import javax.inject.Inject

/**
 * S2551, watch to phone: start a camera session, stop it, or switch its lens.
 *
 * This file holds the whole of what crosses the Data Layer for a camera-viewing session. Mirrored in
 * the watch module's `CameraSessionPayload.kt` - the two are hand-kept in step, as every payload pair
 * before them. Field names are pinned with `@SerializedName` because this crosses a process and a
 * version boundary: the modules share no code, so the wire name is the entire contract and R8
 * renaming a field here would make the phone's answer unreadable rather than merely unknown.
 *
 * No video ever rides these types. The frames travel over the phone's own LAN server, whose finished
 * address arrives in [WearCameraAckPayload].
 *
 * One type for start, stop and switch rather than three: the path already distinguishes them, and a
 * second class would be a name for the same fields that could drift.
 *
 * Lives in `src/main` rather than in `wearGms`, so the ViewModel that shows the outcome compiles for
 * every flavor while only the GMS-backed listener puts it on the wire.
 */
data class WearCameraCommandPayload(

    /**
     * Correlates the answer with the command that asked for it.
     *
     * Needed even though only one session exists at a time, because a stale ack from a request that
     * expired minutes ago is otherwise indistinguishable from the answer to the request the watch is
     * waiting on right now.
     */
    @SerializedName("requestId") val requestId: String,

    /** Null on start and stop; carries the chosen lens id on switch. */
    @SerializedName("lensId") val lensId: String? = null
)

/**
 * S2551: why the phone is not serving a camera. Each value calls for different words on the watch.
 *
 * Every constant is pinned by name, not only the properties that carry it: Gson writes an enum as the
 * constant's own name, so an `@SerializedName` on the field above does not survive R8 renaming these.
 */
enum class WearCameraRefusal {

    /** The owner was never asked: notifications are off on the phone, so nothing could be posted. */
    @SerializedName("NOT_ASKED")
    NOT_ASKED,

    /**
     * Nothing is armed on the phone, so there was no standing consent to serve against.
     *
     * Separate from [NOT_ASKED] because the two call for opposite remedies and the watch cannot tell
     * them apart on its own: it never learns which flavor the paired phone runs, so a phone that
     * refuses for want of an armed capture has to say so rather than borrow the notification reason.
     */
    @SerializedName("NOT_ARMED")
    NOT_ARMED,

    /** The owner saw the request and refused it. */
    @SerializedName("DECLINED")
    DECLINED,

    /** Nobody answered the request before it cancelled itself. */
    @SerializedName("EXPIRED")
    EXPIRED,

    /** The camera or the encoder would not open, so there is nothing to serve. */
    @SerializedName("CAPTURE_FAILED")
    CAPTURE_FAILED,

    /** The phone is not on a network this watch could reach - strategic pillar E. */
    @SerializedName("NO_NETWORK")
    NO_NETWORK,

    /** The phone is paired but cannot serve its LAN address until Wi-Fi is enabled. */
    @SerializedName("NOT_ON_WIFI")
    NOT_ON_WIFI,

    /** This watch asked for the session to end, and it has. Not a failure - the answer to a stop. */
    @SerializedName("STOPPED")
    STOPPED,

    /** A session is already running, or already being asked for. The newcomer is refused, not queued. */
    @SerializedName("BUSY")
    BUSY,

    /**
     * This build cannot serve video at all.
     *
     * The honest answer while the phone's broadcast layer carries audio only: a watch that asks a
     * build predating the video mode gets a named refusal rather than a timeout it must guess about.
     */
    @SerializedName("NOT_SUPPORTED")
    NOT_SUPPORTED,

    /**
     * A reason this build has no name for.
     *
     * The decoder substitutes it for an unreadable value, because Gson maps an unknown enum name to
     * null and null is how this payload says "the phone is serving" - so without this a future
     * refusal would read on an older build as an invitation to open an empty URL.
     */
    @SerializedName("UNKNOWN")
    UNKNOWN
}

/**
 * One camera the phone is willing to open, as the watch must show it.
 *
 * The label crosses as a key rather than as text: the watch renders in its own locale, and sending a
 * phone-side string would show the phone's language on the watch whenever the two differ.
 */
data class WearCameraLensDto(

    @SerializedName("id") val id: String,

    /** Stable key the watch resolves to its own localized label. */
    @SerializedName("labelKey") val labelKey: String,

    /** Which way the lens points, as the platform reports it. */
    @SerializedName("facing") val facing: String
)

/**
 * Phone to watch: where to watch, or why not.
 *
 * The address is a finished URL rather than a host and a port, which is what makes this wire
 * transport-proof: an `rtsp://` address and the MPEG-TS fallback's `http://` address are then the
 * same contract, so the transport verdict of S2662 changes nothing here.
 *
 * A refusal arrives as a value rather than as an absent ack, so the watch never has to tell "refused"
 * from "lost".
 */
data class WearCameraAckPayload(

    @SerializedName("requestId") val requestId: String,

    /** The finished stream address. Empty exactly when [refusal] is set. */
    @SerializedName("url") val url: String,

    /** Every lens this phone offers. Empty exactly when [refusal] is set. */
    @SerializedName("lenses") val lenses: List<WearCameraLensDto>,

    /** Which of [lenses] is live. Null exactly when [refusal] is set. */
    @SerializedName("activeLensId") val activeLensId: String?,

    /** Null exactly when the phone is serving. */
    @SerializedName("refusal") val refusal: WearCameraRefusal?
) {

    companion object {

        fun serving(
            requestId: String,
            url: String,
            lenses: List<WearCameraLensDto>,
            activeLensId: String?
        ): WearCameraAckPayload = WearCameraAckPayload(
            requestId = requestId,
            url = url,
            lenses = lenses,
            activeLensId = activeLensId,
            refusal = null
        )

        fun refused(requestId: String, refusal: WearCameraRefusal): WearCameraAckPayload =
            WearCameraAckPayload(
                requestId = requestId,
                url = "",
                lenses = emptyList(),
                activeLensId = null,
                refusal = refusal
            )
    }
}

/**
 * The wire form of both payloads, in one place.
 *
 * A malformed payload decodes to null rather than throwing: a command from a watch on another build
 * must leave this phone exactly as it found it, and an exception crossing `WearableListenerService`
 * would take the process with it.
 */
class WearCameraSessionPayloadCodec @Inject constructor(private val gson: Gson) {

    fun encodeCommand(payload: WearCameraCommandPayload): ByteArray =
        gson.toJson(payload).toByteArray(Charsets.UTF_8)

    fun decodeCommand(data: ByteArray): WearCameraCommandPayload? = try {
        gson.fromJson(data.decodeToString(), WearCameraCommandPayload::class.java)
    } catch (e: JsonSyntaxException) {
        Timber.w(e, "Undecodable camera command payload")
        null
    }

    fun encodeAck(payload: WearCameraAckPayload): ByteArray =
        gson.toJson(payload).toByteArray(Charsets.UTF_8)

    fun decodeAck(data: ByteArray): WearCameraAckPayload? = try {
        gson.fromJson(data.decodeToString(), WearCameraAckPayload::class.java)
            ?.let(::nameUnknownRefusal)
    } catch (e: JsonSyntaxException) {
        Timber.w(e, "Undecodable camera ack payload")
        null
    }

    /**
     * A refusal this build cannot name arrives from Gson as a null [WearCameraAckPayload.refusal],
     * which is the same shape as "the phone is serving" - so a served session is recognised by
     * carrying a URL, and anything else without a reason is named [WearCameraRefusal.UNKNOWN] rather
     * than opened in the player.
     */
    private fun nameUnknownRefusal(ack: WearCameraAckPayload): WearCameraAckPayload =
        if (ack.refusal == null && ack.url.isBlank()) {
            ack.copy(refusal = WearCameraRefusal.UNKNOWN)
        } else {
            ack
        }
}
