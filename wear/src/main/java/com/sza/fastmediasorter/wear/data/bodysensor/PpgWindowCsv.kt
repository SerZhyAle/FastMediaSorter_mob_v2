package com.sza.fastmediasorter.wear.data.bodysensor

import com.sza.fastmediasorter.wear.domain.bodysensor.MotionSample
import com.sza.fastmediasorter.wear.domain.bodysensor.PpgSample
import com.sza.fastmediasorter.wear.domain.bodysensor.PpgWindow
import com.sza.fastmediasorter.wear.domain.bodysensor.PulseWaveLayout

/**
 * S3113: the on-disk text form of one capture window.
 *
 * Plain CSV rather than a binary or Room blob so that a window pulled off the watch with `run-as` can be
 * read by a person, a spreadsheet or a script, and copied verbatim into a unit-test fixture. Kotlin's
 * `Float.toString` is the shortest text that parses back to the same bits, so a round trip is exact.
 */
object PpgWindowCsv {

    private const val HEADER_V2 = "# fms-ppg-window v2"
    private const val HEADER_V1 = "# fms-ppg-window v1"
    private const val KEY_STARTED_AT = "startedAtMillis"
    private const val KEY_CHANNEL = "pulseChannel"
    private const val KEY_INVERTED = "inverted"
    private const val KIND_PPG = "ppg"
    private const val KIND_MOTION = "motion"
    private const val MOTION_COLUMNS = 3
    private const val V1_PULSE_CHANNEL = 5

    /**
     * Version 1 files were written by the first capture build (2.60.9162.346), before the source decoded
     * Samsung's integer counts out of float bits and before a window carried its layout. The owner's
     * first cuff calibration lives in one, so they are read rather than refused: bits decoded, layout the
     * one that sensor has (research 03).
     */
    private val V1_LAYOUT = PulseWaveLayout(channel = V1_PULSE_CHANNEL, inverted = true)

    fun format(window: PpgWindow): String = buildString {
        append(HEADER_V2)
        append(' ').append(KEY_STARTED_AT).append('=').append(window.startedAtMillis)
        append(' ').append(KEY_CHANNEL).append('=').append(window.layout.channel)
        append(' ').append(KEY_INVERTED).append('=').append(window.layout.inverted)
        append('\n')
        window.ppg.forEach { sample ->
            append(KIND_PPG).append(',').append(sample.timestampNanos)
            sample.channels.forEach { value -> append(',').append(value) }
            append('\n')
        }
        window.motion.forEach { sample ->
            append(KIND_MOTION).append(',').append(sample.timestampNanos).append(',').append(sample.magnitude)
            append('\n')
        }
    }

    /** Rows of an unknown kind are skipped, so a later format may add one without breaking this reader. */
    fun parse(text: String): PpgWindow {
        val lines = text.lineSequence().filter { it.isNotBlank() }.toList()
        val header = lines.firstOrNull().orEmpty()
        val isV1 = header.startsWith(HEADER_V1)
        require(isV1 || header.startsWith(HEADER_V2)) { "Not a PPG window file" }
        val fields = headerFields(header)
        val ppg = mutableListOf<PpgSample>()
        val motion = mutableListOf<MotionSample>()
        lines.drop(1).map { it.split(',') }.forEach { cells ->
            when {
                cells.first() == KIND_PPG -> ppg += ppgSampleOf(cells, decodeBits = isV1)
                cells.first() == KIND_MOTION && cells.size == MOTION_COLUMNS -> motion += MotionSample(
                    timestampNanos = cells[1].toLong(),
                    magnitude = cells[2].toFloat()
                )
            }
        }
        return PpgWindow(
            startedAtMillis = requiredField(fields, KEY_STARTED_AT).toLong(),
            ppg = ppg,
            motion = motion,
            layout = if (isV1) V1_LAYOUT else layoutOf(fields)
        )
    }

    private fun headerFields(header: String): Map<String, String> = header.split(' ')
        .mapNotNull { token -> token.split('=').takeIf { it.size == 2 }?.let { it[0] to it[1] } }
        .toMap()

    private fun layoutOf(fields: Map<String, String>): PulseWaveLayout = PulseWaveLayout(
        channel = requiredField(fields, KEY_CHANNEL).toInt(),
        inverted = requiredField(fields, KEY_INVERTED).toBooleanStrict()
    )

    /** Every malformation is an [IllegalArgumentException], so a reader has one failure type to handle. */
    private fun requiredField(fields: Map<String, String>, key: String): String =
        requireNotNull(fields[key]) { "PPG window header lacks $key" }

    private fun ppgSampleOf(cells: List<String>, decodeBits: Boolean): PpgSample = PpgSample(
        timestampNanos = cells[1].toLong(),
        channels = cells.drop(2).map { cell ->
            val value = cell.toFloat()
            if (decodeBits) value.toRawBits().toFloat() else value
        }
    )
}
