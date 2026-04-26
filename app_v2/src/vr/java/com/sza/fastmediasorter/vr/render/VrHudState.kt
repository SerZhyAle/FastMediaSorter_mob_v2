package com.sza.fastmediasorter.vr.render

/** Repeat-mode indicator drawn in the HUD. Mirrors ExoPlayer repeat states. */
enum class RepeatMode {
    OFF,
    ONE,
    ALL,
}

/**
 * Momentary "button was pressed" pulse drawn for ~300 ms in the HUD.
 * Tracks user-visible controller actions that do not have their own dedicated slot.
 */
enum class ActionBadge {
    PREV_FILE,
    NEXT_FILE,
    REWIND,
    FORWARD,
    OPEN_CONTROLS,
    REPEAT_TOGGLE,
}

/**
 * Immutable snapshot of HUD state for one compose pass.
 *
 * Each field is either a value (number / enum / string) when the slot is active,
 * or `null` / sentinel value when hidden. Drives [VrHudSceneComposer.draw] —
 * same input always paints the same Bitmap.
 *
 * `*UntilMs` fields use [System.currentTimeMillis] timestamps; the composer
 * compares against the current clock to decide visibility for momentary slots.
 */
data class VrHudState(
    val isPaused: Boolean? = null,
    /** Current playback position in ms; -1L when no source. */
    val positionMs: Long = -1L,
    /** Buffered length in ms; -1L when no source. */
    val bufferedMs: Long = -1L,
    /** Total duration in ms; -1L when no source. */
    val totalMs: Long = -1L,
    val volumePercent: Int? = null,
    val zoomFactor: Float? = null,
    val fileLabel: String? = null,
    val fileIndex: Int? = null,
    val fileTotal: Int? = null,
    val repeatMode: RepeatMode? = null,
    val seekDeltaSec: Int? = null,
    /** Current stereo/display mode shown at top-left (e.g. "2D", "360° SBS"). Null = hidden. */
    val stereoModeLabel: String? = null,
    /** When > now, draw a recenter flash overlay for the remaining duration. */
    val recenterFlashUntilMs: Long = 0L,
    /** When > now, draw the immersive-mode toggle banner. */
    val immersiveBadgeUntilMs: Long = 0L,
    /** When non-null, draw the action pulse. The composer fades it after 300 ms. */
    val actionBadge: ActionBadge? = null,
    val actionBadgeUntilMs: Long = 0L,
    /** Centre banner (transitional guard / first-run cheat). */
    val bannerText: String? = null,
    val bannerUntilMs: Long = 0L,
    /**
     * Per-slot timeout map collapsed to a single field: when EVERY slot timeout
     * is in the past AND no permanent slot (progress bar / repeat) is active,
     * the HUD layer can be hidden. The driver computes this from the maxima
     * of the *UntilMs fields.
     */
    val visibleUntilMs: Long = 0L,
) {
    companion object {
        val OFF = VrHudState()
    }
}
