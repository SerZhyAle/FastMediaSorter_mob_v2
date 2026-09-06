package com.sza.fastmediasorter.domain.model

/**
 * The three values the mirror keeps between runs (strategic S1924 2.6): the chosen zoom preset, the
 * horizontal flip and whether the glow field is lit.
 *
 * A projection of [AppSettings] rather than a second store - the values live in the shared settings the
 * way the neighbouring flashlight keeps its own (strategic 5.1). Carrying just these three keeps the
 * mirror's screen from depending on the whole settings surface.
 */
data class MirrorSettings(
    val zoomRatio: Float = DEFAULTS.mirrorZoomRatio,
    val horizontallyFlipped: Boolean = DEFAULTS.mirrorHorizontallyFlipped,
    val backlightOn: Boolean = DEFAULTS.mirrorBacklightOn,
) {
    private companion object {
        /**
         * Read off [AppSettings] rather than restated. A literal here would be a second copy of each
         * default, and the copy is what goes stale the day one of them changes - which is the drift
         * this projection exists to avoid, not to introduce.
         */
        val DEFAULTS = AppSettings()
    }
}
