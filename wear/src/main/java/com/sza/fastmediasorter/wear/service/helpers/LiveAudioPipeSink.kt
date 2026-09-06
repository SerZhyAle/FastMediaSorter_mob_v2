package com.sza.fastmediasorter.wear.service.helpers

import android.os.ParcelFileDescriptor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject

/** One ADTS frame stays well under a kilobyte at the capture bitrate, so a read carries several. */
private const val READ_BUFFER_BYTES = 4_096

private const val PIPE_READ_END = 0
private const val PIPE_WRITE_END = 1

/**
 * S2550 ADR-7: the pipe between the microphone recorder and whoever is listening over the LAN.
 *
 * Three properties are the whole reason this class exists instead of handing the recorder a socket
 * descriptor directly, and the first of them is measured rather than assumed (strategic §6.2,
 * `sdk_gwear_x86_64` API 37, 2026-09-05):
 *
 * 1. Closing the read end while capture runs makes `MediaRecorder.stop()` throw. So the pump drains
 *    for the whole session and a listener that goes away is dropped, never followed.
 * 2. The HTTP response header has to precede the audio, and the recorder cannot write it.
 * 3. A listener may connect after capture started, or reconnect, without the microphone being
 *    touched at all.
 *
 * Ownership rule the callers inherit: both pipe ends belong to this class and are closed by [close]
 * alone, which must run only after the recorder has been stopped.
 *
 * It answers [LiveAudioSource] because the LAN server declares that port: the server is then
 * testable on the JVM, where `ParcelFileDescriptor` does not exist and this class cannot run at all.
 */
class LiveAudioPipeSink @Inject constructor() : LiveAudioSource {

    private class Listener(val sink: OutputStream) {
        val finished = CompletableDeferred<Unit>()
    }

    private val listener = AtomicReference<Listener?>(null)
    private var readEnd: ParcelFileDescriptor? = null
    private var writeEnd: ParcelFileDescriptor? = null
    private var pumpJob: Job? = null

    /** True between [open] and [close]; a second [open] over one of those is a programming error. */
    val isOpen: Boolean
        get() = writeEnd != null

    /**
     * Creates the pipe and starts draining it. The returned descriptor is what `MediaRecorder` is
     * given as its output; the caller never closes it.
     *
     * The scope belongs to the session's owner rather than to this class, so the pump dies with the
     * service that opened the microphone rather than outliving it.
     */
    fun open(scope: CoroutineScope): ParcelFileDescriptor {
        check(!isOpen) { "The live audio pipe is already open" }
        val pipe = ParcelFileDescriptor.createPipe()
        val read = pipe[PIPE_READ_END]
        val write = pipe[PIPE_WRITE_END]
        readEnd = read
        writeEnd = write
        pumpJob = scope.launch(Dispatchers.IO) { drain(read) }
        return write
    }

    /**
     * Attaches [sink] as the single listener and suspends until it is detached - by [detach], by
     * [close], or by its own write failing. Answers `false` when another listener already holds the
     * stream, so the caller can refuse that one itself instead of queueing behind a slot.
     */
    override suspend fun readInto(sink: OutputStream): Boolean {
        val attached = Listener(sink)
        val accepted = listener.compareAndSet(null, attached)
        if (accepted) {
            try {
                attached.finished.await()
            } finally {
                listener.compareAndSet(attached, null)
            }
        }
        return accepted
    }

    /** Ends the current listener's [readInto] without touching the pipe, the recorder or capture. */
    override fun detach() {
        listener.getAndSet(null)?.finished?.complete(Unit)
    }

    /**
     * Ends the session and closes both pipe ends.
     *
     * Call only after the recorder has been stopped: closing the read end under a running capture is
     * the measured case that makes `MediaRecorder.stop()` throw.
     */
    fun close() {
        detach()
        closeQuietly(writeEnd)
        closeQuietly(readEnd)
        writeEnd = null
        readEnd = null
        pumpJob?.cancel()
        pumpJob = null
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

    private fun deliver(buffer: ByteArray, length: Int) {
        val active = listener.get() ?: return
        try {
            active.sink.write(buffer, 0, length)
            active.sink.flush()
        } catch (e: IOException) {
            // A listener that walked out of Wi-Fi costs itself the stream and nothing else: dropping
            // it here is what keeps property 1 in the class KDoc true.
            Timber.i(e, "The live audio listener stopped reading; dropping it and keeping capture up")
            listener.compareAndSet(active, null)
            active.finished.complete(Unit)
        }
    }

    private fun closeQuietly(descriptor: ParcelFileDescriptor?) {
        try {
            descriptor?.close()
        } catch (e: IOException) {
            Timber.w(e, "Failed to close a live audio pipe end")
        }
    }
}
