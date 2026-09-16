package com.sza.fastmediasorter.wear.ui.apps.tourist

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.ui.player.common.rememberRotaryFocus

private val HINT_TEXT_COLOR = Color(0xFFFFCC00.toInt())

/**
 * S3115: touch lock for the tourist dashboard, built on the water-flashlight pattern (S2516 / S2812).
 *
 * It draws nothing: the telemetry underneath stays visible and live while swimming or running, which is
 * the whole point of the lock - the reading must be watchable while the wrist keeps touching things.
 * Every pointer event is consumed on the INITIAL pass, so neither the content below nor the
 * swipe-to-dismiss gesture above ever sees it, and rotation is swallowed for the same reason.
 *
 * Any delivered key leaves the lock rather than one named code: which button a watch reports is a
 * per-model fact, and being unable to unlock is far worse than unlocking by an unexpected button.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TouristLockOverlay(
    onUnlock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = rememberRotaryFocus()

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                    }
                }
            }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp) {
                    onUnlock()
                }
                true
            }
            .onRotaryScrollEvent { true }
            .focusRequester(focusRequester)
            .focusable(),
    )
}

/**
 * S3115: the hint that takes the Lock control's own slot while the lock is on - the only thing telling
 * the owner that a hardware key is the way out.
 */
@Composable
fun TouristUnlockHint(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.wear_tourist_press_button_to_unlock),
        style = MaterialTheme.typography.caption2,
        color = HINT_TEXT_COLOR,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth(),
    )
}
