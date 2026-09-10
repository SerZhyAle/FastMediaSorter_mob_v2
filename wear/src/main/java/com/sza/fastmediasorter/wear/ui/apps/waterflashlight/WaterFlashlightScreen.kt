package com.sza.fastmediasorter.wear.ui.apps.waterflashlight

import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.wear.compose.material.Text
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.ui.common.KeepScreenOnEffect
import com.sza.fastmediasorter.wear.ui.common.LocalWearDateTimeFormatter
import com.sza.fastmediasorter.wear.ui.common.LocalWearUnitSystem
import com.sza.fastmediasorter.wear.ui.common.SystemShadeLockEffect
import com.sza.fastmediasorter.wear.ui.common.findActivity
import com.sza.fastmediasorter.wear.ui.player.common.rememberRotaryFocus
import kotlinx.coroutines.delay
import timber.log.Timber

/**
 * The water flashlight on the watch: the display itself is the light, and no touch closes it
 * (strategic S2516).
 *
 * The watch has no flash unit, so the phone's torch half has no counterpart here - the white field is
 * the whole light. The owner's reason for the program is that his watch cannot run its water-lock mode
 * and the system flashlight at the same time; this screen is the app's own answer, not a replacement
 * for that firmware mode, which no third-party app can reach.
 *
 * S2812 closed the two remaining ways a wet screen still reached the system: the circular edge gesture no
 * longer leaves, and in the edition that does not go through the store the system shade is held shut for
 * as long as the light is on.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun WaterFlashlightScreen(
    onLeave: () -> Unit,
    viewModel: WaterFlashlightViewModel = hiltViewModel(),
) {
    KeepDisplayLit()
    SystemShadeLockEffect(enabled = viewModel.locksSystemShade)
    LaunchedEffect(viewModel.locksSystemShade) {
        Timber.d("S2812: water flashlight shown, locksSystemShade=%s", viewModel.locksSystemShade)
    }

    val focusRequester = rememberRotaryFocus()
    val clockText = rememberMinuteClock()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            // Consumed on the INITIAL pass so nothing downstream or upstream sees it: a plain
            // no-op handler would still leave the swipe-to-dismiss gesture free to close the screen,
            // and that gesture is exactly the wet-glass input this program exists to survive.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                    }
                }
            }
            // Every delivered key leaves, rather than one named code: which button a watch reports is
            // a per-model fact this repository has never measured, and being unable to leave is a far
            // worse failure than leaving by an unexpected button (strategic §6.1).
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp) {
                    Timber.d("S2516: wear water flashlight key %d", event.nativeKeyEvent.keyCode)
                    onLeave()
                }
                true
            }
            // Rotation is swallowed rather than acted on: on a watch whose bezel is capacitive the
            // circular edge gesture arrives here as a rotary event, which is wet-glass input like any
            // other. The owner ruled on 2026-09-09 (S2812) that neither edition reacts to it, so the
            // keys carry the exit alone. Consumed rather than deleted so the event stops here.
            .onRotaryScrollEvent { true }
            .focusRequester(focusRequester)
            .focusable(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = HINT_SIDE_PADDING),
        ) {
            // Black on white, fixed rather than themed: the screen is a lamp, so it stays white in a
            // dark theme, and text taken from the theme would go white with it and vanish.
            Text(
                text = clockText,
                color = Color.Black,
                fontSize = CLOCK_TEXT_SIZE,
            )
            Text(
                text = stringResource(R.string.wear_water_flashlight_locked_hint),
                color = Color.Black,
                fontSize = HINT_TEXT_SIZE,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * Holds the display on at full brightness for as long as the screen is shown, and gives both back on
 * the way out - only this window's brightness is touched, never the watch's own setting.
 *
 * The keep-awake half goes through the shared reference-counted effect: the flag lives on the window,
 * so a screen setting it by hand would clear another screen's claim on navigation.
 */
@Composable
private fun KeepDisplayLit() {
    KeepScreenOnEffect(enabled = true)
    val context = LocalContext.current
    DisposableEffect(context) {
        val window = context.findActivity()?.window
        window?.let { it.attributes = it.attributes.apply { screenBrightness = MAX_BRIGHTNESS } }
        onDispose {
            window?.let {
                it.attributes = it.attributes.apply {
                    screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                }
            }
        }
    }
}

/**
 * The formatted time, re-read at each minute boundary rather than on a fixed tick, so the watch is not
 * woken sixty times for one visible change.
 */
@Composable
private fun rememberMinuteClock(): String {
    // S2795: the app's own measurement system decides the clock length here, not the watch setting -
    // this screen is a full-screen clock, so a disagreement with the rest of the app is unmissable.
    val formatter = LocalWearDateTimeFormatter.current
    val system = LocalWearUnitSystem.current
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(MINUTE_MS - System.currentTimeMillis() % MINUTE_MS)
        }
    }
    return remember(nowMillis, system) { formatter.formatTime(nowMillis, system) }
}

private const val MAX_BRIGHTNESS = 1.0f
private const val MINUTE_MS = 60_000L
private val CLOCK_TEXT_SIZE = 18.sp
private val HINT_TEXT_SIZE = 12.sp
private val HINT_SIDE_PADDING = 16.dp
