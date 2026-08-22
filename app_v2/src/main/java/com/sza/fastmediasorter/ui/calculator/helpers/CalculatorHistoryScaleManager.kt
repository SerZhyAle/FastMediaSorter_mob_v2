package com.sza.fastmediasorter.ui.calculator.helpers

import android.annotation.SuppressLint
import android.content.Context
import android.util.TypedValue
import android.view.ScaleGestureDetector
import android.widget.TextView

/**
 * S1719: the calculator's history is scaled by a pinch, and the size it lands on is remembered.
 *
 * Deliberately stepped rather than continuous. Strategic §2.3 asks for a *noticeable* step because the
 * point is to make a result readable, not to choose a type size: a free scale lands on values the user
 * cannot reproduce, and on a pinch that ends between two of them the text ends up a size nobody picked.
 *
 * The size is stored in the calculator's own preferences, so it survives a rotation, a restart and the
 * activity recreation that an orientation change performs (S1549).
 */
class CalculatorHistoryScaleManager(
    context: Context,
    private val history: TextView,
) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private var stepIndex: Int = prefs.getInt(KEY_STEP, DEFAULT_STEP_INDEX).coerceIn(0, STEPS_SP.lastIndex)

    private val detector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                // One step per gesture, taken when the pinch has clearly opened or closed. Reacting to
                // every callback would step several times inside one finger movement.
                when {
                    detector.scaleFactor > STEP_UP_FACTOR -> stepBy(1)
                    detector.scaleFactor < STEP_DOWN_FACTOR -> stepBy(-1)
                    else -> return false
                }
                return true
            }
        },
    )

    /** The size the history is currently rendered at, in scale-independent pixels. */
    val currentSizeSp: Float get() = STEPS_SP[stepIndex]

    /** Applies the remembered size. Call once the view exists, and again after a recreation. */
    fun apply() {
        history.setTextSize(TypedValue.COMPLEX_UNIT_SP, currentSizeSp)
    }

    /**
     * Starts listening for the pinch on the history.
     *
     * The listener returns true only while a scale gesture is in progress, so an ordinary drag still
     * scrolls the history rather than being swallowed here.
     */
    @SuppressLint("ClickableViewAccessibility")
    fun attachTo(target: android.view.View) {
        apply()
        target.setOnTouchListener { _, event ->
            detector.onTouchEvent(event)
            detector.isInProgress
        }
    }

    private fun stepBy(delta: Int) {
        val next = (stepIndex + delta).coerceIn(0, STEPS_SP.lastIndex)
        if (next == stepIndex) return
        stepIndex = next
        prefs.edit().putInt(KEY_STEP, stepIndex).apply()
        apply()
    }

    private companion object {
        const val PREFS_NAME = "calculator_history_scale"
        const val KEY_STEP = "history_text_step"

        /**
         * The offered sizes. The smallest is the size the history has always had, so a user who never
         * pinches sees no change; the largest still leaves a full result on one line at the widths this
         * screen is laid out for.
         */
        val STEPS_SP = floatArrayOf(12f, 15f, 18f, 22f, 26f)

        const val DEFAULT_STEP_INDEX = 0

        /** How far a pinch must open or close before it counts as one step. */
        const val STEP_UP_FACTOR = 1.15f
        const val STEP_DOWN_FACTOR = 0.87f
    }
}
