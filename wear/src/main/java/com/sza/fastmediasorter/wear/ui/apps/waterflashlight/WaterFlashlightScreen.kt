package com.sza.fastmediasorter.wear.ui.apps.waterflashlight

import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.ui.common.KeepScreenOnEffect
import com.sza.fastmediasorter.wear.ui.common.LocalWearDateTimeFormatter
import com.sza.fastmediasorter.wear.ui.common.LocalWearUnitSystem
import com.sza.fastmediasorter.wear.ui.common.SystemShadeLockEffect
import com.sza.fastmediasorter.wear.ui.common.findActivity
import com.sza.fastmediasorter.wear.ui.player.common.rotaryActionSwallow
import kotlinx.coroutines.delay

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
 *
 * S3394: no single event leaves either, because a single event is what water produces. The way out is
 * three backs in quick succession, or a held key on a watch whose button reaches the app at all - the
 * decision is [WaterFlashlightExitGate]'s.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun WaterFlashlightScreen(
    onLeave: () -> Unit,
    viewModel: WaterFlashlightViewModel = hiltViewModel(),
) {
    KeepDisplayLit()
    val exitGate = remember { WaterFlashlightExitGate() }
    val currentLeave by rememberUpdatedState(onLeave)
    SystemShadeLockEffect(enabled = viewModel.locksSystemShade)

    // The back dispatcher claims BACK on its own and pops this route whatever the key handler below
    // returns (measured on the Galaxy Watch 7, 2026-09-23), so the gate has to decide here as well.
    BackHandler {
        val leaving = exitGate.onBack(System.currentTimeMillis())
        if (leaving) {
            currentLeave()
        }
    }

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
            // Every key is consumed, so none reaches the navigation below; whether one ends the light
            // is the gate's decision alone.
            .onPreviewKeyEvent { event ->
                exitGate.onScreenKey(event, currentLeave)
                true
            }
            // Rotation is swallowed rather than acted on: on a watch whose bezel is capacitive the
            // circular edge gesture arrives here as a rotary event, which is wet-glass input like any
            // other. The owner ruled on 2026-09-09 (S2812) that neither edition reacts to it, so the
            // keys carry the exit alone. Consumed rather than deleted so the event stops here.
            .rotaryActionSwallow(),
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
                style = MaterialTheme.typography.title2,
            )
            Text(
                text = stringResource(R.string.wear_water_flashlight_exit_hint),
                color = Color.Black,
                style = MaterialTheme.typography.caption2,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * Feeds one key event to the gate and leaves when the gate says so.
 *
 * BACK is a single event here, never a timed press. From Android 16 on - which this app targets - the
 * dispatcher hands every back to the [BackHandler] as well, so the key half is ignored there rather
 * than counted twice; below Android 16 the consumed key never reaches the dispatcher and is counted
 * here, or no back could ever leave.
 */
private fun WaterFlashlightExitGate.onScreenKey(event: KeyEvent, onLeave: () -> Unit) {
    val nowMs = System.currentTimeMillis()
    val leaving = when {
        event.key == Key.Back ->
            event.type == KeyEventType.KeyUp && !backReachesDispatcher && onBack(nowMs)
        event.type == KeyEventType.KeyDown -> {
            onKeyDown(
                keyCode = event.key.keyCode,
                nowMs = nowMs,
                longPress = event.nativeKeyEvent.isLongPress || event.nativeKeyEvent.repeatCount > 0,
            )
            false
        }
        event.type == KeyEventType.KeyUp -> onKeyUp(event.key.keyCode, nowMs)
        else -> false
    }
    if (leaving) {
        onLeave()
    }
}

private val backReachesDispatcher = Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA

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
private val HINT_SIDE_PADDING = 16.dp
