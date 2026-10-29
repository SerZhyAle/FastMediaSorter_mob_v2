package com.sza.fastmediasorter.data.broadcast.exchange

import com.sza.fastmediasorter.core.di.ApplicationScope
import com.sza.fastmediasorter.core.di.IoDispatcher
import com.sza.fastmediasorter.data.broadcast.auth.BroadcastCredentialRegistry
import com.sza.fastmediasorter.data.broadcast.auth.BroadcastDigest
import com.sza.fastmediasorter.data.remote.exchange.DeviceExchangeAccountClient
import com.sza.fastmediasorter.data.remote.exchange.DeviceExchangeEnvelope
import com.sza.fastmediasorter.data.remote.exchange.DeviceExchangeSession
import com.sza.fastmediasorter.data.security.e2e.E2eRelayRecords
import com.sza.fastmediasorter.domain.model.DeviceExchangeAccountEntry
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.io.Closeable
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The broadcaster's upload to the fan-out relay of contract DEVICE-EXCHANGE 7.6. For `AUDIO_ONLY` it listens to the
 * broadcast's own LAN HTTP stream on loopback - one encoder output feeds the LAN and the relay - and forwards whole
 * ADTS frames as joinable chunks, taking one LAN listener slot as the contract counts it. For video it forwards the
 * MPEG-TS the encoder session muxes into [ExchangeVideoRelayFeed], its PAT and PMT as the header chunk, starting at
 * the first keyframe.
 *
 * [accepted] names the broadcast whose upload the server accepted, so the descriptor lists `RELAY` only while an
 * upload actually runs; a dropped upload is retried until [stop] or [finish].
 */
@Singleton
class ExchangeRelayUploader @Inject constructor(
    private val client: DeviceExchangeAccountClient,
    private val keys: ExchangeRelayKeys,
    @ApplicationScope private val appScope: CoroutineScope,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val credentials: BroadcastCredentialRegistry,
) {

    private val mutableAccepted = MutableStateFlow<String?>(null)
    val accepted: StateFlow<String?> = mutableAccepted.asStateFlow()

    private var job: Job? = null
    private var activeGeneration: Generation? = null

    private inner class Generation(val broadcastId: String) {
        private val lock = Any()
        private val streams = mutableSetOf<Closeable>()
        private var stopped = false
        private var finishing = false

        fun registerOrClose(stream: Closeable): Boolean = synchronized(lock) {
            if (stopped) {
                closeQuietly(stream)
                false
            } else {
                streams.add(stream)
                true
            }
        }

        fun unregister(stream: Closeable) {
            synchronized(lock) {
                streams.remove(stream)
            }
        }

        fun stop(): List<Closeable> = synchronized(lock) {
            stopped = true
            streams.toList().also { streams.clear() }
        }

        val isStopped: Boolean
            get() = synchronized(lock) { stopped }

        /** Set by [finish]: the upload leaves at the next chunk boundary and ends with the zero-length chunk. */
        val isFinishing: Boolean
            get() = synchronized(lock) { finishing }

        fun beginFinish() = synchronized(lock) { finishing = true }

        /** Neither stopped nor finishing: the upload keeps sending and reconnects a dropped stream. */
        val isRunning: Boolean
            get() = synchronized(lock) { !stopped && !finishing }
    }

    /** Uploads the ADTS stream served at [sourceUrl] as broadcast [broadcastId] through [entry]. */
    @Synchronized
    fun start(entry: DeviceExchangeAccountEntry, broadcastId: String, sourceUrl: String) {
        stopLocked()
        val generation = Generation(broadcastId)
        activeGeneration = generation
        job = appScope.launch(ioDispatcher) { uploadUntilStopped(generation, entry, Source.Adts(sourceUrl)) }
    }

    /** Uploads the MPEG-TS the video session publishes into [feed] as broadcast [broadcastId] through [entry]. */
    @Synchronized
    fun startVideo(entry: DeviceExchangeAccountEntry, broadcastId: String, feed: ExchangeVideoRelayFeed) {
        stopLocked()
        val generation = Generation(broadcastId)
        activeGeneration = generation
        job = appScope.launch(ioDispatcher) { uploadUntilStopped(generation, entry, Source.Ts(feed)) }
    }

    @Synchronized
    fun stop() = stopLocked()

    /**
     * Ends the upload cleanly (contract DEVICE-EXCHANGE item DX): the zero-length chunk of 7.6 goes out at the next
     * chunk boundary, before the data stream closes. Waits at most [timeoutMs] for it, then stops as [stop] does.
     */
    fun finish(timeoutMs: Long) {
        val running = synchronized(this) {
            activeGeneration?.beginFinish()
            job
        }
        running?.let { upload -> runBlocking { withTimeoutOrNull(timeoutMs) { upload.join() } } }
        stop()
    }

    private fun stopLocked() {
        job?.cancel()
        job = null
        val oldGen = activeGeneration
        activeGeneration = null
        oldGen?.stop()?.forEach(::closeQuietly)
        mutableAccepted.value = null
    }

    @Synchronized
    private fun publishAccepted(generation: Generation) {
        if (activeGeneration === generation && !generation.isStopped) {
            mutableAccepted.value = generation.broadcastId
        }
    }

    @Synchronized
    private fun clearAccepted(generation: Generation) {
        if (activeGeneration === generation) {
            mutableAccepted.value = null
        }
    }

    private suspend fun uploadUntilStopped(
        generation: Generation,
        entry: DeviceExchangeAccountEntry,
        source: Source
    ) {
        var backoffMs = INITIAL_BACKOFF_MS
        var refused = false
        try {
            while (currentCoroutineContext().isActive && !refused && generation.isRunning) {
                try {
                    refused = !upload(generation, entry, source)
                    backoffMs = INITIAL_BACKOFF_MS
                } catch (e: IOException) {
                    Timber.i(e, "ExchangeRelayUploader: upload ended")
                } finally {
                    clearAccepted(generation)
                }
                if (refused || !currentCoroutineContext().isActive || !generation.isRunning) break
                delay(backoffMs)
                backoffMs = (backoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
            }
        } finally {
            clearAccepted(generation)
            generation.stop().forEach(::closeQuietly)
        }
    }

    /** False when the server refused the upload - a deployment without the relay - which no retry changes. */
    private suspend fun upload(
        generation: Generation,
        entry: DeviceExchangeAccountEntry,
        source: Source
    ): Boolean {
        val header = (source as? Source.Ts)?.feed?.header?.filterNotNull()?.first()
        if (generation.isStopped || !currentCoroutineContext().isActive) {
            return false
        }
        val session = client.openAuthenticated(entry)
        return uploadSession(generation, session, source, header)
    }

    private suspend fun uploadSession(
        generation: Generation,
        session: DeviceExchangeSession,
        source: Source,
        header: ByteArray?
    ): Boolean {
        if (!generation.registerOrClose(session)) {
            return false
        }
        var accepted = false
        try {
            if (!generation.isStopped && currentCoroutineContext().isActive) {
                val features = session.welcome.features
                val sealing = features?.contains(DeviceExchangeAccountEntry.FEATURE_SEALED) == true
                val ring = keys.newEpoch(generation.broadcastId)?.takeIf { sealing }
                accepted = ring != null && accepted(session, generation.broadcastId)
                if (ring != null && accepted) {
                    publishAccepted(generation)
                    val sealer = ExchangeRelaySealer(generation.broadcastId, source.contentType, ring) {
                        keys.isCurrent(generation.broadcastId, ring)
                    }
                    val headerChunk = sealer.headerChunk(header)
                    val writer = ExchangeRelayChunkWriter(session.connection.output, headerChunk, sealer)
                    when (source) {
                        is Source.Adts -> forward(generation, source.url, writer)
                        is Source.Ts -> pump(generation, source.feed, writer)
                    }
                    writer.end()
                }
            }
        } finally {
            generation.unregister(session)
            closeQuietly(session)
        }
        return accepted
    }

    private fun accepted(session: DeviceExchangeSession, broadcastId: String): Boolean {
        session.send(
            DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_UPLOAD) {
                addProperty(DeviceExchangeAccountClient.KEY_BROADCAST_ID, broadcastId)
                addProperty(KEY_CONTENT_TYPE, E2eRelayRecords.CONTENT_TYPE)
            }
        )
        val answer = session.receive()
        val accepted = answer?.type == DeviceExchangeEnvelope.TYPE_UPLOAD_ACCEPTED
        if (!accepted) Timber.i("ExchangeRelayUploader: upload not accepted (%s)", answer?.string(KEY_REASON))
        return accepted
    }

    // Whole frames per chunk keep every chunk joinable; a few frames per chunk keep the framing overhead small.
    private fun forward(generation: Generation, sourceUrl: String, writer: ExchangeRelayChunkWriter) {
        val connection = openSource(sourceUrl)
        val disconnect = Closeable { connection.disconnect() }
        if (!generation.registerOrClose(disconnect)) {
            return
        }
        try {
            connection.inputStream.use { input ->
                val splitter = AdtsFrameSplitter()
                val batch = mutableListOf<ByteArray>()
                val buffer = ByteArray(READ_BUFFER_BYTES)
                var read = input.read(buffer)
                while (read >= 0 && generation.isRunning) {
                    batch += splitter.feed(buffer, read)
                    if (batch.size >= FRAMES_PER_CHUNK) {
                        writer.write(batch.flatten(), joinable = true)
                        batch.clear()
                    }
                    read = input.read(buffer)
                }
            }
        } finally {
            generation.unregister(disconnect)
            connection.disconnect()
        }
    }

    // S4258: a protected broadcast's own server challenges this read like any listener's, loopback included,
    // so the upload answers with the internal pair; the answer is sent and never logged.
    private fun openSource(sourceUrl: String): HttpURLConnection {
        val first = sourceConnection(sourceUrl, authorization = null)
        val challenge = first.takeIf { it.responseCode == HttpURLConnection.HTTP_UNAUTHORIZED }
            ?.headerFields?.entries
            ?.filter { it.key.equals(BroadcastDigest.HEADER_CHALLENGE, ignoreCase = true) }
            ?.flatMap { it.value }
            ?.takeIf { it.isNotEmpty() }
            ?.joinToString(", ")
            ?: return first
        first.disconnect()
        val path = URL(sourceUrl).path
        val answer = BroadcastDigest.answer(challenge, "GET", path, credentials.internalPair)
        return sourceConnection(sourceUrl, answer)
    }

    private fun sourceConnection(sourceUrl: String, authorization: String?) =
        (URL(sourceUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = SOURCE_TIMEOUT_MS
            readTimeout = SOURCE_TIMEOUT_MS
            authorization?.let { setRequestProperty(BroadcastDigest.HEADER_AUTHORIZATION, it) }
        }

    // Runs until the job is cancelled or the upload finishes; a write to a dropped stream throws and the caller
    // reconnects.
    private suspend fun pump(generation: Generation, feed: ExchangeVideoRelayFeed, writer: ExchangeRelayChunkWriter) {
        var joined = false
        feed.chunks.takeWhile { !generation.isFinishing }.collect { chunk ->
            val room = ExchangeRelayChunkWriter.MAX_CHUNK_LENGTH - ExchangeRelaySealer.RECORD_OVERHEAD
            val fits = chunk.payload.size < room
            // A skipped chunk would leave the listeners mid-picture, so the upload waits for the next keyframe.
            joined = fits && (joined || chunk.joinable)
            if (joined) writer.write(chunk.payload, chunk.joinable)
        }
    }

    private fun List<ByteArray>.flatten(): ByteArray {
        val joined = ByteArray(sumOf { it.size })
        var at = 0
        forEach { frame ->
            frame.copyInto(joined, at)
            at += frame.size
        }
        return joined
    }

    private fun closeQuietly(stream: Closeable) {
        try {
            stream.close()
        } catch (e: IOException) {
            Timber.d(e, "ExchangeRelayUploader: close failed")
        }
    }

    private sealed class Source(val contentType: String) {
        class Adts(val url: String) : Source(CONTENT_TYPE_AAC)
        class Ts(val feed: ExchangeVideoRelayFeed) : Source(CONTENT_TYPE_TS)
    }

    private companion object {
        const val KEY_CONTENT_TYPE = "contentType"
        const val KEY_REASON = DeviceExchangeEnvelope.KEY_REASON
        const val CONTENT_TYPE_AAC = "audio/aac"
        const val CONTENT_TYPE_TS = "video/mp2t"
        const val FRAMES_PER_CHUNK = 4
        const val READ_BUFFER_BYTES = 8 * 1024
        const val SOURCE_TIMEOUT_MS = 10_000
        const val INITIAL_BACKOFF_MS = 2_000L
        const val MAX_BACKOFF_MS = 60_000L
    }
}
