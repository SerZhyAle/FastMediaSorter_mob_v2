package com.sza.fastmediasorter.domain.model

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.annotations.SerializedName
import timber.log.Timber
import javax.inject.Inject

/**
 * S2550 ADR-1, phone to watch: start, or stop. It identifies itself and says nothing else.
 *
 * This file holds the whole of what crosses the Data Layer for a listening session. Mirrored in the
 * watch module's `ListenSessionPayload.kt` - the two are hand-kept in step, as the twelve payload
 * pairs before them. Field names are pinned with `@SerializedName` because this crosses a process and
 * a version boundary: the modules share no code, so the wire name is the entire contract and R8
 * renaming a field here would make the watch's answer unreadable rather than merely unknown.
 *
 * No audio ever rides these types. The Data Layer's Bluetooth path declares roughly 32 kbps, below
 * the project's own floor for audio, and its Wi-Fi path routes through a node on Google servers -
 * exactly the intermediary the owner excluded in S1699. The bytes travel over the watch's own LAN
 * server, whose address arrives in [WearListenAckPayload].
 *
 * One type for both the start and the stop path rather than two identical ones: the path already
 * distinguishes them, and a second class would be a name for the same field that could drift.
 *
 * Lives in `src/main` rather than in `wearGms`, so the ViewModel that shows the outcome compiles for
 * every flavor while only the GMS-backed listener puts it on the wire.
 */
data class WearListenCommandPayload(

    /**
     * Correlates the answer with the command that asked for it.
     *
     * Needed even though only one session exists at a time, because a stale ack from a request that
     * expired minutes ago is otherwise indistinguishable from the answer to the request the user is
     * waiting on right now.
     */
    @SerializedName("requestId") val requestId: String
)

/**
 * S2550: why the watch is not serving. Each value calls for different words on the phone.
 *
 * Every constant is pinned by name, not only the properties that carry it: Gson writes an enum as the
 * constant's own name, so an `@SerializedName` on the field above does not survive R8 renaming these.
 */
enum class WearListenRefusal {

    /** The owner was never asked: notifications are off on the watch, so nothing could be posted. */
    @SerializedName("NOT_ASKED")
    NOT_ASKED,

    /** The owner saw the request and refused it. */
    @SerializedName("DECLINED")
    DECLINED,

    /** Nobody answered the request before it cancelled itself. */
    @SerializedName("EXPIRED")
    EXPIRED,

    /** The microphone or the recorder would not open, so there is nothing to serve. */
    @SerializedName("CAPTURE_FAILED")
    CAPTURE_FAILED,

    /** The watch is not on a network this phone could reach - strategic pillar E. */
    @SerializedName("NO_NETWORK")
    NO_NETWORK,

    /** The watch is paired but cannot serve its LAN address until Wi-Fi is enabled. */
    @SerializedName("NOT_ON_WIFI")
    NOT_ON_WIFI,

    /** This phone asked for the session to end, and it has. Not a failure - the answer to a stop. */
    @SerializedName("STOPPED")
    STOPPED,

    /** Someone is already listening, or already being asked. The newcomer is refused, not queued. */
    @SerializedName("BUSY")
    BUSY,

    /**
     * A reason this build has no name for.
     *
     * The decoder substitutes it for an unreadable value, because Gson maps an unknown enum name to
     * null and null is how this payload says "the watch is serving" - so without this a future
     * refusal would read on an older build as an invitation to play a stream at port zero.
     */
    @SerializedName("UNKNOWN")
    UNKNOWN
}

/**
 * Watch to phone: where to listen, or why not.
 *
 * ADR-2 makes this the reason no discovery subsystem exists - the watch knows its own address and the
 * control channel is already open, so it simply says where it is serving. A boolean-only ack would put
 * that problem straight back into a module that contains no mDNS or NSD code at all.
 *
 * A refusal arrives as a value here rather than as an absent ack, so this phone never has to tell
 * "refused" from "lost".
 */
data class WearListenAckPayload(

    @SerializedName("requestId") val requestId: String,

    /** The watch's own LAN address. Empty exactly when [refusal] is set. */
    @SerializedName("host") val host: String,

    /** The port the watch's LAN server bound to. Zero exactly when [refusal] is set. */
    @SerializedName("port") val port: Int,

    /** Null exactly when the watch is serving. */
    @SerializedName("refusal") val refusal: WearListenRefusal?
)

/** The one path the watch's server answers - it does not even look at it. */
const val WEAR_LISTEN_PATH = "/listen"

/**
 * ADR-4: an ordinary HTTP address, which is what lets the player open it as it opens radio.
 *
 * An extension rather than a member, so the wire model stays exactly the set of fields that cross the
 * Data Layer - a computed property inside it reads to `assert-gson-persistence-contract` as a field
 * nobody pinned.
 */
fun WearListenAckPayload.streamUrl(): String = "http://$host:$port$WEAR_LISTEN_PATH"

/**
 * The wire form of both payloads, in one place.
 *
 * A malformed payload decodes to null rather than throwing: an answer from a watch on another build
 * must leave this phone exactly as it found it, and an exception crossing `WearableListenerService`
 * would take the process with it.
 */
class WearListenSessionPayloadCodec @Inject constructor(private val gson: Gson) {

    fun encodeCommand(payload: WearListenCommandPayload): ByteArray =
        gson.toJson(payload).toByteArray(Charsets.UTF_8)

    fun decodeCommand(data: ByteArray): WearListenCommandPayload? = try {
        gson.fromJson(data.decodeToString(), WearListenCommandPayload::class.java)
    } catch (e: JsonSyntaxException) {
        Timber.w(e, "Undecodable listen command payload")
        null
    }

    fun encodeAck(payload: WearListenAckPayload): ByteArray =
        gson.toJson(payload).toByteArray(Charsets.UTF_8)

    fun decodeAck(data: ByteArray): WearListenAckPayload? = try {
        gson.fromJson(data.decodeToString(), WearListenAckPayload::class.java)
            ?.let(::nameUnknownRefusal)
    } catch (e: JsonSyntaxException) {
        Timber.w(e, "Undecodable listen ack payload")
        null
    }

    /**
     * A refusal this build cannot name arrives from Gson as a null [WearListenAckPayload.refusal],
     * which is the same shape as "the watch is serving" - so a served address is recognised by
     * carrying a port, and anything else without a reason is named [WearListenRefusal.UNKNOWN] rather
     * than opened in the player.
     */
    private fun nameUnknownRefusal(ack: WearListenAckPayload): WearListenAckPayload =
        if (ack.refusal == null && ack.port <= 0) {
            ack.copy(refusal = WearListenRefusal.UNKNOWN)
        } else {
            ack
        }
}
