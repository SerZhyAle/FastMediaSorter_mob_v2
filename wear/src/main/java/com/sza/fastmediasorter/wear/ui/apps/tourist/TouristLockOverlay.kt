package com.sza.fastmediasorter.wear.ui.apps.tourist

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.ui.apps.waterflashlight.WaterFlashlightExitGate
import com.sza.fastmediasorter.wear.ui.apps.waterflashlight.onScreenKey
import com.sza.fastmediasorter.wear.ui.player.common.rotaryActionSwallow
import com.sza.fastmediasorter.wear.ui.theme.WearAppTheme
import timber.log.Timber

/**
 * S3115: touch lock for the tourist dashboard, built on the water-flashlight pattern (S2516 / S2812).
 *
 * It draws nothing: the telemetry underneath stays visible and live while swimming or running, which is
 * the whole point of the lock - the reading must be watchable while the wrist keeps touching things.
 * Every pointer event is consumed on the INITIAL pass, so neither the content below nor the
 * swipe-to-dismiss gesture above ever sees it, and rotation is swallowed for the same reason.
 *
 * That purpose makes it a touch-locked screen of `INPUT-PARITY` 0.7 section 3.3, so one key release or
 * one back must not leave it: a button squeezed by a stroke or a back swiped by wet skin arrives as
 * exactly that. The exit is the water flashlight's [WaterFlashlightExitGate] - a held key or a streak
 * of three - fed through the same key routing, so the module's two locked screens leave alike.
 *
 * The back key does NOT arrive here as a key event at all - the back-navigation dispatcher claims it
 * before Compose delivers it - so on the watch the one hardware button that reaches an application
 * took the whole screen away instead of lifting the lock (measured on Galaxy Watch 7, 2026-09-17).
 * [BackHandler] is what intercepts it in place and counts it towards the streak.
 *
 * The overlay is a focusable full-screen leaf, which Compose would otherwise treat as covering every node
 * below it and drop the whole dashboard from the accessibility tree, so a screen reader would find no
 * reading while the lock is on. It is therefore invisible to accessibility: the sink for pointer, key and
 * rotary input stays as it is, and the readings underneath stay in the tree.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TouristLockOverlay(
    onUnlock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val exitGate = remember { WaterFlashlightExitGate() }
    val currentUnlock by rememberUpdatedState {
        Timber.d("S4658: tourist lock left through the K.3 exit gate")
        onUnlock()
    }

    LaunchedEffect(Unit) {
        Timber.d("S4744: tourist lock overlay shown, invisible to accessibility so the dashboard stays in the tree")
    }

    BackHandler {
        if (exitGate.onBack(System.currentTimeMillis())) {
            currentUnlock()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .semantics { invisibleToUser() }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                    }
                }
            }
            .onPreviewKeyEvent { event ->
                exitGate.onScreenKey(event, currentUnlock)
                true
            }
            .rotaryActionSwallow(),
    )
}

/**
 * S3115: the hint that takes the Lock control's own slot while the lock is on - the only thing telling
 * the owner how to leave it.
 */
@Composable
fun TouristUnlockHint(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.wear_tourist_lock_exit_hint),
        style = MaterialTheme.typography.caption2,
        color = WearAppTheme.colors.unlockHint,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth(),
    )
}
