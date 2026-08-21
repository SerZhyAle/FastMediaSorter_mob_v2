package com.sza.fastmediasorter.wear.ui.player.helpers

import com.sza.fastmediasorter.wear.domain.model.StreamChannelReason
import com.sza.fastmediasorter.wear.domain.model.StreamChannelVerdict
import com.sza.fastmediasorter.wear.domain.repository.StreamNetworkHold
import com.sza.fastmediasorter.wear.domain.repository.WearNetworkChannelMonitor
import com.sza.fastmediasorter.wear.domain.usecase.EvaluateStreamStartUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the network hold and channel observation for one player ViewModel's direct stream.
 *
 * Keeping this state outside the two players makes pause, error and screen-exit release paths identical.
 */
class StreamPlaybackSessionManager(
    private val scope: CoroutineScope,
    private val networkHold: StreamNetworkHold,
    private val channelMonitor: WearNetworkChannelMonitor,
    private val evaluateStreamStart: EvaluateStreamStartUseCase,
    private val onChannelReason: (StreamChannelReason?) -> Unit
) {

    private var mediaKind: String? = null
    private var sessionJob: Job? = null

    fun prepare(mediaKind: String): Boolean {
        val verdict = evaluateStreamStart(mediaKind)
        Timber.d("S1728: stream channel verdict=%s", verdict)
        onChannelReason(verdict.noticeReason())
        this.mediaKind = mediaKind
        return verdict !is StreamChannelVerdict.Refuse
    }

    fun canStartCurrentStream(): Boolean = mediaKind?.let(::prepare) ?: true

    fun withWideChannel() {
        val currentMediaKind = mediaKind ?: return
        if (sessionJob?.isActive == true) return
        sessionJob = scope.launch {
            launch {
                channelMonitor.channel.collect {
                    onChannelReason(evaluateStreamStart(currentMediaKind).noticeReason())
                }
            }
            networkHold.withWideChannel { awaitCancellation() }
        }
    }

    fun stop() {
        sessionJob?.cancel()
        sessionJob = null
    }

    fun clear() {
        stop()
        mediaKind = null
    }

    private fun StreamChannelVerdict.noticeReason(): StreamChannelReason? =
        reason?.takeUnless { it == StreamChannelReason.BANDWIDTH_UNKNOWN }
}

/** Creates ViewModel-owned stream sessions while sharing the app-level network collaborators. */
@Singleton
class StreamPlaybackSessionFactory @Inject constructor(
    private val networkHold: StreamNetworkHold,
    private val channelMonitor: WearNetworkChannelMonitor,
    private val evaluateStreamStart: EvaluateStreamStartUseCase
) {

    fun create(
        scope: CoroutineScope,
        onChannelReason: (StreamChannelReason?) -> Unit
    ): StreamPlaybackSessionManager = StreamPlaybackSessionManager(
        scope = scope,
        networkHold = networkHold,
        channelMonitor = channelMonitor,
        evaluateStreamStart = evaluateStreamStart,
        onChannelReason = onChannelReason
    )
}
