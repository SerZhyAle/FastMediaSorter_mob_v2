package com.sza.fastmediasorter.wear.ui.apps.sos

import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.model.SosMode
import com.sza.fastmediasorter.wear.ui.common.KeepScreenOnEffect
import com.sza.fastmediasorter.wear.ui.common.findActivity
import com.sza.fastmediasorter.wear.ui.player.common.rememberRotaryFocus

/**
 * S3216: the watch's distress signal - the siren the owner found by accident, on the Morse SOS cadence,
 * with the display as its strobe.
 *
 * Two states, and the boundary between them is the whole design (ADR-3):
 *
 * - ARMING, before a mode is chosen. Three chips, ordinary touch, and leaving is whatever leaves any
 *   other screen. Locking the entrance as well would leave the watch unable to pick a mode at all.
 * - SIGNALLING, after it. Every touch is consumed on the INITIAL pass, the rotary bezel is swallowed,
 *   and only a hardware key leaves - the [WaterFlashlightScreen] contract, for its reason: a wet wrist
 *   fires touches and the circular edge gesture of its own, and losing the signal to one of them is the
 *   failure this program cannot have.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SosScreen(
    onLeave: () -> Unit,
    viewModel: SosViewModel = hiltViewModel(),
) {
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    val isLit by viewModel.isLit.collectAsStateWithLifecycle()
    val pendingMode by viewModel.pendingStartMode.collectAsStateWithLifecycle()

    // A start that arrived from the phone may land before this destination was drawn, so the waiting
    // mode is read here rather than delivered - see SosSyncBus for why it is state and not an event.
    LaunchedEffect(pendingMode) {
        pendingMode?.let { requested -> viewModel.start(requested, echoToPhone = false) }
    }

    // A stop from the phone ends the signal on this watch too: the owner's ruling is that switching off
    // either device ends it on both, and a screen left open with nothing running would read as signalling.
    LaunchedEffect(Unit) {
        viewModel.stopRequests.collect {
            viewModel.stop(echoToPhone = false)
            onLeave()
        }
    }

    if (mode == null) {
        SosArmingContent(onSelect = { chosen -> viewModel.start(chosen) })
    } else {
        SosSignallingContent(
            isLit = isLit,
            onLeave = {
                viewModel.stop()
                onLeave()
            },
        )
    }
}

/** The one screen in this program that takes an ordinary tap - the choice of what to engage. */
@Composable
private fun SosArmingContent(onSelect: (SosMode) -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CONTENT_SIDE_PADDING),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(CHIP_SPACING),
        ) {
            Text(
                text = stringResource(R.string.wear_app_sos),
                style = MaterialTheme.typography.title2,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.wear_sos_arming_hint),
                fontSize = HINT_TEXT_SIZE,
                textAlign = TextAlign.Center,
            )
            SosModeChip(
                labelRes = R.string.wear_sos_mode_all,
                mode = SosMode.ALL,
                primary = true,
                onSelect = onSelect,
            )
            SosModeChip(
                labelRes = R.string.wear_sos_mode_sound_only,
                mode = SosMode.SOUND_ONLY,
                primary = false,
                onSelect = onSelect,
            )
            SosModeChip(
                labelRes = R.string.wear_sos_mode_light_only,
                mode = SosMode.LIGHT_ONLY,
                primary = false,
                onSelect = onSelect,
            )
        }
    }
}

@Composable
private fun SosModeChip(
    labelRes: Int,
    mode: SosMode,
    primary: Boolean,
    onSelect: (SosMode) -> Unit,
) {
    CompactChip(
        onClick = { onSelect(mode) },
        label = {
            Text(
                text = stringResource(labelRes),
                fontSize = CHIP_LABEL_SIZE,
            )
        },
        // The default is the accented one: in an emergency the first chip has to be the one a thumb
        // lands on without reading, and «everything» is the mode the owner asked to have by default.
        colors = if (primary) ChipDefaults.primaryChipColors() else ChipDefaults.secondaryChipColors(),
    )
}

/**
 * The locked half: a field flashing white on the cadence, a hint naming the only way out, and no touch
 * target of any kind.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun SosSignallingContent(isLit: Boolean, onLeave: () -> Unit) {
    KeepDisplayLit()
    val focusRequester = rememberRotaryFocus()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isLit) Color.White else Color.Black)
            // Consumed on the INITIAL pass so nothing downstream or upstream sees it: a plain no-op
            // handler would still leave the swipe-to-dismiss gesture free to close the screen, and that
            // gesture is exactly the wet-glass input this program exists to survive (S2812).
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                    }
                }
            }
            // Every delivered key leaves, rather than one named code: which button a watch reports is a
            // per-model fact this repository has never measured, and being unable to stop a siren is a
            // far worse failure than stopping it by an unexpected button.
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp) {
                    onLeave()
                }
                true
            }
            // Swallowed rather than acted on: on a watch with a capacitive bezel the circular edge
            // gesture arrives here as a rotary event, which is wet-glass input like any other.
            .onRotaryScrollEvent { true }
            .focusRequester(focusRequester)
            .focusable(),
        contentAlignment = Alignment.Center,
    ) {
        // Fixed black on the lit frames and white on the dark ones, never themed: the field is a lamp,
        // so a colour taken from the theme would match one phase and vanish in the other.
        Text(
            text = stringResource(R.string.wear_sos_locked_hint),
            color = if (isLit) Color.Black else Color.White,
            fontSize = HINT_TEXT_SIZE,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = CONTENT_SIDE_PADDING),
        )
    }
}

/**
 * Holds the display on at full brightness for as long as the signal runs, and gives both back on the way
 * out - only this window's brightness is touched, never the watch's own setting.
 *
 * The keep-awake half goes through the shared reference-counted effect: the flag lives on the window, so
 * a screen setting it by hand would clear another screen's claim on navigation.
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

private const val MAX_BRIGHTNESS = 1.0f
private val HINT_TEXT_SIZE = 12.sp
private val CHIP_LABEL_SIZE = 12.sp
private val CONTENT_SIDE_PADDING = 16.dp
private val CHIP_SPACING = 4.dp
