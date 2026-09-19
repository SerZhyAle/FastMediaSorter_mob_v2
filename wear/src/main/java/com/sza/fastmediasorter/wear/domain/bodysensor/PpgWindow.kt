package com.sza.fastmediasorter.wear.domain.bodysensor

/**
 * S3113: one raw pulse-wave event exactly as the sensor delivered it.
 *
 * Every channel is kept because the private Samsung type carries no public layout: which index is the
 * pulse wave is decided from a recording on the owner's watch (research 03), never assumed here.
 * [timestampNanos] is the sensor event clock (`elapsedRealtimeNanos`), not wall-clock time.
 */
data class PpgSample(
    val timestampNanos: Long,
    val channels: List<Float>
)

/** S3113: one accelerometer event reduced to its magnitude, which is all motion gating reads. */
data class MotionSample(
    val timestampNanos: Long,
    val magnitude: Float
)

/**
 * S3113: which channel of a [PpgSample] carries the pulse wave, and whether it runs upside down.
 *
 * Owned by the source that produced the window, because it is a fact about that sensor: on Samsung's raw
 * type the pulse is channel 5 and falls with each beat, since more blood reflects less light (research 03).
 */
data class PulseWaveLayout(
    val channel: Int,
    val inverted: Boolean
)

/**
 * S3113: everything captured during one measurement window.
 *
 * Motion is recorded beside the pulse wave rather than judged at capture time, so a window kept on disk
 * can be re-judged when the quality rules change without asking the user to measure again.
 */
data class PpgWindow(
    val startedAtMillis: Long,
    val ppg: List<PpgSample>,
    val motion: List<MotionSample>,
    val layout: PulseWaveLayout
)

/** S3113: the state of one capture window, as a cold flow reports it. */
sealed interface PpgCapture {

    /** Registered and recording; [elapsedMillis] of [totalMillis] have passed. */
    data class Capturing(val elapsedMillis: Long, val totalMillis: Long) : PpgCapture

    /** The window is complete and the sensors are already released. */
    data class Captured(val window: PpgWindow) : PpgCapture

    /** No window, and [reason] selects the sentence the screen owes the user. */
    data class Unavailable(val reason: BodySensorUnavailableReason) : PpgCapture
}
