package com.sza.fastmediasorter.ui.player.helpers

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import timber.log.Timber
import java.io.IOException
import java.io.OutputStream

/**
 * S2881: copies the bytes the player reads into a sink, inside the one connection it already holds.
 *
 * ADR-4: a second HTTP GET against the watch would take one of its four listener slots and add a
 * second continuous Wi-Fi send for the whole session, so the recording branches off here instead of
 * opening a connection of its own. The delegation shape is [RadioHttpDataSource]'s, at the same
 * seam - only [open], [read] and [close] need behaviour beyond what the wrapped source already has.
 *
 * The sink is deliberately expendable: a failed write ends the recording and leaves playback
 * running, because the owner is listening to the watch first and recording it second. Nothing here
 * consults the length [open] returns - the watch's response declares none by design.
 *
 * @param openSink the sink for the address being opened, or null when this address is not the one
 * being recorded - the HTTP path this decorator sits on is shared with internet radio.
 */
@UnstableApi
internal class ListenRecordingTeeDataSource(
    private val delegate: DataSource,
    private val openSink: (Uri) -> OutputStream?,
) : DataSource by delegate {

    private var sink: OutputStream? = null

    override fun open(dataSpec: DataSpec): Long {
        val length = delegate.open(dataSpec)
        sink = try {
            openSink(dataSpec.uri)
        } catch (e: IOException) {
            Timber.i(e, "Could not open the listening recording sink; playing without recording")
            null
        }
        if (sink != null) {
        }
        return length
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val read = try {
            delegate.read(buffer, offset, length)
        } catch (e: IOException) {
            // The bytes already written are a whole recording of what was heard, so close the sink
            // before the exception leaves - the caller may never reach close() on a broken source.
            closeSink()
            throw e
        }
        if (read > 0) {
            tee(buffer, offset, read)
        }
        return read
    }

    override fun close() {
        closeSink()
        delegate.close()
    }

    private fun tee(buffer: ByteArray, offset: Int, read: Int) {
        val target = sink ?: return
        try {
            target.write(buffer, offset, read)
        } catch (e: IOException) {
            Timber.i(e, "The recording sink stopped taking bytes; playback carries on without it")
            closeSink()
        }
    }

    private fun closeSink() {
        val target = sink ?: return
        sink = null
        try {
            target.flush()
            target.close()
        } catch (e: IOException) {
            Timber.i(e, "Could not close the listening recording sink")
        }
    }
}
