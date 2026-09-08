package com.sza.fastmediasorter.wear.ui.common

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.ui.player.common.rotaryActionScroll

private val MESSAGE_PADDING = 16.dp
private val ACTION_SPACING = 8.dp

/** The three things a browse screen can have instead of content. Loading is not one of them. */
enum class WearStateKind {
    EMPTY,
    UNAVAILABLE,
    ERROR
}

/** Ordered actions a state offers. Kept separate from the composable so the rule can be tested. */
internal enum class WearStateAction {
    RETRY,
    BACK
}

/**
 * A screen-specific offer a state carries between Retry and Back.
 *
 * Exists for one screen: the Resources list is empty before the first source is registered, and its
 * two ways out - pull the phone's sources over, or type one in by hand - are offers no other screen
 * has. Dropping them would have left that screen a dead end, and keeping them would have left it the
 * one screen still drawing its own emptiness. In a release build only the first is offered, so the
 * shape the owner fixed - message, one action, Back - is what actually ships; the second appears in
 * debug builds only. A third screen wanting extras is a signal to re-read this decision.
 */
data class WearStateExtraAction(
    val label: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true
)

/**
 * Decides which actions a state shows.
 *
 * A retry is offered only where retrying can change the answer: an empty list was produced by a call
 * that already succeeded, so repeating it would return the same emptiness and read as a broken button.
 * Back is offered always - on a screen with no content the platform dismiss gesture is at its least
 * discoverable, which is the case the owner named when asking for Retry and Back together.
 *
 * Screen-specific extras are placed by the composable rather than named here: they act on the screen
 * while Back leaves it, so they sit before it, and this rule stays about the two actions every state
 * has.
 */
internal fun stateActionsFor(kind: WearStateKind, hasRetry: Boolean): List<WearStateAction> {
    val retryApplies = hasRetry && kind != WearStateKind.EMPTY
    return if (retryApplies) {
        listOf(WearStateAction.RETRY, WearStateAction.BACK)
    } else {
        listOf(WearStateAction.BACK)
    }
}

/**
 * The module's only empty, unavailable and failed surface.
 *
 * Before this existed, seven browse screens each described the same three situations in their own
 * words and offered a different set of controls, and three category screens described none of them at
 * all. A screen now orders a state rather than drawing one.
 *
 * No glyph is drawn on purpose: the module currently marks content with three unrelated visual
 * vocabularies, and unifying them belongs to the ticket that owns the icon contract - baking one of
 * them in here would have to be undone there.
 *
 * @param scrollState hoisted so the hosting screen can point its Scaffold's `positionIndicator` at
 * the content actually on the glass (S2754). While this block replaces a list, the list's own state
 * is standing still, so an indicator left bound to it reports a position nothing here can move -
 * which is what Google Play rejected the watch build for.
 */
@Composable
fun WearStateBlock(
    kind: WearStateKind,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    message: String? = null,
    onRetry: (() -> Unit)? = null,
    extraActions: List<WearStateExtraAction> = emptyList(),
    scrollState: ScrollState = rememberScrollState()
) {
    val text = message ?: defaultMessageFor(kind)
    val retryLabel = stringResource(R.string.retry)
    val backLabel = stringResource(R.string.wear_state_back)
    val actions = stateActionsFor(kind, onRetry != null)
    val squareSide = wearMaxSquareSide()

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // S2568: the message plus its chips is taller than the round screen's content box on a 227 dp
        // watch, so the last action - the only way off an empty Resources screen - was drawn under the
        // glass edge, collapsed to a one-pixel line. The form is WearActionCloud's, which is the one
        // shape in this module measured to actually scroll: the fixed-side Box carries the scroll and
        // the inner Column only claims that height as a minimum. Hanging verticalScroll on the Column
        // itself was tried, shipped to a device and changed nothing (dev/REFUTED_APPROACHES.md).
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
                Text(
                    text = text,
                    style = MaterialTheme.typography.body1,
                    color = if (kind == WearStateKind.ERROR) {
                        MaterialTheme.colors.error
                    } else {
                        MaterialTheme.colors.onSurface
                    },
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(MESSAGE_PADDING)
                        .semantics { contentDescription = text }
                )
                if (actions.contains(WearStateAction.RETRY)) {
                    StateChip(
                        label = retryLabel,
                        onClick = { onRetry?.invoke() },
                        primary = true
                    )
                }
                extraActions.forEach { extra ->
                    StateChip(
                        label = extra.label,
                        onClick = extra.onClick,
                        primary = extraActions.first() === extra && !actions.contains(WearStateAction.RETRY),
                        enabled = extra.enabled
                    )
                }
                StateChip(
                    label = backLabel,
                    onClick = onBack,
                    primary = false
                )
            }
        }
    }
}

/**
 * A caller should say what is empty - "no favourites yet" and "no files in this folder" are different
 * sentences. The generic line exists so a caller that forgot leaves a bare screen rather than crashing
 * the watch, which is the failure this whole component was introduced to remove.
 */
@Composable
private fun defaultMessageFor(kind: WearStateKind): String = when (kind) {
    WearStateKind.EMPTY -> stringResource(R.string.wear_state_empty)
    WearStateKind.UNAVAILABLE -> stringResource(R.string.wear_state_unavailable)
    WearStateKind.ERROR -> stringResource(R.string.wear_state_error)
}

@Composable
private fun StateChip(
    label: String,
    onClick: () -> Unit,
    primary: Boolean,
    enabled: Boolean = true
) {
    // S2522: the secondary branch is spelled out through the base factory rather than through
    // secondaryChipColors(), which exposes no disabled colour at all and derives it as the content
    // colour at a fixed low alpha - the pattern AndroidX documents as insufficient contrast on a light
    // background and patched only in the primary family (b/254025377). The surface background and the
    // content colour derived from it are exactly what secondaryChipColors() would have produced.
    val colors = if (primary) {
        ChipDefaults.primaryChipColors()
    } else {
        ChipDefaults.chipColors(
            backgroundColor = MaterialTheme.colors.surface,
            disabledContentColor = MaterialTheme.colors.onSurfaceVariant
        )
    }
    Chip(
        onClick = onClick,
        label = { Text(text = label) },
        colors = colors,
        enabled = enabled,
        modifier = Modifier
            .padding(top = ACTION_SPACING)
            .semantics { contentDescription = label }
    )
}
