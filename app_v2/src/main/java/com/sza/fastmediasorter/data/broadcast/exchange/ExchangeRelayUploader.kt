package com.sza.fastmediasorter.data.broadcast.exchange

import com.sza.fastmediasorter.core.di.ApplicationScope
import com.sza.fastmediasorter.core.di.IoDispatcher
import com.sza.fastmediasorter.data.remote.exchange.DeviceExchangeAccountClient
import com.sza.fastmediasorter.data.remote.exchange.DeviceExchangeEnvelope
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.Closeable
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Collections
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
 * upload actually runs; a dropped upload is retried until [stop].
 */
@Singleton
class ExchangeRelayUploader @Inject constructor(
    private val client: DeviceExchangeAccountClient,
    @ApplicationScope private val appScope: CoroutineScope,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    private val mutableAccepted = MutableStateFlow<String?>(null)
    val accepted: StateFlow<String?> = mutableAccepted.asStateFlow()

    private var job: Job? = null

    // Blocking reads on both streams ignore cancellation; closing them is what ends an upload.
    private val openStreams: MutableSet<Closeable> = Collections.synchronizedSet(mutableSetOf())

    /** Uploads the ADTS stream served at [sourceUrl] as broadcast [broadcastId] through [entry]. */
    @Synchronized
    fun start(entry: DeviceExchangeAccountEntry, broadcastId: String, sourceUrl: String) {
        stopLocked()
        job = appScope.launch(ioDispatcher) { uploadUntilStopped(entry, broadcastId, Source.Adts(sourceUrl)) }
    }

    /** Uploads the MPEG-TS the video session publishes into [feed] as broadcast [broadcastId] through [entry]. */
    @Synchronized
    fun startVideo(entry: DeviceExchangeAccountEntry, broadcastId: String, feed: ExchangeVideoRelayFeed) {
        stopLocked()
        job = appScope.launch(ioDispatcher) { uploadUntilStopped(entry, broadcastId, Source.Ts(feed)) }
    }

    @Synchronized
    fun stop() = stopLocked()

    private fun stopLocked() {
        job?.cancel()
        job = null
        val streams = synchronized(openStreams) { openStreams.toList().also { openStreams.clear() } }
        streams.forEach(::closeQuietly)
        mutableAccepted.value = null
    }

    private suspend fun uploadUntilStopped(entry: DeviceExchangeAccountEntry, broadcastId: String, source: Source) {
        var backoffMs = INITIAL_BACKOFF_MS
        var refused = false
        while (currentCoroutineContext().isActive && !refused) {
            try {
                refused = !upload(entry, broadcastId, source)
                backoffMs = INITIAL_BACKOFF_MS
            } catch (e: IOException) {
                Timber.i(e, "ExchangeRelayUploader: upload ended")
            }
            mutableAccepted.compareAndSet(broadcastId, null)
            if (refused) break
            delay(backoffMs)
            backoffMs = (backoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
        }
    }

    /** False when the server refused the upload - a deployment without the relay - which no retry changes. */
    private suspend fun upload(entry: DeviceExchangeAccountEntry, broadcastId: String, source: Source): Boolean {
        // Read before the stream opens, so no server stream is held while the encoder warms up.
        val header = (source as? Source.Ts)?.feed?.header?.filterNotNull()?.first()
        val session = client.openAuthenticated(entry).also(openStreams::add)
        try {
            session.send(
                DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_UPLOAD) {
                    addProperty(DeviceExchangeAccountClient.KEY_BROADCAST_ID, broadcastId)
                    addProperty(KEY_CONTENT_TYPE, source.contentType)
                }
            )
            val answer = session.receive()
            if (answer?.type != DeviceExchangeEnvelope.TYPE_UPLOAD_ACCEPTED) {
                val reason = answer?.string(DeviceExchangeEnvelope.KEY_REASON)
                Timber.i("ExchangeRelayUploader: upload not accepted (%s)", reason)
                return false
            }
            mutableAccepted.value = broadcastId
            val writer = ExchangeRelayChunkWriter(session.connection.output, header)
            when (source) {
                is Source.Adts -> forward(source.url, writer)
                is Source.Ts -> pump(source.feed, writer)
            }
            writer.end()
            return true
        } finally {
            openStreams.remove(session)
            closeQuietly(session)
        }
    }

    // Whole frames per chunk keep every chunk joinable; a few frames per chunk keep the framing overhead small.
    private fun forward(sourceUrl: String, writer: ExchangeRelayChunkWriter) {
        val connection = (URL(sourceUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = SOURCE_TIMEOUT_MS
            readTimeout = SOURCE_TIMEOUT_MS
        }
        val disconnect = Closeable { connection.disconnect() }
        openStreams.add(disconnect)
        try {
            connection.inputStream.use { input ->
                val splitter = AdtsFrameSplitter()
                val batch = mutableListOf<ByteArray>()
                val buffer = ByteArray(READ_BUFFER_BYTES)
                var read = input.read(buffer)
                while (read >= 0) {
                    batch += splitter.feed(buffer, read)
                    if (batch.size >= FRAMES_PER_CHUNK) {
                        writer.write(batch.flatten(), joinable = true)
                        batch.clear()
                    }
                    read = input.read(buffer)
                }
            }
        } finally {
            openStreams.remove(disconnect)
            connection.disconnect()
        }
    }

    // Runs until the job is cancelled; a write to a dropped stream throws and the caller reconnects.
    private suspend fun pump(feed: ExchangeVideoRelayFeed, writer: ExchangeRelayChunkWriter) {
        var joined = false
        feed.chunks.collect { chunk ->
            val fits = chunk.payload.size < ExchangeRelayChunkWriter.MAX_CHUNK_LENGTH
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
        const val CONTENT_TYPE_AAC = "audio/aac"
        const val CONTENT_TYPE_TS = "video/mp2t"
        const val FRAMES_PER_CHUNK = 4
        const val READ_BUFFER_BYTES = 8 * 1024
        const val SOURCE_TIMEOUT_MS = 10_000
        const val INITIAL_BACKOFF_MS = 2_000L
        const val MAX_BACKOFF_MS = 60_000L
    }
}
