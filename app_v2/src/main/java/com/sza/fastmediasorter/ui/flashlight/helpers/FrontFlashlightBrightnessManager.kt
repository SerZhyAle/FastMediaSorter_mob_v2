package com.sza.fastmediasorter.ui.flashlight.helpers

/**
 * Owns the single brightness step the front flashlight is on (strategic S2777 ADR-1). Both inputs the
 * screen offers - a tap on a step and a vertical swipe - move this one index, so the highlighted step
 * can never disagree with the light actually on the window.
 *
 * The step values are window brightness, never the device setting (S1796 ADR-2), and they are not
 * persisted: the flashlight comes back at full brightness on every run.
 */
class FrontFlashlightBrightnessManager {

    var currentLevel: Int = LEVELS.lastIndex
        private set

    val levelCount: Int get() = LEVELS.size

    fun setLevel(level: Int) {
        currentLevel = level.coerceIn(0, LEVELS.lastIndex)
    }

    fun shiftLevels(steps: Int) {
        setLevel(currentLevel + steps)
    }

    fun currentBrightness(): Float = LEVELS[currentLevel]

    private companion object {
        /**
         * The ends repeat the range the swipe gesture already covered before the steps existed, so no
         * glow this screen could produce is lost. The dimmest step is deliberately not black: a dark
         * screen reads as a crash, not as the lowest setting of a lamp.
         */
        val LEVELS = floatArrayOf(0.05f, 0.25f, 0.5f, 0.75f, 1.0f)
    }
}
