package com.sza.fastmediasorter.wear.service.helpers

import android.content.Context
import androidx.core.content.ContextCompat
import com.google.gson.JsonObject
import com.sza.fastmediasorter.data.security.e2e.E2eBytes
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.data.exchange.WearExchangeConnector
import com.sza.fastmediasorter.wear.data.exchange.WearExchangeControlEvent
import com.sza.fastmediasorter.wear.data.exchange.WearExchangeControlStreams
import com.sza.fastmediasorter.wear.data.exchange.WearExchangeE2e
import com.sza.fastmediasorter.wear.data.exchange.WearExchangeEntryStore
import com.sza.fastmediasorter.wear.data.exchange.WearExchangeEnvelope
import com.sza.fastmediasorter.wear.data.exchange.WearExchangeLoopbackBridge
import com.sza.fastmediasorter.wear.data.exchange.WearExchangeRelayEndpoint
import com.sza.fastmediasorter.wear.data.exchange.WearLoopbackPort
import com.sza.fastmediasorter.wear.data.exchange.WearSealedRelayProxy
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
import kotlinx.coroutines.flow.first
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
    private val entries: WearExchangeEntryStore,
    private val e2e: WearExchangeE2e,
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

    private suspend fun onControlEvent(event: WearExchangeControlEvent) {
        when (event.envelope.type) {
            WearExchangeEnvelope.TYPE_CAST_OFFER -> onOffer(event)
            WearExchangeEnvelope.TYPE_CAST_STOP ->
                event.envelope.string(KEY_BROADCAST_ID)?.let { perform(machine.onStopped(it)) }
        }
    }

    private suspend fun onOffer(event: WearExchangeControlEvent) {
        val endpoint = WearExchangeRelayEndpoint.fromOffer(event.envelope, openedDescriptor(event))
        val broadcastId = endpoint?.broadcastId ?: WearExchangeRelayEndpoint.broadcastIdOf(event.envelope)
        if (broadcastId == null) {
            Timber.w("WearExchangeCastCoordinator: a cast offer named no broadcast")
            return
        }
        endpoint?.let { endpoints[broadcastId] = it }
        val offer = WearExchangeCastOffer(event.identity, broadcastId, endpoint?.title)
        perform(machine.onOffer(offer, playable = endpoint != null, playAtOnce = false))
    }

    // S4240, 27 item DK: the offer's box opens with the keys the watch already trusts, as the phone opens a cast; a
    // box that does not open leaves the sealed relay keyless, and the offer unplayable.
    private suspend fun openedDescriptor(event: WearExchangeControlEvent): JsonObject? {
        val descriptor = WearExchangeRelayEndpoint.descriptorOf(event.envelope)
        val box = WearExchangeRelayEndpoint.boxOf(event.envelope)
        val broadcastId = WearExchangeRelayEndpoint.broadcastIdOf(event.envelope)
        val entry = entries.entries.first().firstOrNull { it.identity == event.identity }
        return if (box == null || broadcastId == null || entry == null) {
            descriptor
        } else {
            e2e.trustedSealing(entry).openBroadcast(broadcastId, box, descriptor)
        }
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
            loopbackFor(endpoint)
        } catch (e: IOException) {
            Timber.w(e, "WearExchangeCastCoordinator: the loopback proxy did not open")
            return
        }
        val url = endpoint.loopbackUrl(bridge.port)
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

    // 27 item DL: a sealed relay is no stream for any player, so it plays only through the opening proxy.
    private fun loopbackFor(endpoint: WearExchangeRelayEndpoint): WearLoopbackPort {
        val opener = { connector.connect(endpoint.host, endpoint.port, endpoint.certFingerprint) }
        val groupKey = endpoint.groupKey?.let(E2eBytes::fromB64url)
        val host = if (endpoint.port == HTTPS_PORT) endpoint.host else "${endpoint.host}:${endpoint.port}"
        return if (groupKey == null) {
            WearExchangeLoopbackBridge(LABEL, opener = opener)
        } else {
            WearSealedRelayProxy(LABEL, endpoint.path, host, endpoint.broadcastId, groupKey, opener = opener)
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

    private class Playing(val broadcastId: String, val url: String, val bridge: WearLoopbackPort) {
        var seen = false
    }

    private companion object {
        const val LABEL = "cast"
        const val HTTPS_PORT = 443
        const val KEY_BROADCAST_ID = "broadcastId"
        const val KEY_ACCEPTED = "accepted"
        const val KEY_REASON = "reason"
    }
}
