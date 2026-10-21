package com.sza.fastmediasorter.wear.data.exchange

import com.sza.fastmediasorter.wear.domain.model.WearExchangeMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/** A control stream that is up; a new [generation] for the same entry means it reconnected. */
data class WearExchangeLiveStream(
    val identity: String,
    val generation: Long,
    val publicEndpoint: String?,
)

/** One envelope the server sent on the control stream of the entry named by [identity]. */
data class WearExchangeControlEvent(val identity: String, val envelope: WearExchangeEnvelope)

/**
 * The watch's control streams (contract DEVICE-EXCHANGE 7.3): one per exchange entry while [run]'s `wanted` says
 * so, because a cast can arrive on any of them and a broadcast is announced on the outgoing one. Each stream sends
 * a keepalive every interval, closes after a missed window and reconnects with a growing pause; a missing token or
 * a permanent refusal ends it until the entry changes. Releasing a stream says `bye`, so the device goes offline
 * at once instead of after the window.
 */
@Singleton
class WearExchangeControlStreams @Inject constructor(
    private val store: WearExchangeEntryStore,
    private val opener: WearExchangeSessionOpener,
) {

    private val mutableEvents = MutableSharedFlow<WearExchangeControlEvent>(extraBufferCapacity = EVENT_BUFFER)
    val events: SharedFlow<WearExchangeControlEvent> = mutableEvents.asSharedFlow()

    private val mutableLive = MutableStateFlow<Map<String, WearExchangeLiveStream>>(emptyMap())
    val live: StateFlow<Map<String, WearExchangeLiveStream>> = mutableLive.asStateFlow()

    private val mutableFailures = MutableStateFlow<Map<String, WearExchangeMessage>>(emptyMap())

    /** The last failure of each entry's stream, cleared once it is served again; the account list shows it. */
    val failures: StateFlow<Map<String, WearExchangeMessage>> = mutableFailures.asStateFlow()

    private val sessions = ConcurrentHashMap<String, WearExchangeSession>()
    private val generations = AtomicLong()

    /** Holds a stream for every entry while [wanted] is true; returns only when the caller is cancelled. */
    suspend fun run(wanted: Flow<Boolean>) = coroutineScope {
        val loops = mutableMapOf<String, Pair<WearExchangeEntry, Job>>()
        try {
            combine(wanted, store.entries) { isWanted, entries -> if (isWanted) entries else emptyList() }
                .collect { entries -> reconcile(this, loops, entries.associateBy(WearExchangeEntry::identity)) }
        } finally {
            // A blocked read ignores cancellation; closing the socket is what lets each loop end.
            loops.keys.forEach(::release)
        }
    }

    /** Sends on the live stream of [identity]; false when there is none or the write failed. */
    fun send(identity: String, envelope: WearExchangeEnvelope): Boolean =
        sessions[identity]?.let { sendQuietly(it, envelope) } ?: false

    // The outgoing mark is not a connection property: moving it must not drop the stream a cast may be on.
    private fun reconcile(
        scope: CoroutineScope,
        loops: MutableMap<String, Pair<WearExchangeEntry, Job>>,
        wantedEntries: Map<String, WearExchangeEntry>,
    ) {
        loops.entries.removeAll { (identity, running) ->
            val stale = wantedEntries[identity]?.copy(outgoing = false) != running.first.copy(outgoing = false)
            if (stale) {
                release(identity)
                running.second.cancel()
            }
            stale
        }
        wantedEntries.forEach { (identity, entry) ->
            if (identity !in loops) loops[identity] = entry to scope.launch(Dispatchers.IO) { loop(entry) }
        }
    }

    private suspend fun loop(entry: WearExchangeEntry) {
        var backoffMs = WearExchangeKeepalive.INITIAL_BACKOFF_MS
        var outcome = Outcome.RETRY
        while (currentCoroutineContext().isActive && outcome != Outcome.TERMINAL) {
            outcome = attempt(entry)
            if (outcome != Outcome.TERMINAL) {
                if (outcome == Outcome.WELCOMED) backoffMs = WearExchangeKeepalive.INITIAL_BACKOFF_MS
                delay(backoffMs)
                backoffMs = WearExchangeKeepalive.nextBackoffMs(backoffMs)
            }
        }
    }

    private suspend fun attempt(entry: WearExchangeEntry): Outcome {
        val session = try {
            opener.open(entry, WearExchangeKeepalive.KEEPALIVE_SECONDS, WearExchangeConnector.NO_READ_TIMEOUT)
        } catch (e: IOException) {
            return openFailure(entry.identity, e)
        }
        // Released while the connect was in flight: the read below would otherwise block on a stream nobody wants.
        if (!currentCoroutineContext().isActive) session.close()
        return serve(entry.identity, session)
    }

    private fun openFailure(identity: String, failure: IOException): Outcome {
        val message = WearExchangeFailureClassifier.of(failure)
        mutableFailures.update { it + (identity to message) }
        Timber.i("WearExchangeControlStreams: stream did not open, code=%s", message.code)
        Timber.d("S4236: watch stream failure classified code=${message.code}")
        return openOutcome(failure)
    }

    private fun openOutcome(failure: IOException): Outcome = when (failure) {
        is WearExchangeNotEnrolledException -> Outcome.TERMINAL
        is WearExchangeRefusedException -> {
            Timber.w("WearExchangeControlStreams: refused (%s)", failure.reason)
            if (failure.reason in WearExchangeKeepalive.PERMANENT_REFUSALS) Outcome.TERMINAL else Outcome.RETRY
        }
        is WearExchangeCertificateUntrustedException, is WearExchangeCertificateChangedException -> {
            Timber.w(failure, "WearExchangeControlStreams: certificate refused")
            Outcome.TERMINAL
        }
        else -> {
            Timber.i(failure, "WearExchangeControlStreams: exchange server unreachable")
            Outcome.RETRY
        }
    }

    private suspend fun serve(identity: String, session: WearExchangeSession): Outcome = coroutineScope {
        val generation = generations.incrementAndGet()
        sessions[identity] = session
        val stream = WearExchangeLiveStream(identity, generation, session.welcome.publicEndpoint)
        mutableLive.update { it + (identity to stream) }
        mutableFailures.update { it - identity }
        val lastHeard = AtomicLong(nowMs())
        val intervalMs = WearExchangeKeepalive.intervalMs(session.welcome.keepaliveSeconds)
        val keepalive = launch { keepalive(session, intervalMs, lastHeard) }
        try {
            receiveAll(identity, session, lastHeard)
        } finally {
            keepalive.cancel()
            sessions.remove(identity, session)
            mutableLive.update { current ->
                if (current[identity]?.generation == generation) current - identity else current
            }
            session.close()
        }
        Outcome.WELCOMED
    }

    // Blocking reads; closing the session from release() or the keepalive is what ends this loop.
    private fun receiveAll(identity: String, session: WearExchangeSession, lastHeard: AtomicLong) {
        try {
            var envelope = session.receive()
            while (envelope != null) {
                lastHeard.set(nowMs())
                if (envelope.type != WearExchangeEnvelope.TYPE_KEEPALIVE) {
                    mutableEvents.tryEmit(WearExchangeControlEvent(identity, envelope))
                }
                envelope = session.receive()
            }
        } catch (e: IOException) {
            Timber.i(e, "WearExchangeControlStreams: control stream ended")
        }
    }

    private suspend fun keepalive(session: WearExchangeSession, intervalMs: Long, lastHeard: AtomicLong) {
        var alive = true
        while (alive) {
            delay(intervalMs)
            val silentForMs = nowMs() - lastHeard.get()
            alive = !WearExchangeKeepalive.isDead(silentForMs, intervalMs) && sendQuietly(session, KEEPALIVE)
        }
        Timber.i("WearExchangeControlStreams: keepalive window missed or write failed")
        session.close()
    }

    private fun release(identity: String) {
        val session = sessions.remove(identity) ?: return
        sendQuietly(session, BYE)
        session.close()
    }

    private fun sendQuietly(session: WearExchangeSession, envelope: WearExchangeEnvelope): Boolean = try {
        session.send(envelope)
        true
    } catch (e: IOException) {
        Timber.i(e, "WearExchangeControlStreams: %s write failed", envelope.type)
        false
    }

    private fun nowMs(): Long = TimeUnit.NANOSECONDS.toMillis(System.nanoTime())

    private enum class Outcome { WELCOMED, RETRY, TERMINAL }

    private companion object {
        const val EVENT_BUFFER = 16
        val KEEPALIVE = WearExchangeEnvelope.of(WearExchangeEnvelope.TYPE_KEEPALIVE)
        val BYE = WearExchangeEnvelope.of(WearExchangeEnvelope.TYPE_BYE)
    }
}
