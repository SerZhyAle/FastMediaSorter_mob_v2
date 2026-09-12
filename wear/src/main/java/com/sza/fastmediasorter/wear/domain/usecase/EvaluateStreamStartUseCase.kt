package com.sza.fastmediasorter.wear.domain.usecase

import com.sza.fastmediasorter.wear.domain.model.StreamChannelReason
import com.sza.fastmediasorter.wear.domain.model.StreamChannelVerdict
import com.sza.fastmediasorter.wear.domain.model.WearNetworkChannelKind
import com.sza.fastmediasorter.wear.domain.repository.WearNetworkChannelMonitor
import javax.inject.Inject

/**
 * Floors on the bandwidth the link *declares*, not on throughput anyone measured.
 *
 * They matter because a Bluetooth-carried link declares roughly 32 kbps, which clears none of them -
 * so a stream that would arrive as ragged sound is refused with a stated reason instead.
 */
private const val AUDIO_FLOOR_KBPS = 128
private const val VIDEO_FLOOR_KBPS = 800
private const val RTSP_FLOOR_KBPS = 800

/**
 * Decides whether a stream may be opened on the link the watch has right now.
 *
 * Runs before the stream is opened, because the alternative the strategic spec rules out is the user
 * hearing the answer as broken audio.
 */
class EvaluateStreamStartUseCase @Inject constructor(
    private val monitor: WearNetworkChannelMonitor
) {

    /** The playback side: may this watch open the stream it is about to play? */
    operator fun invoke(mediaKind: String): StreamChannelVerdict =
        evaluate(floorFor(mediaKind), requireWifi = false)

    /**
     * S2550 pillar E, the serving side: is this watch ready to hand its audio to a phone?
     *
     * The transport is read here and nowhere else. S1728 ADR-2 established that the transport name
     * predicts capacity badly, and that still holds - this is not a capacity question. What the phone
     * has to reach is a socket on the watch's own LAN, which exists on Wi-Fi and on nothing else, so
     * the kind is the fact rather than a proxy for one.
     *
     * The Wi-Fi test precedes the bandwidth floors deliberately: a Bluetooth-carried link declares
     * roughly 32 kbps and would fail them too, reporting a narrow channel to an owner whose actual
     * problem is that Wi-Fi is off.
     */
    fun forServing(): StreamChannelVerdict = evaluate(AUDIO_FLOOR_KBPS, requireWifi = true)

    /**
     * S2551 pillar E: may this watch open the camera stream the phone serves on its own LAN?
     *
     * The playback entrance [invoke] cannot answer this. It passes `requireWifi = false`, so a link
     * that declares no bandwidth at all returns `AllowDegraded(BANDWIDTH_UNKNOWN)` and the player
     * opens on a link that can never route to the phone's LAN address - the endless load the
     * strategic criterion 4 forbids. A cellular link is the mirror case: it clears the video floor
     * and still reaches nothing.
     */
    fun forPhoneCamera(): StreamChannelVerdict = evaluate(VIDEO_FLOOR_KBPS, requireWifi = true)

    private fun evaluate(floorKbps: Int, requireWifi: Boolean): StreamChannelVerdict {
        val channel = monitor.channel.value
        return when {
            channel.kind == WearNetworkChannelKind.NONE ->
                StreamChannelVerdict.Refuse(StreamChannelReason.NO_LINK)

            requireWifi && channel.kind != WearNetworkChannelKind.WIFI ->
                StreamChannelVerdict.Refuse(StreamChannelReason.NOT_ON_WIFI)

            !channel.hasBandwidthEstimate ->
                StreamChannelVerdict.AllowDegraded(StreamChannelReason.BANDWIDTH_UNKNOWN)

            !channel.isValidated ->
                StreamChannelVerdict.AllowDegraded(StreamChannelReason.UNVALIDATED_LINK)

            (channel.downstreamKbps ?: 0) >= floorKbps ->
                StreamChannelVerdict.Allow

            else ->
                StreamChannelVerdict.Refuse(StreamChannelReason.NARROW_LINK)
        }
    }

    /** Audio is the classifier's default for anything it cannot place, so it is the else branch too. */
    private fun floorFor(mediaKind: String): Int = when (mediaKind) {
        ClassifyWearStreamMediaKindUseCase.VIDEO -> VIDEO_FLOOR_KBPS
        ClassifyWearStreamMediaKindUseCase.RTSP -> RTSP_FLOOR_KBPS
        else -> AUDIO_FLOOR_KBPS
    }
}
