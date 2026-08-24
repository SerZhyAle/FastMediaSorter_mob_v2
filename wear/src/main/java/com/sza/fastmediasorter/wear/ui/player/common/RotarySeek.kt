package com.sza.fastmediasorter.wear.ui.player.common

import androidx.compose.foundation.focusable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.rotary.onRotaryScrollEvent

/**
 * S1683: the bezel's action arrives as a lambda and this file never learns what it does. The owner
 * chose one action out of several plausible ones, so a later ticket can rebind rotation without
 * editing a player screen.
 *
 * The event is always consumed. Returning false would hand the same rotation to whatever scrollable
 * container sits underneath, and the audio player hosts one - the bezel would then do two things at
 * once, which is the behaviour this modifier exists to prevent.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.rotaryAction(
    focusRequester: FocusRequester,
    onScroll: (Float) -> Unit
): Modifier = this
    .onRotaryScrollEvent { event ->
        onScroll(event.verticalScrollPixels)
        true
    }
    .focusRequester(focusRequester)
    .focusable()

/**
 * Rotary events are delivered to the focused composable only, and nothing on a watch grants focus by
 * touch, so a screen that never asks for it receives no rotation at all.
 */
@Composable
fun rememberRotaryFocus(): FocusRequester {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(focusRequester) {
        focusRequester.requestFocus()
    }
    return focusRequester
}

/**
 * Ready-made binding for a screen that wants whole steps rather than a stream of pixels: it owns the
 * focus request and the accumulator, so a player screen states what a step does and nothing else.
 */
@Composable
fun Modifier.rotaryActionSteps(onStep: (Int) -> Unit): Modifier {
    val focusRequester = rememberRotaryFocus()
    val accumulator = remember { RotaryStepAccumulator() }
    return this.rotaryAction(focusRequester) { delta ->
        accumulator.add(delta, onStep)
    }
}

/**
 * Turns a stream of rotary pixels into whole steps.
 *
 * One detent of a physical bezel arrives as several events, and a rotation of the emulated crown
 * arrives as a continuous burst; passing each event straight to an action would fire it dozens of
 * times per turn. Travel accumulates until it crosses [stepPixels], and only then is a step reported.
 * The residue is kept, so a slow turn still reaches a step instead of being rounded away, and the
 * accumulator resets on a direction change so a turn back does not have to undo the pending travel
 * first.
 */
class RotaryStepAccumulator(private val stepPixels: Float = DEFAULT_STEP_PIXELS) {

    private var pending = 0f

    /** Reports one call to [onStep] per whole step of travel; the sign says which way the bezel turned. */
    fun add(delta: Float, onStep: (Int) -> Unit) {
        if (delta == 0f) return
        if (pending != 0f && (pending > 0f) != (delta > 0f)) {
            pending = 0f
        }
        pending += delta
        while (pending >= stepPixels) {
            pending -= stepPixels
            onStep(1)
        }
        while (pending <= -stepPixels) {
            pending += stepPixels
            onStep(-1)
        }
    }

    private companion object {
        /**
         * Starting value, to be confirmed on a real bezel and adjusted there: a rotary event reports
         * pixels scaled by `ViewConfiguration.getScaledVerticalScrollFactor`, which is a device
         * property, so the pixels per detent cannot be derived here.
         */
        const val DEFAULT_STEP_PIXELS = 120f
    }
}
