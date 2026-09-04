@file:Suppress("MatchingDeclarationName")

package com.sza.fastmediasorter.wear.ui.common

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Text
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.ui.player.common.rotaryActionScroll

/**
 * Action button definition for watch action menus and dialogs.
 */
data class WearAction(
    val label: String,
    val icon: (@Composable () -> Unit)? = null,
    val primary: Boolean = false,
    val enabled: Boolean = true,
    val onClick: () -> Unit
)

/**
 * Single content-sized Wear action button.
 *
 * Measures itself by its content length rather than expanding to full screen width.
 */
@Composable
fun WearActionButton(
    action: WearAction,
    modifier: Modifier = Modifier
) {
    Chip(
        onClick = action.onClick,
        enabled = action.enabled,
        label = { Text(action.label) },
        icon = action.icon?.let { iconLambda -> { iconLambda() } },
        modifier = modifier.semantics { contentDescription = action.label },
        colors = if (action.primary) ChipDefaults.primaryChipColors() else ChipDefaults.secondaryChipColors()
    )
}

/**
 * Strategic ADR-1 / ADR-2: Host for action button arrangements.
 *
 * The area is the inscribed square [wearMaxSquareSide] rather than a proportional inset because
 * a wrapping cloud or action set is two-dimensional content whose outer rows sit where the round glass
 * has already narrowed. Housing content within the inscribed square guarantees no element clips the round
 * glass boundary in any row. Vertical centring is provided via a plain scroll state with rotary binding.
 */
@Composable
private fun WearActionSquareHost(
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val squareSide = wearMaxSquareSide()
    val scrollState = rememberScrollState()
    val cancelLabel = stringResource(R.string.cancel)
    if (onDismiss != null) {
        // While the menu is up it owns back, so the system button and TalkBack's back gesture cancel
        // the menu rather than leaving the screen the menu was called from.
        BackHandler {
            timber.log.Timber.d("S2506: back cancels action menu")
            onDismiss()
        }
    }
    Box(
        modifier = if (onDismiss == null) {
            modifier.fillMaxSize()
        } else {
            modifier.fillMaxSize().cancelOnBackdrop(onDismiss, cancelLabel)
        },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(squareSide)
                .rotaryActionScroll(scrollState)
                .verticalScroll(scrollState)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = squareSide),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (header != null) {
                    header()
                    Spacer(modifier = Modifier.height(BUTTON_GAP))
                }
                content()
            }
        }
    }
}

/**
 * Turns the otherwise transparent backdrop into the menu's cancel.
 *
 * The buttons are children of the same Box and take their own press first, so "past the buttons"
 * needs no zone arithmetic - what reaches here is by definition what missed them.
 *
 * The horizontal drag is consumed rather than left to travel outward: `SwipeDismissableNavHost`
 * pops the back stack straight from its own `SwipeToDismissBox` and never through the back
 * dispatcher, so a drag allowed past this node takes the whole player away instead of the menu.
 * Compose dispatches the main pass from child to ancestor, which is what makes consuming it here
 * enough.
 */
private fun Modifier.cancelOnBackdrop(onDismiss: () -> Unit, cancelLabel: String): Modifier =
    pointerInput(onDismiss) {
        detectTapGestures {
            timber.log.Timber.d("S2506: backdrop tap cancels action menu")
            onDismiss()
        }
    }
        .pointerInput(onDismiss) {
            detectHorizontalDragGestures(
                onDragEnd = {
                    timber.log.Timber.d("S2506: backdrop swipe cancels action menu")
                    onDismiss()
                }
            ) { change, _ -> change.consume() }
        }
        // Plain `semantics`, never `clickable`: the latter merges its descendants, which would fold
        // every button into one node and leave a TalkBack user unable to reach any of them.
        .semantics {
            onClick(label = cancelLabel) {
                onDismiss()
                true
            }
        }

/**
 * Stacks action buttons at equal width equal to the widest button, centred horizontally and vertically.
 */
@Composable
fun WearActionColumn(
    actions: List<WearAction>,
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null,
    onDismiss: (() -> Unit)? = null
) {
    timber.log.Timber.d("S2469: WearActionColumn actions=%d", actions.size)
    WearActionSquareHost(
        modifier = modifier,
        header = header,
        onDismiss = onDismiss
    ) {
        Column(
            modifier = Modifier.width(IntrinsicSize.Min),
            verticalArrangement = Arrangement.spacedBy(BUTTON_GAP),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            actions.forEach { action ->
                WearActionButton(
                    action = action,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * Lays out action buttons in a wrapping cloud, filling rows by content width and centring them.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WearActionCloud(
    actions: List<WearAction>,
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null,
    onDismiss: (() -> Unit)? = null
) {
    timber.log.Timber.d("S2469: WearActionCloud actions=%d", actions.size)
    WearActionSquareHost(
        modifier = modifier,
        header = header,
        onDismiss = onDismiss
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(BUTTON_GAP, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(BUTTON_GAP),
            maxItemsInEachRow = Int.MAX_VALUE
        ) {
            actions.forEach { action ->
                WearActionButton(action = action)
            }
        }
    }
}

private val BUTTON_GAP = 6.dp
