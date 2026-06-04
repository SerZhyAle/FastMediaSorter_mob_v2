package com.sza.fastmediasorter.core.systeminfo

import timber.log.Timber
import java.io.File
import java.io.RandomAccessFile

/**
 * Rough, budget-limited device benchmarks for the System info dialog. These are NOT precise
 * profiling - they give an order-of-magnitude throughput for quick diagnostics in bug reports.
 *
 * WHY conservative defaults: the buffer/file sizes and time caps below are intentionally small so
 * that running on dialog open does not lag the UI or heat the device. The exact thresholds are an
 * open tuning item (strategic S0337 §6.7) to be adjusted on real hardware.
 *
 * WHY System.nanoTime: it is the monotonic wall-clock for measuring elapsed durations and is not
 * shadowed/frozen by test runners, unlike android.os.SystemClock.
 *
 * All functions are blocking and must be called off the main thread. They return a sentinel
 * [FAILED] (-1.0) instead of throwing.
 */
object SystemInfoBenchmark {

    const val FAILED = -1.0

    private const val MEMORY_BUFFER_BYTES = 4 * 1024 * 1024
    private const val MEMORY_TIME_CAP_NANOS = 200_000_000L
    private const val STORAGE_FILE_BYTES = 8 * 1024 * 1024
    private const val STORAGE_BLOCK_BYTES = 64 * 1024

    /** Approximate in-memory copy throughput in MB/s, measured under a hard time cap. */
    fun measureMemoryThroughputMbps(): Double = try {
        val src = ByteArray(MEMORY_BUFFER_BYTES) { it.toByte() }
        val dst = ByteArray(MEMORY_BUFFER_BYTES)
        val start = System.nanoTime()
        var bytesCopied = 0L
        var passes = 0
        while (System.nanoTime() - start < MEMORY_TIME_CAP_NANOS) {
            System.arraycopy(src, 0, dst, 0, MEMORY_BUFFER_BYTES)
            bytesCopied += MEMORY_BUFFER_BYTES
            passes++
        }
        val elapsedNanos = System.nanoTime() - start
        if (elapsedNanos <= 0 || passes == 0) FAILED else toMbps(bytesCopied, elapsedNanos)
    } catch (e: Exception) {
        Timber.w(e, "System info: memory benchmark failed")
        FAILED
    }

    /**
     * Approximate sequential storage throughput in MB/s as (write, read), measured on a small temp
     * file in [dir]. The temp file is always deleted in `finally`.
     */
    fun measureStorageThroughputMbps(dir: File): Pair<Double, Double> {
        var file: File? = null
        return try {
            file = File(dir, "sysinfo_bench_${System.nanoTime()}.tmp")
            val block = ByteArray(STORAGE_BLOCK_BYTES) { it.toByte() }

            val writeStart = System.nanoTime()
            // WHY "rw" (buffered) + single fd.sync() below, not "rws": "rws" forces a synchronous
            // content+metadata flush to storage on EVERY write() call. With 64KB blocks that turns a
            // sequential-throughput measurement into per-block sync latency, collapsing the reported
            // write speed to ~tens of MB/s on devices whose real UFS write is hundreds of MB/s.
            RandomAccessFile(file, "rw").use { raf ->
                var written = 0
                while (written < STORAGE_FILE_BYTES) {
                    raf.write(block)
                    written += block.size
                }
                raf.fd.sync()
            }
            val writeNanos = System.nanoTime() - writeStart

            val readStart = System.nanoTime()
            RandomAccessFile(file, "r").use { raf ->
                val buf = ByteArray(STORAGE_BLOCK_BYTES)
                while (raf.read(buf) > 0) { /* drain */ }
            }
            val readNanos = System.nanoTime() - readStart

            val write = if (writeNanos <= 0) FAILED else toMbps(STORAGE_FILE_BYTES.toLong(), writeNanos)
            val read = if (readNanos <= 0) FAILED else toMbps(STORAGE_FILE_BYTES.toLong(), readNanos)
            write to read
        } catch (e: Exception) {
            Timber.w(e, "System info: storage benchmark failed")
            FAILED to FAILED
        } finally {
            file?.delete()
        }
    }

    private fun toMbps(bytes: Long, elapsedNanos: Long): Double {
        val seconds = elapsedNanos / 1_000_000_000.0
        val megabytes = bytes / (1024.0 * 1024.0)
        return megabytes / seconds
    }
}
