package com.sza.fastmediasorter.wear.ui.apps.calculator

import android.content.Context

/**
 * S1719: the size the watch draws its calculator history at, and the memory of the user's choice.
 *
 * The same five steps the phone offers, mirrored as numbers rather than shared as a class: the watch
 * module does not depend on `app_v2`, and copying the phone's manager across would create a second
 * implementation to keep in step - which is the drift this ticket's design exists to avoid. Only the
 * values are the same; the driver is not, because a watch has no pinch.
 *
 * Stored in its own preferences file, so a chosen size survives leaving the calculator and restarting
 * the app.
 */
class WearCalculatorHistoryScale(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private var stepIndex: Int = prefs.getInt(KEY_STEP, DEFAULT_STEP_INDEX).coerceIn(0, STEPS_SP.lastIndex)

    /** The size the history is currently drawn at, in scale-independent pixels. */
    val currentSizeSp: Float get() = STEPS_SP[stepIndex]

    /**
     * Moves one step and answers with the new size.
     *
     * At either end the step is refused and the current size comes back unchanged, so a crown turned
     * past the last size does nothing rather than wrapping around to the smallest.
     */
    fun step(delta: Int): Float {
        val next = (stepIndex + delta).coerceIn(0, STEPS_SP.lastIndex)
        if (next != stepIndex) {
            stepIndex = next
            prefs.edit().putInt(KEY_STEP, stepIndex).apply()
        }
        return currentSizeSp
    }

    private companion object {
        const val PREFS_NAME = "wear_calculator_history_scale"
        const val KEY_STEP = "history_text_step"

        /**
         * The offered sizes, mirroring the phone's. The first is what the watch has always drawn, so a
         * user who never turns the crown sees no change.
         */
        val STEPS_SP = floatArrayOf(12f, 15f, 18f, 22f, 26f)

        const val DEFAULT_STEP_INDEX = 0
    }
}
