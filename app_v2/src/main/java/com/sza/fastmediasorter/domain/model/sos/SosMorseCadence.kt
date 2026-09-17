package com.sza.fastmediasorter.domain.model.sos

/**
 * S3216: the Morse SOS cadence, `... --- ...`, as one flat list of on/off spans.
 *
 * Declared once and read by both halves of the signal - the siren and the torch strobe - because the
 * owner's ask is that the light blinks WITH the sound rather than merely at the same rate. Two private
 * copies of these numbers would drift apart at the first tuning pass and the drift would be invisible in
 * a screenshot.
 *
 * Standard Morse proportions: a dash is three dots, the gap inside a letter is one dot, the gap between
 * letters three, and the gap before the pattern repeats seven.
 */
object SosMorseCadence {

    /** One span of the cadence: [durationMs] with the signal either engaged or silent. */
    data class Span(val durationMs: Long, val engaged: Boolean)

    const val DOT_MS = 150L
    private const val DASH_MS = DOT_MS * 3
    private const val LETTER_GAP_MS = DOT_MS * 3
    private const val CYCLE_GAP_MS = DOT_MS * 7

    /** One full `... --- ...` cycle, ending in the silence that separates it from the next one. */
    val cycle: List<Span> = buildList {
        letter(DOT_MS)
        add(Span(LETTER_GAP_MS, engaged = false))
        letter(DASH_MS)
        add(Span(LETTER_GAP_MS, engaged = false))
        letter(DOT_MS)
        add(Span(CYCLE_GAP_MS, engaged = false))
    }

    /** Total length of one cycle, which is what a caller needs to size a generated audio buffer. */
    val cycleMs: Long = cycle.sumOf { it.durationMs }

    /** Three marks of [markMs] separated by the one-dot gap that holds a letter together. */
    private fun MutableList<Span>.letter(markMs: Long) {
        repeat(MARKS_PER_LETTER) { index ->
            add(Span(markMs, engaged = true))
            if (index < MARKS_PER_LETTER - 1) {
                add(Span(DOT_MS, engaged = false))
            }
        }
    }

    private const val MARKS_PER_LETTER = 3
}
