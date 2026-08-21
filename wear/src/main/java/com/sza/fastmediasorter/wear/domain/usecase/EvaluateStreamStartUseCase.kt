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

    operator fun invoke(mediaKind: String): StreamChannelVerdict {
        val channel = monitor.channel.value
        return when {
            channel.kind == WearNetworkChannelKind.NONE ->
                StreamChannelVerdict.Refuse(StreamChannelReason.NO_LINK)

            !channel.hasBandwidthEstimate ->
                StreamChannelVerdict.AllowDegraded(StreamChannelReason.BANDWIDTH_UNKNOWN)

            !channel.isValidated ->
                StreamChannelVerdict.AllowDegraded(StreamChannelReason.UNVALIDATED_LINK)

            (channel.downstreamKbps ?: 0) >= floorFor(mediaKind) ->
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
