package com.sza.fastmediasorter.wear.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
 * Decides which actions a state shows.
 *
 * A retry is offered only where retrying can change the answer: an empty list was produced by a call
 * that already succeeded, so repeating it would return the same emptiness and read as a broken button.
 * Back is offered always - on a screen with no content the platform dismiss gesture is at its least
 * discoverable, which is the case the owner named when asking for Retry and Back together.
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
 */
@Composable
fun WearStateBlock(
    kind: WearStateKind,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    message: String? = null,
    onRetry: (() -> Unit)? = null
) {
    val text = message ?: defaultMessageFor(kind)
    val retryLabel = stringResource(R.string.retry)
    val backLabel = stringResource(R.string.wear_state_back)

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(wearScreenInsets()),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = text,
                style = MaterialTheme.typography.body1,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(MESSAGE_PADDING)
                    .semantics { contentDescription = text }
            )
            stateActionsFor(kind, onRetry != null).forEach { action ->
                when (action) {
                    WearStateAction.RETRY -> StateChip(
                        label = retryLabel,
                        onClick = { onRetry?.invoke() },
                        primary = true
                    )
                    WearStateAction.BACK -> StateChip(
                        label = backLabel,
                        onClick = onBack,
                        primary = false
                    )
                }
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
    primary: Boolean
) {
    val colors = if (primary) ChipDefaults.primaryChipColors() else ChipDefaults.secondaryChipColors()
    Chip(
        onClick = onClick,
        label = { Text(text = label) },
        colors = colors,
        modifier = Modifier
            .padding(top = ACTION_SPACING)
            .semantics { contentDescription = label }
    )
}
