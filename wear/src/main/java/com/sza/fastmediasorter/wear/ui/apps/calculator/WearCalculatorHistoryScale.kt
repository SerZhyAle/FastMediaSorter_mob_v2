package com.sza.fastmediasorter.wear.ui.apps.calculator

/**
 * S1719: the sizes the watch draws its calculator history at.
 *
 * The same five steps the phone offers, mirrored as numbers rather than shared as a class: the watch
 * module does not depend on `app_v2`, and copying the phone's manager across would create a second
 * implementation to keep in step - which is the drift this ticket's design exists to avoid. Only the
 * values are the same; the driver is not, because a watch has no pinch.
 *
 * S3954: the chosen step is stored in the watch's DataStore through [CalculatorViewModel] rather than
 * in a preferences file read here - this object was built during composition, so its first read
 * blocked the main thread on disk.
 */
class WearCalculatorHistoryScale private constructor() {

    companion object {
        /**
         * The offered sizes, mirroring the phone's. The first is what the watch has always drawn, so a
         * user who never turns the crown sees no change.
         */
        private val STEPS_SP = floatArrayOf(12f, 15f, 18f, 22f, 26f)

        const val DEFAULT_STEP_INDEX = 0

        /** The size of [step], in scale-independent pixels; a stored step the table no longer has is clamped. */
        fun sizeSpOf(step: Int): Float = STEPS_SP[clamped(step)]

        /**
         * The step [delta] away from [step].
         *
         * At either end the step is refused and the same step comes back, so a crown turned past the last
         * size does nothing rather than wrapping around to the smallest.
         */
        fun stepped(step: Int, delta: Int): Int = clamped(step + delta)

        private fun clamped(step: Int): Int = step.coerceIn(0, STEPS_SP.lastIndex)
    }
}
