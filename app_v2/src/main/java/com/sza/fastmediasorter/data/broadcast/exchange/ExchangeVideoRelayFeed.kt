package com.sza.fastmediasorter.data.broadcast.exchange

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** One relay upload chunk (DEVICE-EXCHANGE 7.6): MPEG-TS packets, joinable when they open with an IDR. */
class ExchangeRelayChunk(val payload: ByteArray, val joinable: Boolean)

/**
 * The hand-over between the video encoder session, which lives in the broadcasting flavors, and the relay upload:
 * the session publishes its MPEG-TS [header] and chunks here, [ExchangeRelayUploader] reads them. The session
 * muxes only while [isWanted], so a broadcast nobody relays costs no copy of its frames.
 */
@Singleton
class ExchangeVideoRelayFeed @Inject constructor() {

    private val mutableHeader = MutableStateFlow<ByteArray?>(null)
    val header: StateFlow<ByteArray?> = mutableHeader.asStateFlow()

    // Dropping the oldest keeps the encoder callback from ever blocking; the relay resumes at the next keyframe.
    private val mutableChunks = MutableSharedFlow<ExchangeRelayChunk>(
        extraBufferCapacity = BUFFERED_CHUNKS,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val chunks: SharedFlow<ExchangeRelayChunk> = mutableChunks.asSharedFlow()

    val isWanted: Boolean get() = mutableChunks.subscriptionCount.value > 0

    fun open(header: ByteArray) {
        mutableHeader.value = header
    }

    fun close() {
        mutableHeader.value = null
    }

    fun offer(chunk: ExchangeRelayChunk) {
        mutableChunks.tryEmit(chunk)
    }

    private companion object {
        const val BUFFERED_CHUNKS = 256
    }
}
