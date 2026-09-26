package com.sza.fastmediasorter.data.remote.sftp

import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream
import java.io.FilterOutputStream
import java.io.IOException
import java.io.OutputStream

/**
 * Destination of a retried download. A failed attempt may already have written part of the file;
 * the retry streams the whole file again, so without a rewind the copy holds the partial bytes
 * followed by the full content and still reports success.
 *
 * The caller's stream is never closed here - the caller owns it.
 */
internal class RewindableDownloadSink(private val target: OutputStream) : FilterOutputStream(target) {

    // Append-mode streams report the current file size here, so a rewind never cuts earlier content.
    private val fileStart: Long? = (target as? FileOutputStream)?.channel?.position()

    var written: Long = 0L
        private set

    override fun write(b: Int) {
        out.write(b)
        written++
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        out.write(b, off, len)
        written += len
    }

    override fun close() = Unit

    /** False when the failed attempt left bytes in a stream that cannot be reset to its start. */
    fun rewind(): Boolean {
        if (written == 0L) return true
        val rewound = when (target) {
            is ByteArrayOutputStream -> {
                target.reset()
                true
            }
            is FileOutputStream -> truncateFile(target)
            else -> false
        }
        if (rewound) written = 0L
        return rewound
    }

    private fun truncateFile(stream: FileOutputStream): Boolean {
        val start = fileStart ?: return false
        return try {
            stream.channel.truncate(start)
            true
        } catch (e: IOException) {
            Timber.w(e, "SFTP download destination could not be truncated for a retry")
            false
        }
    }
}
