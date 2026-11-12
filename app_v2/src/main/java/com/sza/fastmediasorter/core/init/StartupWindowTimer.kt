package com.sza.fastmediasorter.core.init

import android.os.Build
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import timber.log.Timber

/**
 * Times the stretch of process start that runs before Hilt exists - the end of
 * `attachBaseContext`, every ContentProvider `onCreate`, and the field injection inside
 * `Application.super.onCreate()` - and samples the main-thread stack while that stretch stalls.
 *
 * A plain object rather than an injected class because its first mark is taken before the
 * Hilt component is built. Every line starts with `Startup info:`, a prefix the release file
 * tree persists below its WARN threshold, so a remote log carries it from every build type.
 */
object StartupWindowTimer {

    private const val SAMPLE_INTERVAL_MS = 2_000L
    private const val STALL_THRESHOLD_MS = 2_000L
    private const val MAX_SAMPLES = 10
    private const val MAX_FRAMES = 12
    private const val UNKNOWN = -1L

    @Volatile
    private var attachStart = UNKNOWN

    @Volatile
    private var attachEnd = UNKNOWN

    @Volatile
    private var onCreateEnter = UNKNOWN

    @Volatile
    private var finished = false

    fun markAttachStart() {
        attachStart = SystemClock.uptimeMillis()
    }

    fun markAttachEnd() {
        val now = SystemClock.uptimeMillis()
        attachEnd = now
        startSampler(Looper.getMainLooper().thread, now)
    }

    fun markOnCreateEnter() {
        onCreateEnter = SystemClock.uptimeMillis()
    }

    fun markInjected() {
        if (finished) return
        finished = true
        val processStart = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Process.getStartUptimeMillis()
        } else {
            UNKNOWN
        }
        Timber.i(
            formatWindowLine(processStart, attachStart, attachEnd, onCreateEnter, SystemClock.uptimeMillis())
        )
    }

    private fun startSampler(mainThread: Thread, windowStart: Long) {
        val sampler = Thread({ sampleWhileStalled(mainThread, windowStart) }, "fms-startup-sampler")
        sampler.isDaemon = true
        sampler.start()
    }

    private fun sampleWhileStalled(mainThread: Thread, windowStart: Long) {
        var samples = 0
        var previous: List<StackTraceElement> = emptyList()
        try {
            while (!finished && samples < MAX_SAMPLES) {
                Thread.sleep(SAMPLE_INTERVAL_MS)
                val waited = SystemClock.uptimeMillis() - windowStart
                if (!finished && waited >= STALL_THRESHOLD_MS) {
                    val frames = mainThread.stackTrace.toList()
                    samples++
                    // A stack unchanged since the last sample adds no evidence, only lines.
                    if (frames != previous) Timber.i(formatStallLine(waited, frames, MAX_FRAMES))
                    previous = frames
                }
            }
        } catch (_: InterruptedException) {
            // Nobody interrupts this thread; an interrupt can only mean the process is going away.
            Thread.currentThread().interrupt()
        }
    }

    /** One line naming every segment of the window in milliseconds; `n/a` where a mark is missing. */
    internal fun formatWindowLine(
        processStart: Long,
        attachStart: Long,
        attachEnd: Long,
        onCreateEnter: Long,
        injected: Long
    ): String =
        "Startup info: window" +
            " preAttach=${span(processStart, attachStart)}" +
            " attach=${span(attachStart, attachEnd)}" +
            " providers=${span(attachEnd, onCreateEnter)}" +
            " inject=${span(onCreateEnter, injected)}" +
            " total=${span(processStart, injected)}"

    /** The top [maxFrames] frames of the main thread, innermost first, after [waitedMs] of waiting. */
    internal fun formatStallLine(waitedMs: Long, frames: List<StackTraceElement>, maxFrames: Int): String =
        "Startup info: stall main thread waited=${waitedMs}ms at:\n" +
            frames.take(maxFrames).joinToString("\n") { "    at $it" }

    private fun span(from: Long, to: Long): String =
        if (from < 0 || to < 0) "n/a" else "${to - from}ms"
}
