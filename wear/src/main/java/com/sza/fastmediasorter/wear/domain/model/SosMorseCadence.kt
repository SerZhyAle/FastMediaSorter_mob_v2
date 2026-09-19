package com.sza.fastmediasorter.wear.domain.model

/**
 * S3216: the Morse SOS cadence, `... --- ...`, as one flat list of on/off spans - watch-side mirror.
 *
 * Read by both halves of the watch signal, the siren and the white screen, so the display blinks WITH
 * the sound rather than merely at the same rate. Mirrored from the phone module's copy: the modules
 * share no source, and the owner's ask is one signal on two devices, so the numbers must not diverge.
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
