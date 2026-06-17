package com.sza.fastmediasorter.data.remote.ftp

import java.io.FilterOutputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicLong

/**
 * OutputStream wrapper that tallies written bytes into [counter]. Apache Commons Net
 * `retrieveFile` copies through `Util.copyStream`, which writes in buffer-sized chunks via the
 * 3-arg `write`; a separate coroutine polls [counter] to emit byte-level download progress (S0496).
 */
class FtpProgressOutputStream(
    delegate: OutputStream,
    private val counter: AtomicLong,
) : FilterOutputStream(delegate) {

    override fun write(b: Int) {
        out.write(b)
        counter.incrementAndGet()
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        out.write(b, off, len)
        counter.addAndGet(len.toLong())
    }
}
