package com.sza.fastmediasorter.data.network.glide

import java.io.IOException
import java.io.PipedInputStream
import java.io.PipedOutputStream

/**
 * A PipedInputStream that can propagate exceptions from the writer thread to the reader thread.
 * Used to stream network data to Glide while isolating the network thread from interrupts.
 */
class ErrorPropagatingPipedInputStream(pipeSize: Int) : PipedInputStream(pipeSize) {
    
    @Volatile
    private var error: Throwable? = null

    fun setError(t: Throwable) {
        error = t
    }

    @Throws(IOException::class)
    override fun read(): Int {
        checkError()
        return super.read()
    }

    @Throws(IOException::class)
    override fun read(b: ByteArray, off: Int, len: Int): Int {
        checkError()
        return super.read(b, off, len)
    }

    @Throws(IOException::class)
    private fun checkError() {
        error?.let { throw IOException("Exception from writer: ${it.message}", it) }
    }
}
