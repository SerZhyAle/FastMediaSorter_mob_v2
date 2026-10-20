package com.sza.fastmediasorter.wear.data.exchange

import com.sza.fastmediasorter.data.security.e2e.E2eRelayRecords
import com.sza.fastmediasorter.wear.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
 * The watch's upload to the fan-out relay of contract DEVICE-EXCHANGE 7.6, mirrored from the phone's
 * `ExchangeRelayUploader` for `AUDIO_ONLY`. It listens to the broadcast's own LAN HTTP stream - one encoder output
 * feeds the LAN and the relay, and the upload takes one LAN slot as the contract counts it - and forwards whole ADTS
 * frames as joinable chunks.
 *
 * [accepted] names the broadcast whose upload the server accepted, so the descriptor lists `RELAY` only while an
 * upload actually runs; a dropped upload is retried until [stop].
 */
@Singleton
class WearExchangeRelayUploader @Inject constructor(
    private val opener: WearExchangeSessionOpener,
    private val keys: WearExchangeRelayKeys,
    @ApplicationScope private val scope: CoroutineScope,
) {

    private val mutableAccepted = MutableStateFlow<String?>(null)
    val accepted: StateFlow<String?> = mutableAccepted.asStateFlow()

    private var job: Job? = null

    // Blocking reads and writes ignore cancellation; closing the streams is what ends an upload.
    private val openStreams: MutableSet<Closeable> = Collections.synchronizedSet(mutableSetOf())

    /** Uploads the ADTS stream served at [sourceUrl] as broadcast [broadcastId] through [entry]. */
    @Synchronized
    fun start(entry: WearExchangeEntry, broadcastId: String, sourceUrl: String) {
        stopLocked()
        job = scope.launch { uploadUntilStopped(entry, broadcastId, sourceUrl) }
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

    private suspend fun uploadUntilStopped(entry: WearExchangeEntry, broadcastId: String, sourceUrl: String) {
        var backoffMs = WearExchangeKeepalive.INITIAL_BACKOFF_MS
        var refused = false
        while (currentCoroutineContext().isActive && !refused) {
            refused = try {
                !upload(entry, broadcastId, sourceUrl).also { backoffMs = WearExchangeKeepalive.INITIAL_BACKOFF_MS }
            } catch (e: WearExchangeNotEnrolledException) {
                Timber.w(e, "WearExchangeRelayUploader: the outgoing entry holds no token")
                true
            } catch (e: IOException) {
                Timber.i(e, "WearExchangeRelayUploader: upload ended")
                false
            }
            mutableAccepted.compareAndSet(broadcastId, null)
            if (!refused) {
                delay(backoffMs)
                backoffMs = WearExchangeKeepalive.nextBackoffMs(backoffMs)
            }
        }
    }

    /** False when the server refused the upload - a deployment without the relay - which no retry changes. */
    private suspend fun upload(entry: WearExchangeEntry, broadcastId: String, sourceUrl: String): Boolean {
        val session = opener.open(entry, null, WearExchangeConnector.ENROLL_READ_TIMEOUT_MS).also(openStreams::add)
        try {
            // 27 item DP: a server without `sealed` gets no relay at all; every upload stream is a new epoch (DL).
            val sealing = WearExchangeEntry.FEATURE_SEALED in session.welcome.features
            val ring = keys.newEpoch(broadcastId)?.takeIf { sealing }
            val accepted = ring != null && accepted(session, broadcastId)
            if (ring != null && accepted) {
                mutableAccepted.value = broadcastId
                val sealer = WearExchangeRelaySealer(broadcastId, CONTENT_TYPE_AAC, ring) {
                    keys.isCurrent(broadcastId, ring)
                }
                val writer = WearExchangeRelayChunkWriter(session.connection.output, sealer.headerChunk(), sealer)
                forward(sourceUrl, writer)
                writer.end()
            }
            return accepted
        } finally {
            openStreams.remove(session)
            closeQuietly(session)
        }
    }

    private fun accepted(session: WearExchangeSession, broadcastId: String): Boolean {
        session.send(
            WearExchangeEnvelope.of(WearExchangeEnvelope.TYPE_UPLOAD) {
                addProperty(KEY_BROADCAST_ID, broadcastId)
                addProperty(KEY_CONTENT_TYPE, E2eRelayRecords.CONTENT_TYPE)
            }
        )
        val answer = session.receive()
        val accepted = answer?.type == WearExchangeEnvelope.TYPE_UPLOAD_ACCEPTED
        if (!accepted) Timber.i("WearExchangeRelayUploader: upload not accepted (%s)", answer?.string(KEY_REASON))
        return accepted
    }

    // Whole frames per chunk keep every chunk joinable; a few frames per chunk keep the framing overhead small.
    private fun forward(sourceUrl: String, writer: WearExchangeRelayChunkWriter) {
        val connection = (URL(sourceUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = SOURCE_TIMEOUT_MS
            readTimeout = SOURCE_TIMEOUT_MS
        }
        val disconnect = Closeable { connection.disconnect() }
        openStreams.add(disconnect)
        try {
            connection.inputStream.use { input ->
                val splitter = WearAdtsFrameSplitter()
                val batch = mutableListOf<ByteArray>()
                val buffer = ByteArray(READ_BUFFER_BYTES)
                var read = input.read(buffer)
                while (read >= 0) {
                    batch += splitter.feed(buffer, read)
                    if (batch.size >= FRAMES_PER_CHUNK) {
                        writer.write(batch.joined(), joinable = true)
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

    private fun List<ByteArray>.joined(): ByteArray {
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
            Timber.d(e, "WearExchangeRelayUploader: close failed")
        }
    }

    private companion object {
        const val KEY_BROADCAST_ID = "broadcastId"
        const val KEY_CONTENT_TYPE = "contentType"
        const val KEY_REASON = "reason"
        const val CONTENT_TYPE_AAC = "audio/aac"
        const val FRAMES_PER_CHUNK = 4
        const val READ_BUFFER_BYTES = 8 * 1024
        const val SOURCE_TIMEOUT_MS = 10_000
    }
}
