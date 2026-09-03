package com.sza.fastmediasorter.wear.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.wear.compose.foundation.lazy.ScalingLazyListState

/**
 * S2473: whether an overlay laid over the list behind [listState] belongs on screen right now.
 *
 * The signal is the scroll itself rather than a timer after the last touch: the controls are wanted
 * while the wearer waits, which is exactly the moment an idle timer would take them away. The video
 * player's own auto-hide is the opposite case - it hides what a resting hand is done with - so the
 * two deliberately do not share a mechanism.
 *
 * `derivedStateOf` is what keeps a scroll from recomposing the caller on every frame: the reading
 * changes twice per gesture, at its start and at its end, not once per pixel.
 *
 * The caller branches on the result and composes nothing while it is false, so a hidden overlay is
 * absent rather than invisible-and-still-tappable.
 *
 * **No fade.** A first attempt wrapped the overlay in a Compose visibility animation, and the S2250
 * ratchet refused it - correctly: on the watch an animation is a decision, because a stored setting
 * can switch animations off and every animating site owes that setting a branch. The dictation asked
 * for the controls to hide while the list moves, not for them to fade, so the animation was dropped
 * rather than argued for. Adding one later is a deliberate change that consults
 * `WearPreferencesRepository.isAnimationsDisabled`, the way the video player's controls already do.
 */
@Composable
fun rememberOverlayVisibleOnIdle(listState: ScalingLazyListState): Boolean {
    val visible by remember(listState) {
        derivedStateOf { !listState.isScrollInProgress }
    }
    return visible
}
