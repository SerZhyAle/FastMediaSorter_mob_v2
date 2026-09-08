package com.sza.fastmediasorter.wear.service.helpers

import android.os.ParcelFileDescriptor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject

/** One ADTS frame stays well under a kilobyte at the capture bitrate, so a read carries several. */
private const val READ_BUFFER_BYTES = 4_096

private const val PIPE_READ_END = 0
private const val PIPE_WRITE_END = 1

/**
 * S2550 ADR-7: the pipe between the microphone recorder and whoever is listening over the LAN.
 *
 * Three properties are the whole reason this class exists instead of handing the recorder a socket
 * descriptor directly, and the first of them is measured rather than assumed (S2550 strategic §6.2,
 * `sdk_gwear_x86_64` API 37, 2026-09-05):
 *
 * 1. Closing the read end while capture runs makes `MediaRecorder.stop()` throw. So the pump drains
 *    for the whole session and a listener that goes away is dropped, never followed.
 * 2. The HTTP response header has to precede the audio, and the recorder cannot write it.
 * 3. A listener may connect after capture started, or reconnect, without the microphone being
 *    touched at all.
 *
 * S2509 turned the single listener into a bounded set of them. What that costs, and why it is paid
 * this way: the watch is the weakest device of the pair, so every listener gets its own bounded queue
 * and its own writer coroutine rather than being written to from the drain thread. One stalled reader
 * then fills only its own queue - which discards its oldest frames instead of growing - and neither
 * the microphone nor any healthy listener waits for it. Live audio is the case where dropping an old
 * frame is right: a listener that fell a second behind wants the present, not a replay.
 *
 * Ownership rule the callers inherit: both pipe ends belong to this class and are closed by [close]
 * alone, which must run only after the recorder has been stopped.
 *
 * It answers [LiveAudioSource] because the LAN server declares that port: the server is then testable
 * on the JVM, where `ParcelFileDescriptor` does not exist and this class cannot run at all.
 */
class LiveAudioPipeSink @Inject constructor() : LiveAudioSource {

    /**
     * One attached listener: its socket stream, the bounded queue feeding it, and the writer that
     * drains that queue. [finished] is what [readInto] suspends on, so the listener's HTTP connection
     * lives exactly as long as this object does.
     */
    private class Listener(val sink: OutputStream) {

        val frames = Channel<ByteArray>(
            capacity = LiveAudioLimits.LISTENER_BUFFER_FRAMES,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )

        val finished = CompletableDeferred<Unit>()

        private var writerJob: Job? = null

        /** Never suspends and never fails: a full queue drops its oldest frame and capture moves on. */
        fun offer(frame: ByteArray) {
            frames.trySend(frame)
        }

        fun startWriter(scope: CoroutineScope) {
            writerJob = scope.launch(Dispatchers.IO) {
                try {
                    for (frame in frames) {
                        sink.write(frame)
                        sink.flush()
                    }
                } catch (e: IOException) {
                    // This listener walked out of Wi-Fi or closed its tab. It costs itself the stream
                    // and nothing else - the drain never sees this, and no other listener is touched.
                    Timber.i(e, "A live audio listener stopped reading; dropping that listener alone")
                } finally {
                    finished.complete(Unit)
                }
            }
        }

        /** Ends this listener alone. Idempotent: a disconnect and a stop may both arrive. */
        fun end() {
            frames.close()
            writerJob?.cancel()
            writerJob = null
            finished.complete(Unit)
        }
    }

    private val lock = Any()
    private val listeners = mutableListOf<Listener>()
    private var scope: CoroutineScope? = null
    private var readEnd: ParcelFileDescriptor? = null
    private var writeEnd: ParcelFileDescriptor? = null
    private var pumpJob: Job? = null

    /** True between [open] and [close]; a second [open] over one of those is a programming error. */
    val isOpen: Boolean
        get() = writeEnd != null

    /** How many listeners are attached right now. The server refuses past the same limit. */
    val listenerCount: Int
        get() = synchronized(lock) { listeners.size }

    /**
     * Creates the pipe and starts draining it. The returned descriptor is what `MediaRecorder` is
     * given as its output; the caller never closes it.
     *
     * The scope belongs to the session's owner rather than to this class, so the pump and every
     * listener writer die with the service that opened the microphone rather than outliving it.
     */
    fun open(scope: CoroutineScope): ParcelFileDescriptor {
        check(!isOpen) { "The live audio pipe is already open" }
        val pipe = ParcelFileDescriptor.createPipe()
        val read = pipe[PIPE_READ_END]
        val write = pipe[PIPE_WRITE_END]
        readEnd = read
        writeEnd = write
        this.scope = scope
        pumpJob = scope.launch(Dispatchers.IO) { drain(read) }
        return write
    }

    /**
     * Attaches [sink] as one of the bounded listeners and suspends until that listener alone is
     * finished - by [detach], by [close], or by its own write failing. Answers `false` when the
     * listener limit is already reached, so the caller refuses that connection itself rather than
     * queueing it behind a slot that may never free.
     */
    override suspend fun readInto(sink: OutputStream): Boolean {
        val listener = scope?.let { register(sink, it) } ?: return false
        try {
            listener.finished.await()
        } finally {
            unregister(listener)
        }
        return true
    }

    /** Ends the session of [sink] alone. Every other listener and the capture keep running. */
    override fun detach(sink: OutputStream) {
        val listener = synchronized(lock) { listeners.firstOrNull { it.sink === sink } }
        listener?.end()
    }

    /** Ends every listener at once, without touching the pipe, the recorder or capture. */
    override fun detachAll() {
        val ending = synchronized(lock) { listeners.toList() }
        ending.forEach { it.end() }
    }

    /**
     * Ends the session and closes both pipe ends.
     *
     * Call only after the recorder has been stopped: closing the read end under a running capture is
     * the measured case that makes `MediaRecorder.stop()` throw.
     */
    fun close() {
        detachAll()
        closeQuietly(writeEnd)
        closeQuietly(readEnd)
        writeEnd = null
        readEnd = null
        pumpJob?.cancel()
        pumpJob = null
        scope = null
    }

    private fun register(sink: OutputStream, pumpScope: CoroutineScope): Listener? {
        val listener = synchronized(lock) {
            if (listeners.size >= LiveAudioLimits.MAX_LISTENERS) {
                null
            } else {
                Listener(sink).also { listeners.add(it) }
            }
        }
        listener?.startWriter(pumpScope)
        return listener
    }

    private fun unregister(listener: Listener) {
        synchronized(lock) { listeners.remove(listener) }
        listener.end()
    }

    private fun drain(source: ParcelFileDescriptor) {
        val buffer = ByteArray(READ_BUFFER_BYTES)
        try {
            ParcelFileDescriptor.AutoCloseInputStream(source).use { input -> pump(input, buffer) }
        } catch (e: IOException) {
            // close() shuts the read end to unblock this thread, so the normal end of a session
            // arrives here as an IOException rather than as end-of-stream.
            Timber.i(e, "The live audio pipe stopped draining")
        }
    }

    /**
     * Blocking by construction: the read has to be interruptible by closing the descriptor, which is
     * what [close] does, and no coroutine cancellation can unblock a native read.
     */
    private fun pump(input: InputStream, buffer: ByteArray) {
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) {
                break
            }
            deliver(buffer, read)
        }
    }

    /**
     * Hands the frame to every attached listener. The copy is taken once and shared, because the read
     * buffer is reused on the next pass and the listeners read it after this call has returned; they
     * only ever read it, so one array is safe for all of them.
     */
    private fun deliver(buffer: ByteArray, length: Int) {
        val active = synchronized(lock) { listeners.toList() }
        if (active.isEmpty()) {
            return
        }
        val frame = buffer.copyOf(length)
        active.forEach { it.offer(frame) }
    }

    private fun closeQuietly(descriptor: ParcelFileDescriptor?) {
        try {
            descriptor?.close()
        } catch (e: IOException) {
            Timber.w(e, "Failed to close a live audio pipe end")
        }
    }
}
