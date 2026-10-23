package com.sza.fastmediasorter.data.remote.exchange

import com.sza.fastmediasorter.core.di.IoDispatcher
import com.sza.fastmediasorter.data.remote.sftp.anywhere.ExchangeCertificateChangedException
import com.sza.fastmediasorter.data.remote.sftp.anywhere.ExchangeCertificateUntrustedException
import com.sza.fastmediasorter.domain.model.DeviceExchangeAccountEntry
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
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

/** A cast envelope (7.8) the server sent on the control stream of [entry]. */
data class ExchangeControlEvent(val entry: DeviceExchangeAccountEntry, val envelope: DeviceExchangeEnvelope)

/**
 * The receive-only control streams of the entries that are not the outgoing one (contract DEVICE-EXCHANGE item
 * BM: one stream per entry, each under its own token; item AC: every entry accepts casts). The outgoing entry's
 * stream belongs to the producer's tunnel and never runs here, or the two would replace each other on the server.
 * Each stream keeps the 7.3 keepalive window and reconnects with a growing pause; a missing token, a permanent
 * refusal or a refused certificate ends it until the entry changes. Releasing a stream says `bye`.
 *
 * A directory surface opens the same entry's control stream ([DeviceExchangeDirectoryWatcher]); it lends that
 * stream here while it lives, so casts keep arriving and answers keep going out on it instead of the two
 * streams taking the presence back from each other.
 */
@Singleton
class ExchangeReceiverControlStreams @Inject constructor(
    private val client: DeviceExchangeAccountClient,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    private val mutableEvents = MutableSharedFlow<ExchangeControlEvent>(extraBufferCapacity = EVENT_BUFFER)

    /** Every `cast-offer` and `cast-stop` read on one of these streams or on a lent one. */
    val events: SharedFlow<ExchangeControlEvent> = mutableEvents.asSharedFlow()

    private val sessions = ConcurrentHashMap<String, DeviceExchangeSession>()
    private val lent = ConcurrentHashMap<String, DeviceExchangeSession>()
    private val lentIds = MutableStateFlow<Set<String>>(emptySet())

    /** Holds a stream for every entry [entries] lists; returns only when the caller is cancelled. */
    suspend fun run(entries: Flow<List<DeviceExchangeAccountEntry>>) = coroutineScope {
        val loops = mutableMapOf<String, Pair<StreamKey, Job>>()
        try {
            entries.collect { wanted ->
                reconcile(this, loops, wanted.associateBy(DeviceExchangeAccountEntry::identity))
            }
        } finally {
            // A blocked read ignores cancellation; closing the socket is what lets each loop end.
            loops.keys.forEach(::release)
        }
    }

    /** Sends on the stream of [identity], a lent one first; false when there is none or the write failed. */
    fun send(identity: String, envelope: DeviceExchangeEnvelope): Boolean =
        (lent[identity] ?: sessions[identity])?.let { sendQuietly(it, envelope) } ?: false

    /**
     * [session] is [entry]'s control stream for now: this entry's own stream steps aside without a `bye`, because
     * the lent one already replaced it on the server, and reconnects only after [giveBack].
     */
    fun lend(entry: DeviceExchangeAccountEntry, session: DeviceExchangeSession) {
        lent[entry.identity] = session
        lentIds.update { it + entry.identity }
        sessions.remove(entry.identity)?.close()
    }

    fun giveBack(entry: DeviceExchangeAccountEntry, session: DeviceExchangeSession) {
        if (lent.remove(entry.identity, session)) lentIds.update { it - entry.identity }
    }

    /** An envelope read on a lent stream; only the cast messages travel on. */
    fun deliver(entry: DeviceExchangeAccountEntry, envelope: DeviceExchangeEnvelope) {
        if (envelope.type in CAST_TYPES) mutableEvents.tryEmit(ExchangeControlEvent(entry, envelope))
    }

    private fun reconcile(
        scope: CoroutineScope,
        loops: MutableMap<String, Pair<StreamKey, Job>>,
        wanted: Map<String, DeviceExchangeAccountEntry>,
    ) {
        loops.entries.removeAll { (identity, running) ->
            val stale = wanted[identity]?.let(::StreamKey) != running.first
            if (stale) {
                release(identity)
                running.second.cancel()
            }
            stale
        }
        wanted.forEach { (identity, entry) ->
            if (identity !in loops) loops[identity] = StreamKey(entry) to scope.launch(ioDispatcher) { loop(entry) }
        }
    }

    private suspend fun loop(entry: DeviceExchangeAccountEntry) {
        var backoffMs = INITIAL_BACKOFF_MS
        var outcome = Outcome.RETRY
        while (currentCoroutineContext().isActive && outcome != Outcome.TERMINAL) {
            lentIds.first { entry.identity !in it }
            outcome = attempt(entry)
            if (outcome != Outcome.TERMINAL) {
                if (outcome == Outcome.WELCOMED) backoffMs = INITIAL_BACKOFF_MS
                delay(backoffMs)
                backoffMs = (backoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
            }
        }
    }

    private suspend fun attempt(entry: DeviceExchangeAccountEntry): Outcome {
        val session = try {
            client.openAuthenticated(entry, KEEPALIVE_SECONDS)
        } catch (e: IOException) {
            return openOutcome(e)
        }
        // Released while the connect was in flight: the read below would otherwise block on a stream nobody wants.
        if (!currentCoroutineContext().isActive) session.close()
        return serve(entry, session)
    }

    private fun openOutcome(failure: IOException): Outcome = when (failure) {
        is DeviceExchangeNotEnrolledException -> Outcome.TERMINAL
        is DeviceExchangeRefusedException -> {
            Timber.w("ExchangeReceiverControlStreams: refused (%s)", failure.reason)
            if (failure.reason in PERMANENT_REFUSALS) Outcome.TERMINAL else Outcome.RETRY
        }
        is ExchangeCertificateUntrustedException, is ExchangeCertificateChangedException -> {
            Timber.w(failure, "ExchangeReceiverControlStreams: certificate refused")
            Outcome.TERMINAL
        }
        else -> {
            Timber.i(failure, "ExchangeReceiverControlStreams: exchange server unreachable")
            Outcome.RETRY
        }
    }

    private suspend fun serve(entry: DeviceExchangeAccountEntry, session: DeviceExchangeSession): Outcome =
        coroutineScope {
            sessions[entry.identity] = session
            Timber.d("S4421: receive-only control stream welcomed for a non-outgoing entry")
            val lastHeard = AtomicLong(nowMs())
            val intervalMs = (session.welcome.keepaliveSeconds ?: KEEPALIVE_SECONDS).toLong() * MILLIS_PER_SECOND
            val keepalive = launch { keepalive(session, intervalMs, lastHeard) }
            val outcome = try {
                receiveAll(entry, session, lastHeard)
            } finally {
                keepalive.cancel()
                sessions.remove(entry.identity, session)
                session.close()
            }
            outcome
        }

    // Blocking reads; closing the session from release(), lend() or the keepalive is what ends this loop.
    private fun receiveAll(
        entry: DeviceExchangeAccountEntry,
        session: DeviceExchangeSession,
        lastHeard: AtomicLong,
    ): Outcome {
        var outcome = Outcome.WELCOMED
        try {
            var envelope = session.receive()
            while (envelope != null) {
                lastHeard.set(nowMs())
                if (envelope.type == DeviceExchangeEnvelope.TYPE_REFUSED) {
                    val reason = envelope.string(DeviceExchangeEnvelope.KEY_REASON)
                    Timber.w("ExchangeReceiverControlStreams: stream refused (%s)", reason)
                    if (reason in PERMANENT_REFUSALS) outcome = Outcome.TERMINAL
                    envelope = null
                } else {
                    deliver(entry, envelope)
                    envelope = session.receive()
                }
            }
        } catch (e: IOException) {
            Timber.i(e, "ExchangeReceiverControlStreams: control stream ended")
        }
        return outcome
    }

    // The server answers every keepalive, so three silent intervals mean a dead stream (7.3).
    private suspend fun keepalive(session: DeviceExchangeSession, intervalMs: Long, lastHeard: AtomicLong) {
        var alive = true
        while (alive) {
            delay(intervalMs)
            val silentForMs = nowMs() - lastHeard.get()
            alive = silentForMs <= intervalMs * KEEPALIVE_WINDOW && sendQuietly(session, KEEPALIVE)
        }
        Timber.i("ExchangeReceiverControlStreams: keepalive window missed or write failed")
        session.close()
    }

    private fun release(identity: String) {
        val session = sessions.remove(identity) ?: return
        sendQuietly(session, BYE)
        session.close()
    }

    private fun sendQuietly(session: DeviceExchangeSession, envelope: DeviceExchangeEnvelope): Boolean = try {
        session.send(envelope)
        true
    } catch (e: IOException) {
        Timber.i(e, "ExchangeReceiverControlStreams: %s write failed", envelope.type)
        false
    }

    private fun nowMs(): Long = TimeUnit.NANOSECONDS.toMillis(System.nanoTime())

    // What reaches the server: an entry whose welcome merely refreshed a remembered field keeps its stream.
    private data class StreamKey(val identity: String, val deviceId: String, val pinnedCertificate: String?) {
        constructor(entry: DeviceExchangeAccountEntry) : this(entry.identity, entry.deviceId, entry.pinnedCertificate)
    }

    private enum class Outcome { WELCOMED, RETRY, TERMINAL }

    private companion object {
        const val KEEPALIVE_SECONDS = 30
        const val KEEPALIVE_WINDOW = 3
        const val INITIAL_BACKOFF_MS = 2_000L
        const val MAX_BACKOFF_MS = 120_000L
        const val MILLIS_PER_SECOND = 1_000L
        const val EVENT_BUFFER = 16
        val CAST_TYPES = setOf(DeviceExchangeEnvelope.TYPE_CAST_OFFER, DeviceExchangeEnvelope.TYPE_CAST_STOP)
        val KEEPALIVE = DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_KEEPALIVE)
        val BYE = DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_BYE)
        val PERMANENT_REFUSALS = setOf(
            DeviceExchangeEnvelope.REASON_BAD_CREDENTIALS,
            DeviceExchangeEnvelope.REASON_DEVICE_REVOKED,
            DeviceExchangeEnvelope.REASON_TLS_REQUIRED,
            DeviceExchangeEnvelope.REASON_VERSION_UNSUPPORTED,
            DeviceExchangeEnvelope.REASON_ID_COLLISION,
        )
    }
}
