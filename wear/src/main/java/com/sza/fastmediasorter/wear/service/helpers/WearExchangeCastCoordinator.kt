package com.sza.fastmediasorter.wear.service.helpers

import android.content.Context
import androidx.core.content.ContextCompat
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.data.exchange.WearExchangeConnector
import com.sza.fastmediasorter.wear.data.exchange.WearExchangeControlEvent
import com.sza.fastmediasorter.wear.data.exchange.WearExchangeControlStreams
import com.sza.fastmediasorter.wear.data.exchange.WearExchangeEnvelope
import com.sza.fastmediasorter.wear.data.exchange.WearExchangeLoopbackBridge
import com.sza.fastmediasorter.wear.data.exchange.WearExchangeRelayEndpoint
import com.sza.fastmediasorter.wear.di.ApplicationScope
import com.sza.fastmediasorter.wear.domain.exchange.WearExchangeCastAction
import com.sza.fastmediasorter.wear.domain.exchange.WearExchangeCastMachine
import com.sza.fastmediasorter.wear.domain.exchange.WearExchangeCastOffer
import com.sza.fastmediasorter.wear.domain.playback.WearBackgroundSession
import com.sza.fastmediasorter.wear.domain.playback.WearBackgroundSessionState
import com.sza.fastmediasorter.wear.domain.usecase.ClassifyWearStreamMediaKindUseCase
import com.sza.fastmediasorter.wear.service.WearPlaybackService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The watch's receiver half of cast (contract DEVICE-EXCHANGE 7.8): reads `cast-offer` and `cast-stop` off every
 * control stream, lets [WearExchangeCastMachine] decide, and acts - answers on the stream the offer came on, asks
 * through [WearExchangeCastNotifier], plays the `RELAY` endpoint through a pinning loopback proxy in the playback
 * service. Every machine call holds [mutex], because offers arrive on the stream collector and answers on the
 * notification receiver.
 */
@Singleton
class WearExchangeCastCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val streams: WearExchangeControlStreams,
    private val connector: WearExchangeConnector,
    private val notifier: WearExchangeCastNotifier,
    private val playbackSession: WearBackgroundSessionState,
    @ApplicationScope private val scope: CoroutineScope,
) {

    private val mutex = Mutex()
    private val machine = WearExchangeCastMachine()
    private val endpoints = mutableMapOf<String, WearExchangeRelayEndpoint>()
    private val expiries = mutableMapOf<String, Job>()
    private var current: Playing? = null

    private val mutablePlaying = MutableStateFlow(false)

    /** True while a cast this watch accepted is playing; the control streams stay up for its `cast-stop`. */
    val playing: StateFlow<Boolean> = mutablePlaying.asStateFlow()

    suspend fun follow() = coroutineScope {
        launch { streams.events.collect { event -> mutex.withLock { onControlEvent(event) } } }
        launch { playbackSession.session.collect { session -> mutex.withLock { onPlaybackSession(session) } } }
    }

    /** The owner's Listen or Decline from the notification. */
    fun onUserAnswer(broadcastId: String, accepted: Boolean) {
        scope.launch { mutex.withLock { perform(machine.onUserAnswer(broadcastId, accepted)) } }
    }

    private fun onControlEvent(event: WearExchangeControlEvent) {
        when (event.envelope.type) {
            WearExchangeEnvelope.TYPE_CAST_OFFER -> onOffer(event)
            WearExchangeEnvelope.TYPE_CAST_STOP ->
                event.envelope.string(KEY_BROADCAST_ID)?.let { perform(machine.onStopped(it)) }
        }
    }

    private fun onOffer(event: WearExchangeControlEvent) {
        Timber.d("S4194: cast offer received on a control stream")
        val endpoint = WearExchangeRelayEndpoint.fromOffer(event.envelope)
        val broadcastId = endpoint?.broadcastId
            ?: event.envelope.string(KEY_BROADCAST_ID)
            ?: event.envelope.obj(KEY_BROADCAST)?.get(KEY_BROADCAST_ID)?.takeIf { it.isJsonPrimitive }?.asString
        if (broadcastId == null) {
            Timber.w("WearExchangeCastCoordinator: a cast offer named no broadcast")
            return
        }
        endpoint?.let { endpoints[broadcastId] = it }
        val offer = WearExchangeCastOffer(event.identity, broadcastId, endpoint?.title)
        perform(machine.onOffer(offer, playable = endpoint != null, playAtOnce = false))
    }

    // The service reports what it plays; once it stopped playing this cast - the owner's stop, the stream's end -
    // the cast is over here too, and the proxy and the control streams it kept up are released.
    private fun onPlaybackSession(session: WearBackgroundSession?) {
        val playing = current ?: return
        if (session?.mediaUri == playing.url) {
            playing.seen = true
        } else if (playing.seen) {
            perform(machine.onStopped(playing.broadcastId))
        }
    }

    private fun perform(actions: List<WearExchangeCastAction>) {
        actions.forEach { action ->
            when (action) {
                is WearExchangeCastAction.Answer -> answer(action)
                is WearExchangeCastAction.Ask -> ask(action.offer)
                is WearExchangeCastAction.Play -> play(action.offer)
                is WearExchangeCastAction.Dismiss -> dismiss(action.broadcastId)
                is WearExchangeCastAction.StopPlaying -> stopPlaying(action.broadcastId)
            }
        }
    }

    private fun answer(action: WearExchangeCastAction.Answer) {
        val envelope = WearExchangeEnvelope.of(WearExchangeEnvelope.TYPE_CAST_ANSWER) {
            addProperty(KEY_BROADCAST_ID, action.offer.broadcastId)
            addProperty(KEY_ACCEPTED, action.accepted)
            action.reason?.let { addProperty(KEY_REASON, it) }
        }
        if (!streams.send(action.offer.identity, envelope)) {
            Timber.i("WearExchangeCastCoordinator: the answer found no live stream; the server answers timeout")
        }
        if (!action.accepted) endpoints.remove(action.offer.broadcastId)
    }

    private fun ask(offer: WearExchangeCastOffer) {
        val id = offer.broadcastId
        if (notifier.ask(id, offer.title)) {
            expiries.remove(id)?.cancel()
            expiries[id] = scope.launch {
                delay(WearExchangeCastNotifier.ANSWER_WINDOW_MS)
                mutex.withLock {
                    perform(machine.onExpired(id))
                    if (current?.broadcastId != id) endpoints.remove(id)
                }
            }
        } else {
            Timber.i("WearExchangeCastCoordinator: notifications are off, the offer is declined")
            perform(machine.onUserAnswer(id, accepted = false))
        }
    }

    // The endpoint stays: an accepted answer dismisses the question before it plays.
    private fun dismiss(broadcastId: String) {
        expiries.remove(broadcastId)?.cancel()
        notifier.cancel()
    }

    private fun play(offer: WearExchangeCastOffer) {
        val endpoint = endpoints[offer.broadcastId] ?: return
        current?.let { stopPlaying(it.broadcastId) }
        val bridge = try {
            WearExchangeLoopbackBridge(LABEL) {
                connector.connect(endpoint.host, endpoint.port, endpoint.certFingerprint)
            }
        } catch (e: IOException) {
            Timber.w(e, "WearExchangeCastCoordinator: the loopback proxy did not open")
            return
        }
        val url = endpoint.loopbackUrl(bridge.port)
        Timber.d("S4194: cast playback starting through the pinning loopback proxy")
        current = Playing(offer.broadcastId, url, bridge)
        mutablePlaying.value = true
        val intent = WearPlaybackService.startIntent(
            context = context,
            fileId = WearPlaybackService.NO_FILE_ID,
            mediaUri = url,
            positionMs = 0L,
            streamMediaKind = ClassifyWearStreamMediaKindUseCase.AUDIO,
            title = offer.title ?: context.getString(R.string.wear_exchange_cast_title),
        )
        try {
            ContextCompat.startForegroundService(context, intent)
        } catch (e: IllegalStateException) {
            // Outside the notification's start allowance the platform refuses; nothing plays, so nothing is kept.
            Timber.w(e, "WearExchangeCastCoordinator: the playback service was refused")
            perform(machine.onStopped(offer.broadcastId))
        }
    }

    private fun stopPlaying(broadcastId: String) {
        val playing = current?.takeIf { it.broadcastId == broadcastId } ?: return
        current = null
        mutablePlaying.value = false
        endpoints.remove(broadcastId)
        playing.bridge.close()
        if (playbackSession.session.value?.mediaUri == playing.url) {
            try {
                context.startService(WearPlaybackService.stopIntent(context))
            } catch (e: IllegalStateException) {
                Timber.w(e, "WearExchangeCastCoordinator: the playback service could not be told to stop")
            }
        }
    }

    private class Playing(val broadcastId: String, val url: String, val bridge: WearExchangeLoopbackBridge) {
        var seen = false
    }

    private companion object {
        const val LABEL = "cast"
        const val KEY_BROADCAST = "broadcast"
        const val KEY_BROADCAST_ID = "broadcastId"
        const val KEY_ACCEPTED = "accepted"
        const val KEY_REASON = "reason"
    }
}
