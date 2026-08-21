package com.sza.fastmediasorter.wear.domain.usecase

import com.sza.fastmediasorter.wear.domain.model.StreamChannelReason
import com.sza.fastmediasorter.wear.domain.model.StreamChannelVerdict
import com.sza.fastmediasorter.wear.domain.model.WearNetworkChannel
import com.sza.fastmediasorter.wear.domain.model.WearNetworkChannelKind
import com.sza.fastmediasorter.wear.domain.repository.WearNetworkChannelMonitor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Test

class EvaluateStreamStartUseCaseTest {

    @Test
    fun `no link at all is refused and says so`() {
        val verdict = evaluate(WearNetworkChannel.NONE, ClassifyWearStreamMediaKindUseCase.AUDIO)

        assertEquals(StreamChannelVerdict.Refuse(StreamChannelReason.NO_LINK), verdict)
    }

    @Test
    fun `an absent bandwidth estimate degrades rather than refuses`() {
        val channel = wifi(downstreamKbps = null)

        val verdict = evaluate(channel, ClassifyWearStreamMediaKindUseCase.AUDIO)

        assertEquals(
            StreamChannelVerdict.AllowDegraded(StreamChannelReason.BANDWIDTH_UNKNOWN),
            verdict
        )
    }

    @Test
    fun `an unvalidated link degrades rather than refuses`() {
        val channel = wifi(downstreamKbps = WIDE_KBPS, isValidated = false)

        val verdict = evaluate(channel, ClassifyWearStreamMediaKindUseCase.AUDIO)

        assertEquals(
            StreamChannelVerdict.AllowDegraded(StreamChannelReason.UNVALIDATED_LINK),
            verdict
        )
    }

    @Test
    fun `a wide link allows the stream`() {
        val channel = wifi(downstreamKbps = WIDE_KBPS)

        val verdict = evaluate(channel, ClassifyWearStreamMediaKindUseCase.AUDIO)

        assertEquals(StreamChannelVerdict.Allow, verdict)
    }

    @Test
    fun `a link below the floor is refused as narrow`() {
        val channel = wifi(downstreamKbps = BLUETOOTH_KBPS)

        val verdict = evaluate(channel, ClassifyWearStreamMediaKindUseCase.AUDIO)

        assertEquals(StreamChannelVerdict.Refuse(StreamChannelReason.NARROW_LINK), verdict)
    }

    @Test
    fun `the floor is per media kind - one link allows audio and refuses video`() {
        val channel = wifi(downstreamKbps = BETWEEN_FLOORS_KBPS)

        val audio = evaluate(channel, ClassifyWearStreamMediaKindUseCase.AUDIO)
        val video = evaluate(channel, ClassifyWearStreamMediaKindUseCase.VIDEO)

        assertEquals(StreamChannelVerdict.Allow, audio)
        assertEquals(StreamChannelVerdict.Refuse(StreamChannelReason.NARROW_LINK), video)
    }

    @Test
    fun `rtsp is held to the video floor, not the audio one`() {
        val channel = wifi(downstreamKbps = BETWEEN_FLOORS_KBPS)

        val verdict = evaluate(channel, ClassifyWearStreamMediaKindUseCase.RTSP)

        assertEquals(StreamChannelVerdict.Refuse(StreamChannelReason.NARROW_LINK), verdict)
    }

    private fun evaluate(channel: WearNetworkChannel, mediaKind: String): StreamChannelVerdict =
        EvaluateStreamStartUseCase(monitorOf(channel)).invoke(mediaKind)

    /**
     * An anonymous object rather than a named fake class: this package is `domain/usecase`, where the
     * naming gate requires a `*UseCase` suffix, and calling a monitor a use case to satisfy it would
     * put a false name in the tree to please a check.
     */
    private fun monitorOf(channelValue: WearNetworkChannel): WearNetworkChannelMonitor =
        object : WearNetworkChannelMonitor {
            override val channel: StateFlow<WearNetworkChannel> = MutableStateFlow(channelValue)
        }

    private fun wifi(
        downstreamKbps: Int?,
        isValidated: Boolean = true
    ): WearNetworkChannel = WearNetworkChannel(
        kind = WearNetworkChannelKind.WIFI,
        downstreamKbps = downstreamKbps,
        upstreamKbps = null,
        isMetered = false,
        isValidated = isValidated
    )

    companion object {
        private const val WIDE_KBPS = 5000
        private const val BLUETOOTH_KBPS = 32
        private const val BETWEEN_FLOORS_KBPS = 300
    }
}
