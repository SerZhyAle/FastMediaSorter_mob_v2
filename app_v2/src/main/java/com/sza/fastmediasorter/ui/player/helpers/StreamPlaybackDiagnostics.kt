package com.sza.fastmediasorter.ui.player.helpers

/**
 * S1127: per-session health aggregator for internet-stream playback. Pure Kotlin, holds no Media3
 * types, so the accounting is unit-testable off-device; [StreamDiagnosticsAnalyticsListener] is the
 * thin Media3 adapter that feeds it. The clock is injected ([currentTimeMs]) so tests drive TTFF and
 * stall durations deterministically instead of depending on wall time.
 *
 * Stall semantics: only re-buffering AFTER the first frame counts as a stall - the initial buffer is
 * startup, captured separately as time-to-first-frame.
 */
internal class StreamPlaybackDiagnostics(
    private val currentTimeMs: () -> Long,
) {
    private var preparedAtMs: Long = 0L
    private var firstFrameMs: Long = NOT_SET
    private var stallStartedAtMs: Long = 0L
    private var stallCountValue: Int = 0
    private var totalStallMsValue: Long = 0L
    private var droppedFramesValue: Int = 0
    private var decoderNameValue: String? = null
    private var hardwareDecoderValue: Boolean? = null

    /** Marks the start of the time-to-first-frame window (call right before `player.prepare()`). */
    fun onPrepared() {
        preparedAtMs = currentTimeMs()
    }

    fun onFirstFrameRendered() {
        if (firstFrameMs == NOT_SET && preparedAtMs > 0L) {
            firstFrameMs = currentTimeMs() - preparedAtMs
        }
    }

    /** A stall opens only once the stream is playing (first frame seen) and is not already stalled. */
    fun onStallStarted() {
        if (stallStartedAtMs == 0L && firstFrameMs != NOT_SET) {
            stallStartedAtMs = currentTimeMs()
        }
    }

    fun onStallEnded() {
        if (stallStartedAtMs != 0L) {
            totalStallMsValue += currentTimeMs() - stallStartedAtMs
            stallCountValue++
            stallStartedAtMs = 0L
        }
    }

    fun onDroppedFrames(count: Int) {
        droppedFramesValue += count
    }

    fun onDecoderInitialized(name: String, hardwareAccelerated: Boolean) {
        decoderNameValue = name
        hardwareDecoderValue = hardwareAccelerated
    }

    /** One greppable line for a Timber harvest; logged once on session teardown. */
    fun summary(): String {
        val ttff = if (firstFrameMs != NOT_SET) "${firstFrameMs}ms" else "n/a"
        val decoder = decoderNameValue?.let { "$it(${if (hardwareDecoderValue == true) "hw" else "sw"})" } ?: "unknown"
        return "ttff=$ttff stalls=$stallCountValue totalStallMs=$totalStallMsValue " +
            "dropped=$droppedFramesValue decoder=$decoder"
    }

    val timeToFirstFrameMs: Long get() = firstFrameMs
    val stallCount: Int get() = stallCountValue
    val totalStallMs: Long get() = totalStallMsValue
    val droppedFrames: Int get() = droppedFramesValue
    val decoderName: String? get() = decoderNameValue
    val isHardwareDecoder: Boolean? get() = hardwareDecoderValue

    private companion object {
        const val NOT_SET = -1L
    }
}
