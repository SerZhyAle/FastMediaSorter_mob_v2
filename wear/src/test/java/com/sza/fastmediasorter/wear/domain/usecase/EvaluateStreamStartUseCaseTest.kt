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

    @Test
    fun `serving is allowed on a wide wifi link`() {
        val verdict = evaluateServing(wifi(downstreamKbps = WIDE_KBPS))

        assertEquals(StreamChannelVerdict.Allow, verdict)
    }

    @Test
    fun `serving over bluetooth says wifi is off, not that the channel is narrow`() {
        val channel = channelOf(WearNetworkChannelKind.BLUETOOTH, downstreamKbps = BLUETOOTH_KBPS)

        val verdict = evaluateServing(channel)

        assertEquals(StreamChannelVerdict.Refuse(StreamChannelReason.NOT_ON_WIFI), verdict)
    }

    /**
     * A wide non-Wi-Fi link is the case that separates the two reasons: it clears every bandwidth
     * floor, so anything that judged capacity alone would allow it - and the phone still cannot reach
     * a socket on the watch's LAN, because there is no LAN.
     */
    @Test
    fun `serving over a wide cellular link is still refused as not on wifi`() {
        val channel = channelOf(WearNetworkChannelKind.CELLULAR, downstreamKbps = WIDE_KBPS)

        val verdict = evaluateServing(channel)

        assertEquals(StreamChannelVerdict.Refuse(StreamChannelReason.NOT_ON_WIFI), verdict)
    }

    @Test
    fun `serving with no link at all is refused as no link`() {
        val verdict = evaluateServing(WearNetworkChannel.NONE)

        assertEquals(StreamChannelVerdict.Refuse(StreamChannelReason.NO_LINK), verdict)
    }

    @Test
    fun `serving over a narrow wifi link is still refused as narrow`() {
        val channel = wifi(downstreamKbps = BLUETOOTH_KBPS)

        val verdict = evaluateServing(channel)

        assertEquals(StreamChannelVerdict.Refuse(StreamChannelReason.NARROW_LINK), verdict)
    }

    /**
     * The case that separates the two refusals: a Bluetooth link declared wider than the video floor
     * still has no LAN to reach the phone on, so the Wi-Fi test has to run before the floors or the
     * owner is told the channel is narrow when the real answer is that Wi-Fi is off.
     */
    @Test
    fun `the phone camera over a wide bluetooth link says wifi is off, not that the channel is narrow`() {
        val channel = channelOf(WearNetworkChannelKind.BLUETOOTH, downstreamKbps = WIDE_KBPS)

        val verdict = evaluatePhoneCamera(channel)

        assertEquals(StreamChannelVerdict.Refuse(StreamChannelReason.NOT_ON_WIFI), verdict)
    }

    @Test
    fun `the phone camera on a wifi link with no estimate degrades rather than refuses`() {
        val verdict = evaluatePhoneCamera(wifi(downstreamKbps = null))

        assertEquals(
            StreamChannelVerdict.AllowDegraded(StreamChannelReason.BANDWIDTH_UNKNOWN),
            verdict
        )
    }

    /** The video floor, not the audio one: a link that carries a radio station cannot carry a camera. */
    @Test
    fun `the phone camera below the video floor is refused as narrow`() {
        val verdict = evaluatePhoneCamera(wifi(downstreamKbps = BETWEEN_FLOORS_KBPS))

        assertEquals(StreamChannelVerdict.Refuse(StreamChannelReason.NARROW_LINK), verdict)
    }

    @Test
    fun `the phone camera on a wide validated wifi link is allowed`() {
        val verdict = evaluatePhoneCamera(wifi(downstreamKbps = WIDE_KBPS))

        assertEquals(StreamChannelVerdict.Allow, verdict)
    }

    private fun evaluate(channel: WearNetworkChannel, mediaKind: String): StreamChannelVerdict =
        EvaluateStreamStartUseCase(monitorOf(channel)).invoke(mediaKind)

    private fun evaluateServing(channel: WearNetworkChannel): StreamChannelVerdict =
        EvaluateStreamStartUseCase(monitorOf(channel)).forServing()

    private fun evaluatePhoneCamera(channel: WearNetworkChannel): StreamChannelVerdict =
        EvaluateStreamStartUseCase(monitorOf(channel)).forPhoneCamera()

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
    ): WearNetworkChannel = channelOf(WearNetworkChannelKind.WIFI, downstreamKbps, isValidated)

    private fun channelOf(
        kind: WearNetworkChannelKind,
        downstreamKbps: Int?,
        isValidated: Boolean = true
    ): WearNetworkChannel = WearNetworkChannel(
        kind = kind,
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
