package com.sza.fastmediasorter.wear.ui.player.common

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import com.sza.fastmediasorter.wear.ui.common.LocalWearRotaryFocusStack
import kotlinx.coroutines.launch
import timber.log.Timber

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
 *
 * Asking once is not enough where consumers overlap (S2763). The request is therefore keyed on being the
 * newest live consumer in [LocalWearRotaryFocusStack]: a dialog or an action cloud opening over a list
 * takes the crown while it is there, and closing it makes the list top again, which re-fires the request
 * below. With no stack provided the condition is constant and this behaves exactly as it did before.
 */
@Composable
fun rememberRotaryFocus(): FocusRequester {
    val focusRequester = remember { FocusRequester() }
    val stack = LocalWearRotaryFocusStack.current
    val token = remember { Any() }

    DisposableEffect(stack, token) {
        stack?.push(token)
        onDispose { stack?.remove(token) }
    }

    val owned = stack == null || stack.isTop(token)
    LaunchedEffect(owned) {
        if (owned) {
            Timber.d("S3263: rotary focus claimed by the top consumer")
            focusRequester.requestFocus()
        }
    }
    return focusRequester
}

/**
 * Ready-made binding for a screen that wants whole steps rather than a stream of pixels: it owns the
 * focus request and the accumulator, so a player screen states what a step does and nothing else.
 *
 * Each emitted step also ticks the haptic engine, which is what makes a bezel turn feel like detents
 * rather than a silent slide; a caller whose action already produces its own feedback passes
 * [hapticFeedbackEnabled] = false to avoid a double tick.
 */
@Composable
fun Modifier.rotaryActionSteps(
    hapticFeedbackEnabled: Boolean = true,
    onStep: (Int) -> Unit
): Modifier {
    val focusRequester = rememberRotaryFocus()
    val accumulator = remember { RotaryStepAccumulator() }
    val haptic = LocalHapticFeedback.current
    return this.rotaryAction(focusRequester) { delta ->
        accumulator.add(delta) { step ->
            Timber.d("S3263: rotary detent emitted at 48f with haptics=$hapticFeedbackEnabled")
            if (hapticFeedbackEnabled) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
            onStep(step)
        }
    }
}

/**
 * Claims rotary focus and eats every crown turn that reaches it.
 *
 * A non-scrolling full-screen surface still has to register in [LocalWearRotaryFocusStack]: without a
 * focused node the crown keeps driving whatever scrollable was focused before, so an overlay that only
 * looks modal is not. This replaces the inline `focusRequester(..) + focusable() + onRotaryScrollEvent`
 * triple that each such screen used to spell out for itself.
 */
@Composable
fun Modifier.rotaryActionSwallow(focusRequester: FocusRequester = rememberRotaryFocus()): Modifier =
    this.rotaryAction(focusRequester) { }

/**
 * S2049: this pinned Wear Compose Foundation build wires no rotary input into `ScalingLazyColumn` at
 * all - its sources carry no rotary reference anywhere in the module - so a scrollable screen needs the
 * same explicit focus request the stepped and seek variants above already use, applied to a plain scroll
 * instead of a stepped or single-consumer action.
 */
@Composable
fun Modifier.rotaryActionScroll(listState: ScalingLazyListState): Modifier {
    val focusRequester = rememberRotaryFocus()
    val coroutineScope = rememberCoroutineScope()
    return this.rotaryAction(focusRequester) { delta ->
        coroutineScope.launch { listState.scrollBy(delta) }
    }
}

/**
 * Rotary scroll overload for a plain [ScrollState] container, such as an inscribed square scroll box.
 */
@Composable
fun Modifier.rotaryActionScroll(scrollState: ScrollState): Modifier {
    val focusRequester = rememberRotaryFocus()
    val coroutineScope = rememberCoroutineScope()
    return this.rotaryAction(focusRequester) { delta ->
        coroutineScope.launch { scrollState.scrollBy(delta) }
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
         * A rotary event reports pixels scaled by `ViewConfiguration.getScaledVerticalScrollFactor`,
         * a device property, so the pixels per physical detent cannot be derived here. 48f is the
         * value `docs/ui/WEAR_UI_COMPONENT_PATTERNS.md` section 3.2 fixes for the whole module, chosen
         * for a responsive detent feel rather than measured off one watch.
         */
        const val DEFAULT_STEP_PIXELS = 48f
    }
}
