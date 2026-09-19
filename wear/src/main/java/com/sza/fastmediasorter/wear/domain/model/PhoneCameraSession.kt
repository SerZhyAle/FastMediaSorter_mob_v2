package com.sza.fastmediasorter.wear.domain.model

/**
 * S2551: why the watch is not watching the phone's camera.
 *
 * A superset of [CameraRefusal] rather than a reuse of it. The wire enum spells what the PHONE can
 * answer, and two of the reasons a session fails never reach the wire at all: the watch's own link
 * verdict runs before anything is sent, and a send that never left the watch has no ack to carry a
 * reason. Widening the wire enum with those would put constants on the contract that the phone can
 * never send and an older phone would have to ignore.
 */
enum class PhoneCameraFailure {

    /** The phone could not be reached at all - no node answered the command. */
    NO_PHONE,

    /** The watch is not on Wi-Fi, so no LAN address of the phone is reachable from here. */
    NOT_ON_WIFI,

    /** The watch has no link at all. */
    NO_NETWORK,

    /** The link is on Wi-Fi and below the video floor, so the picture would not arrive. */
    NARROW_LINK,

    /** The phone could not ask its owner: notifications are off there. */
    NOT_ASKED,

    /** The phone has nothing armed to serve, so no standing consent applied. */
    NOT_ARMED,

    /** The owner refused the request on the phone. */
    DECLINED,

    /** Nobody answered on the phone before the request cancelled itself. */
    EXPIRED,

    /** The camera or the encoder would not open on the phone. */
    CAPTURE_FAILED,

    /** This watch asked for the session to end, and it has. Not a failure - the answer to a stop. */
    STOPPED,

    /**
     * S3212: the phone is no longer serving - the broadcast ended there.
     *
     * Separate from [STOPPED], which says this watch asked for the end. The phone sends nothing when
     * its owner stops the broadcast himself, so this one is concluded on the watch from a refused
     * socket on the address the phone was serving a moment ago.
     */
    ENDED,

    /** The phone is already serving a session, or already being asked for one. */
    BUSY,

    /** The phone's build cannot serve video at all. */
    NOT_SUPPORTED,

    /** A reason neither side has a name for. */
    UNKNOWN
}

/** The wire refusal as this screen states it. Total, so a new wire constant cannot go unnamed. */
fun CameraRefusal.asSessionFailure(): PhoneCameraFailure = when (this) {
    CameraRefusal.NOT_ASKED -> PhoneCameraFailure.NOT_ASKED
    CameraRefusal.NOT_ARMED -> PhoneCameraFailure.NOT_ARMED
    CameraRefusal.DECLINED -> PhoneCameraFailure.DECLINED
    CameraRefusal.EXPIRED -> PhoneCameraFailure.EXPIRED
    CameraRefusal.CAPTURE_FAILED -> PhoneCameraFailure.CAPTURE_FAILED
    CameraRefusal.NO_NETWORK -> PhoneCameraFailure.NO_NETWORK
    CameraRefusal.NOT_ON_WIFI -> PhoneCameraFailure.NOT_ON_WIFI
    CameraRefusal.STOPPED -> PhoneCameraFailure.STOPPED
    CameraRefusal.BUSY -> PhoneCameraFailure.BUSY
    CameraRefusal.NOT_SUPPORTED -> PhoneCameraFailure.NOT_SUPPORTED
    CameraRefusal.UNKNOWN -> PhoneCameraFailure.UNKNOWN
}

/**
 * S3223: the same refusal, read against whether this watch asked for it.
 *
 * `STOPPED` is the wire's only word for an ended session, so the phone sends it both as the answer to
 * a stop and as its own announcement that the broadcast is over. Unasked, it would tell the owner his
 * watch ended a session he ended on the phone, which [PhoneCameraFailure.ENDED] states correctly.
 */
fun CameraRefusal.asSessionFailure(unrequested: Boolean): PhoneCameraFailure =
    if (unrequested && this == CameraRefusal.STOPPED) {
        PhoneCameraFailure.ENDED
    } else {
        asSessionFailure()
    }

/**
 * S2551: what the watch knows about the phone's camera session right now.
 *
 * [Requested] carries its own id because an ack that answers a request the owner already abandoned
 * must be dropped rather than acted on - a stale address opens a player onto a port that closed
 * minutes ago, and nothing about that failure names its cause.
 */
sealed interface PhoneCameraSessionState {

    /** Nothing asked for, nothing running. */
    data object Idle : PhoneCameraSessionState

    /** A command is on the wire and its answer has not arrived. */
    data class Requested(val requestId: String) : PhoneCameraSessionState

    /** The phone is serving [url], and offers [lenses]. */
    data class Live(
        /**
         * S3223: the id of the command whose ack opened this session.
         *
         * Kept because the phone names the same id when it announces the end of the session on its
         * own, and a live session that had forgotten it left the listener nothing to match that
         * announcement against.
         */
        val requestId: String,
        val url: String,
        val lenses: List<CameraLensDto>,
        val activeLensId: String?,
    ) : PhoneCameraSessionState

    /** The session ended or never began, for a reason the screen states in words. */
    data class Refused(val reason: PhoneCameraFailure) : PhoneCameraSessionState
}
