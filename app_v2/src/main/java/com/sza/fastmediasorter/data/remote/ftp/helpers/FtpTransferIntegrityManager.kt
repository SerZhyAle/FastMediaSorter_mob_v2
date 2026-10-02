package com.sza.fastmediasorter.data.remote.ftp.helpers

import androidx.annotation.WorkerThread
import com.sza.fastmediasorter.data.remote.ftp.FtpProgressOutputStream
import org.apache.commons.net.ftp.FTPClient
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicLong

/**
 * Positive completion alone cannot authorize deletion of a move's source.
 * Every member blocks on the FTP socket; callers already run inside their IO-confined operation.
 */
internal object FtpTransferIntegrityManager {

    @WorkerThread
    fun remoteSize(client: FTPClient, path: String): Long {
        client.getSize(path)?.trim()?.toLongOrNull()?.takeIf { it >= 0L }?.let { return it }
        val metadata = client.mlistFile(path)
        // FTPFile defaults a missing MLST size fact to zero, which is not evidence of an empty file.
        val metadataSize = metadata?.takeIf { it.isFile }?.rawListing?.trim()?.substringBefore(' ')
            ?.split(';')?.firstOrNull { it.startsWith("size=", ignoreCase = true) }
            ?.substringAfter('=')?.toLongOrNull()?.takeIf { it >= 0L }
        val name = path.substringAfterLast('/')
        return metadataSize ?: client.listFiles(path)?.singleOrNull {
            it.isFile && it.name == name
        }?.size?.takeIf { it >= 0L }
            ?: throw IOException("FTP file size unavailable: $path; source retained")
    }

    @WorkerThread
    fun upload(client: FTPClient, path: String, input: InputStream, expectedSize: Long) {
        val counter = AtomicLong(0L)
        val counted = object : FilterInputStream(input) {
            override fun read(): Int = input.read().also { if (it >= 0) counter.incrementAndGet() }

            override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
                input.read(buffer, offset, length).also { if (it > 0) counter.addAndGet(it.toLong()) }
        }
        if (!client.storeFile(path, counted)) throw IOException("FTP upload failed: ${client.replyString}")
        if (input.read() >= 0) throw IOException("FTP upload did not consume source: $path; source retained")
        val transferred = counter.get()
        if (expectedSize > 0L) verifyLength(path, expectedSize, transferred)
        verifyLength(path, transferred, remoteSize(client, path))
    }

    @WorkerThread
    fun download(
        client: FTPClient,
        path: String,
        output: OutputStream,
        expectedSize: Long = remoteSize(client, path)
    ) {
        val counter = AtomicLong(0L)
        val counted = FtpProgressOutputStream(output, counter)
        val success = try {
            client.retrieveFile(path, counted)
        } catch (e: SocketTimeoutException) {
            // A retry starts at byte zero; appending it to an already written sink corrupts the copy.
            throw if (counter.get() > 0L) {
                IOException("FTP partial download timed out: $path; source retained", e)
            } else {
                e
            }
        }
        if (!success) throw IOException("FTP download failed: ${client.replyString}")
        verifyLength(path, expectedSize, counter.get())
    }

    private fun verifyLength(path: String, expected: Long, actual: Long) {
        if (expected != actual) {
            throw IOException("FTP length mismatch: $path, expected=$expected, actual=$actual; source retained")
        }
    }
}
