package com.sza.fastmediasorter.ui.player.helpers

/**
 * S1510: one reading of a live stream session, taken on a fixed cadence.
 *
 * Deliberately free of Media3 types so the arithmetic that turns two of these into a diagnostic line
 * is plain Kotlin and testable - the same split [StreamPlaybackDiagnostics] already uses for the
 * end-of-session summary.
 *
 * [renderedFrames] is a running total, not a rate: the rate is a difference between two samples, which
 * is the whole point. It is null until the video renderer is enabled, so an audio-only stream and the
 * first moments of a video one are representable rather than reported as zero frames.
 */
internal data class StreamStatsSample(
    val atMs: Long,
    val renderedFrames: Int?,
    val bitrateEstimateBps: Long,
    val width: Int,
    val height: Int,
    val formatBitrateBps: Int,
)

/**
 * S1510: turns two consecutive samples into the line that goes to the log.
 *
 * The prefix is not cosmetic. The release log persists nothing below WARN except messages starting
 * with one of a registered set of prefixes, and `Stream diag: ` is one of them - a line spelled any
 * other way silently never reaches the file, which from outside looks exactly like a tick that does
 * not run (S1468).
 */
internal object StreamStatsFormatter {

    const val PREFIX = "Stream diag: "

    /**
     * Null when the pair cannot yield a rate: no previous sample yet, or a non-advancing clock. Both
     * are ordinary at the start of a session rather than errors, and a line reading "0.0 fps" there
     * would be a false statement about the stream rather than a missing one about the sampler.
     */
    fun line(previous: StreamStatsSample?, current: StreamStatsSample): String? {
        val elapsedMs = current.atMs - (previous ?: return null).atMs
        return if (elapsedMs <= 0L) {
            null
        } else {
            val fps = renderedFps(previous.renderedFrames, current.renderedFrames, elapsedMs)
            buildString {
                append(PREFIX)
                append("stats fps=")
                append(fps?.let { formatOneDecimal(it) } ?: "n/a")
                append(" net=")
                append(current.bitrateEstimateBps / BITS_PER_KBIT)
                append("kbps rung=")
                append(rung(current))
                append(" over=")
                append(elapsedMs)
                append("ms")
            }
        }
    }

    /**
     * Null when either end of the pair has no counter, or when the counter went backwards - which it
     * does whenever the renderer is re-enabled mid-session and starts a fresh count. Reporting the
     * negative difference as a rate would invent a stall that did not happen.
     */
    fun renderedFps(previousFrames: Int?, currentFrames: Int?, elapsedMs: Long): Double? {
        if (previousFrames == null || currentFrames == null || elapsedMs <= 0L) return null
        val delta = currentFrames - previousFrames
        return if (delta < 0) null else delta * MS_PER_SECOND / elapsedMs.toDouble()
    }

    private fun rung(sample: StreamStatsSample): String {
        if (sample.width <= 0 || sample.height <= 0) return "n/a"
        val bitrate = if (sample.formatBitrateBps > 0) {
            "@${sample.formatBitrateBps / BITS_PER_KBIT}kbps"
        } else {
            ""
        }
        return "${sample.width}x${sample.height}$bitrate"
    }

    private fun formatOneDecimal(value: Double): String {
        val scaled = kotlin.math.round(value * TENTHS).toLong()
        return "${scaled / TENTHS}.${scaled % TENTHS}"
    }

    private const val MS_PER_SECOND = 1000.0
    private const val BITS_PER_KBIT = 1000
    private const val TENTHS = 10
}
