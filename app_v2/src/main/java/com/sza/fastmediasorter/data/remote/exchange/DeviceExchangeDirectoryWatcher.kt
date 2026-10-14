package com.sza.fastmediasorter.data.remote.exchange

import com.sza.fastmediasorter.core.di.IoDispatcher
import com.sza.fastmediasorter.domain.model.DeviceExchangeAccountEntry
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The live directory of one account entry (contract DEVICE-EXCHANGE 7.4): `list`, then `subscribe`, then every
 * `changed` push applied by revision, with `list` sent again on a gap.
 *
 * The stream it opens is the device's control stream (7.2), so for the outgoing entry it replaces the producer's
 * for as long as it lives, exactly as a one-shot `list` does; the producer's reconnect takes it back and this
 * flow then ends with the last directory it emitted. It is collected only while a directory surface is open.
 */
@Singleton
class DeviceExchangeDirectoryWatcher @Inject constructor(
    private val client: DeviceExchangeAccountClient,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    /**
     * Emits the whole directory on every change. Ends when the server closes the stream.
     *
     * @throws DeviceExchangeRefusedException when the server refused the token or a request.
     * @throws IOException when the server cannot be reached or the stream breaks.
     */
    fun watch(entry: DeviceExchangeAccountEntry): Flow<DeviceExchangeDirectory> = channelFlow {
        val session = client.openAuthenticated(entry, KEEPALIVE_SECONDS)
        val intervalMs = (session.welcome.keepaliveSeconds ?: KEEPALIVE_SECONDS).toLong() * MILLIS_PER_SECOND
        val keepalive = launch { keepalive(session, intervalMs) }
        launch {
            read(session, channel)
            keepalive.cancel()
            channel.close()
        }
        // A blocked socket read ignores cancellation; closing the stream is what releases it.
        awaitClose { session.close() }
    }.flowOn(ioDispatcher)

    // The server answers every keepalive (7.3); [read] drops the answers.
    private suspend fun keepalive(session: DeviceExchangeSession, intervalMs: Long) {
        while (true) {
            delay(intervalMs)
            session.send(KEEPALIVE)
        }
    }

    private suspend fun read(session: DeviceExchangeSession, out: SendChannel<DeviceExchangeDirectory>) {
        session.send(LIST)
        session.send(SUBSCRIBE)
        var state: DeviceExchangeDirectoryState? = null
        var envelope = session.receive()
        while (envelope != null) {
            when (envelope.type) {
                DeviceExchangeEnvelope.TYPE_DIRECTORY -> {
                    val fresh = DeviceExchangeDirectoryState.of(DeviceExchangeDirectory.from(envelope))
                    state = fresh
                    out.send(fresh.directory)
                }
                DeviceExchangeEnvelope.TYPE_CHANGED -> state = onChanged(session, state, envelope, out)
                DeviceExchangeEnvelope.TYPE_REFUSED -> throw DeviceExchangeRefusedException(
                    envelope.string(DeviceExchangeEnvelope.KEY_REASON) ?: DeviceExchangeAccountClient.REASON_UNKNOWN
                )
                else -> Unit
            }
            envelope = session.receive()
        }
        Timber.i("DeviceExchangeDirectoryWatcher: the server closed the directory stream")
    }

    private suspend fun onChanged(
        session: DeviceExchangeSession,
        held: DeviceExchangeDirectoryState?,
        envelope: DeviceExchangeEnvelope,
        out: SendChannel<DeviceExchangeDirectory>,
    ): DeviceExchangeDirectoryState? {
        // A push that arrives before the first answer is covered by that answer.
        if (held == null) return null
        return when (val outcome = held.apply(DeviceExchangeChanged.from(envelope))) {
            is DeviceExchangeDirectoryState.Outcome.Applied -> outcome.state.also { out.send(it.directory) }
            DeviceExchangeDirectoryState.Outcome.Gap -> {
                session.send(LIST)
                held
            }
            DeviceExchangeDirectoryState.Outcome.Stale -> held
        }
    }

    companion object {
        const val KEEPALIVE_SECONDS = 30
        private const val MILLIS_PER_SECOND = 1_000L
        private val LIST = DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_LIST)
        private val SUBSCRIBE = DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_SUBSCRIBE)
        private val KEEPALIVE = DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_KEEPALIVE)
    }
}
