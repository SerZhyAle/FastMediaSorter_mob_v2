package com.sza.fastmediasorter.core.playback

import android.net.Uri
import timber.log.Timber
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S2881: the one narrow thing the player layer is allowed to know about a watch recording.
 *
 * The player must not reach sideways into the wear session owner, and the wear session owner must
 * not build data sources, so neither knows the other: the session arms this holder with the address
 * it is about to play and the file to fill, and the media source factory asks it - by address - for
 * a sink. Every other stream this factory serves, internet radio included, asks and is told no.
 *
 * The address is part of the deal rather than a mere convenience: the HTTP branch of
 * `NetworkAwareMediaSourceFactory` is shared with radio, so an armed holder that answered any URI
 * would record whatever happened to start playing next.
 */
@Singleton
class ListenRecordingSinkHolder @Inject constructor() {

    private val lock = Any()
    private var armedUrl: String? = null
    private var target: File? = null
    private var liveSink: OutputStream? = null

    /** True between [arm] and [closeAndTake], which is the only window the tee is built in. */
    fun isArmed(): Boolean = synchronized(lock) { armedUrl != null }

    /**
     * Declare that the stream at [streamUrl] is about to be played and must also land in [file].
     *
     * Called before playback starts, because the data source is opened inside that start and asks
     * this holder while it opens.
     */
    fun arm(streamUrl: String, file: File) {
        synchronized(lock) {
            armedUrl = streamUrl
            target = file
            liveSink = null
        }
    }

    /**
     * The sink for [uri], or null when this address is not the one being recorded.
     *
     * A second call for the same armed session returns null rather than a second handle: the player
     * may re-open a source after an error, and two handles on one file would interleave into a file
     * that decodes as neither take.
     */
    fun openSinkFor(uri: Uri): OutputStream? = synchronized(lock) {
        val file = target.takeIf { armedUrl == uri.toString() && liveSink == null }
        val opened = if (file == null) null else openQuietly(file)
        if (opened != null) {
            liveSink = opened
        }
        opened
    }

    /**
     * Close the sink and return the file it filled, or null when nothing was being recorded.
     *
     * This is what makes strategic §5.2's teardown order reachable from the session owner: the file
     * is whole before playback is torn down, instead of being closed later on the player's thread
     * while the save is already reading it. The tee closes its own copy afterwards, which a closed
     * stream accepts.
     */
    fun closeAndTake(): File? = synchronized(lock) {
        val file = target
        val sink = liveSink
        armedUrl = null
        target = null
        liveSink = null
        if (sink != null) {
            try {
                sink.flush()
                sink.close()
            } catch (e: IOException) {
                Timber.i(e, "Could not close the watch recording file cleanly")
            }
        }
        if (sink == null) null else file
    }

    private fun openQuietly(file: File): OutputStream? = try {
        LockedSink(BufferedOutputStream(FileOutputStream(file)))
    } catch (e: IOException) {
        Timber.i(e, "Could not open the watch recording file; the session plays without recording")
        null
    }

    /**
     * The two threads that touch this file are the player's loader thread, which writes, and
     * whichever thread ends the session, which closes - so every operation is serialised here.
     * Without that a close landing between a buffered write's bounds check and its copy is a garbled
     * tail or a NullPointerException thrown into the player's read loop, neither of which the tee's
     * IOException handling would catch.
     *
     * A write after the close is dropped rather than refused: the recording is finished at that
     * point by intent, and reporting it as a failure would say a word about every ordinary stop.
     */
    private class LockedSink(private val delegate: OutputStream) : OutputStream() {

        private var closed = false

        override fun write(b: Int) = synchronized(this) {
            if (!closed) {
                delegate.write(b)
            }
        }

        override fun write(b: ByteArray, off: Int, len: Int) = synchronized(this) {
            if (!closed) {
                delegate.write(b, off, len)
            }
        }

        override fun flush() = synchronized(this) {
            if (!closed) {
                delegate.flush()
            }
        }

        override fun close() = synchronized(this) {
            if (!closed) {
                closed = true
                delegate.close()
            }
        }
    }
}
