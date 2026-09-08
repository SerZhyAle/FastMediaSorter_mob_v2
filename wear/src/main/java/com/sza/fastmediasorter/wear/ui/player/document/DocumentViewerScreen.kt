package com.sza.fastmediasorter.wear.ui.player.document

import androidx.annotation.StringRes
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Text
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.documents.DocumentFontSize
import com.sza.fastmediasorter.wear.domain.documents.WearDocumentFailure
import com.sza.fastmediasorter.wear.ui.common.WEAR_LIST_ANCHOR
import com.sza.fastmediasorter.wear.ui.common.WEAR_LIST_NO_ANCHOR
import com.sza.fastmediasorter.wear.ui.common.WearListColumn
import com.sza.fastmediasorter.wear.ui.common.WearListPosition
import com.sza.fastmediasorter.wear.ui.common.WearScreenScaffold
import com.sza.fastmediasorter.wear.ui.common.WearStateBlock
import com.sza.fastmediasorter.wear.ui.common.WearStateKind
import com.sza.fastmediasorter.wear.ui.common.rememberWearListState
import com.sza.fastmediasorter.wear.ui.player.common.rotaryActionScroll
import com.sza.fastmediasorter.wear.util.GridColumnFit
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first

/** The module's interactive minimum, which strategic §3.2 requires of the font-size targets. */
private val FONT_TARGET_SIZE = GridColumnFit.DEFAULT_MIN_TARGET_DP.dp

private val FONT_TARGET_BORDER = 2.dp
private val PROGRESS_SIZE = 32.dp
private val VERTICAL_GAP = 8.dp
private const val TITLE_MAX_LINES = 2

/**
 * The chosen size is shown as its own effect - one letter drawn at that size - so the control needs
 * no word on a face that has no room for three of them, and reads the same in every locale.
 */
private const val FONT_SAMPLE_GLYPH = "A"

/**
 * The watch's own reader, the fourth content screen beside the three players.
 *
 * S2532: everything it draws comes from [DocumentViewerUiState] - the text, and the four outcomes that
 * are not text. Each outcome is a sentence rather than a colour or a glyph alone, which is what
 * strategic §3.2 asks for: an empty file, a file that vanished, a file this process may not open and
 * a file that broke mid-read must stay apart for a wearer who cannot see the difference between two
 * shades of red.
 *
 * @param onBack leaves the screen; offered by the empty and failed states, where the platform dismiss
 * gesture is at its least discoverable, exactly as [WearStateBlock] does for every browse screen.
 */
@Composable
fun DocumentViewerScreen(
    viewModel: DocumentViewerViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // The generic opening anchor is declined because this screen has TWO opening positions - the
    // restored one and the first paragraph - and both are settled after the text arrives. Left on, its
    // scroll would race the restore for the same list and sometimes win, dropping the wearer back at
    // the top of a document they were half way through. DocumentReadingAnchor applies both instead.
    val listState = rememberWearListState(initialCenterItemIndex = WEAR_LIST_NO_ANCHOR)
    val stateScrollState = rememberScrollState()
    val failure = uiState.failure
    // S2754: restates the branch below, so the indicator can be bound before the branch is taken.
    // Every outcome but loading replaces the paragraph list with the state block and its own scroll.
    val showsStateBlock = !uiState.isLoading &&
        (failure != null || uiState.isEmpty || uiState.paragraphs.isEmpty())

    WearScreenScaffold(
        // The list carries the round-screen inset itself through WearListColumn, and the state block
        // measures the inscribed square on its own, so a second inset here would pay for both twice.
        contentPadding = PaddingValues(0.dp),
        scrollState = listState,
        positionIndicator = {
            if (showsStateBlock) PositionIndicator(stateScrollState) else PositionIndicator(listState)
        }
    ) {
        when {
            uiState.isLoading -> DocumentLoading()
            failure != null -> WearStateBlock(
                kind = WearStateKind.ERROR,
                message = stringResource(messageFor(failure)),
                onBack = onBack,
                scrollState = stateScrollState
            )
            // A file holding nothing but blank lines reads as empty too: it was read successfully and
            // there is still nothing to show, which is the same sentence rather than a blank screen.
            uiState.isEmpty || uiState.paragraphs.isEmpty() -> WearStateBlock(
                kind = WearStateKind.EMPTY,
                message = stringResource(R.string.wear_document_empty),
                onBack = onBack,
                scrollState = stateScrollState
            )
            else -> DocumentContent(
                uiState = uiState,
                listState = listState,
                onFontSizeChanged = viewModel::onFontSizeChanged,
                onScrollStopped = viewModel::onScrollStopped
            )
        }
    }
}

@Composable
private fun DocumentContent(
    uiState: DocumentViewerUiState,
    listState: ScalingLazyListState,
    onFontSizeChanged: (DocumentFontSize) -> Unit,
    onScrollStopped: (Int, Int) -> Unit
) {
    val bodyStyle = MaterialTheme.typography.body2
    val paragraphStyle = bodyStyle.copy(fontSize = bodyStyle.fontSize * uiState.fontSize.scale)

    DocumentReadingAnchor(
        listState = listState,
        initialPosition = uiState.initialPosition,
        onScrollStopped = onScrollStopped
    )

    WearListColumn(
        modifier = Modifier
            .fillMaxSize()
            // S2049: this pinned Wear Compose build wires no rotary input into the scaling column, so
            // the bezel reaches a scrolling screen only where the screen asks for it - and strategic
            // §2 goal 2 makes the bezel a way of reading this one, not a convenience.
            .rotaryActionScroll(listState),
        state = listState
    ) {
        item { DocumentTitle(uiState.fileName) }
        item { FontSizeRow(selected = uiState.fontSize, onSelected = onFontSizeChanged) }
        items(uiState.paragraphs) { paragraph ->
            Text(
                text = paragraph,
                style = paragraphStyle,
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (uiState.truncated) {
            item { TruncationNotice() }
        }
    }
}

/**
 * Opens the reader where it belongs, and reports every later resting place.
 *
 * A document with no stored anchor opens on [WEAR_LIST_ANCHOR], the module's own opening rule, which on
 * this screen is the first paragraph - the title and the font-size row sit above it. One with a stored
 * anchor opens on that instead, so no call site has to know which case it is in.
 *
 * Neither can ride on the list state's initial index: [WearListColumn] passes no centering reservation,
 * so an index given before the list has rows is discarded - the same reason `rememberWearListState`
 * scrolls after the first non-empty layout rather than starting there.
 *
 * The report is driven by the list settling rather than by each scrolled frame, which is what keeps a
 * gesture to one DataStore write; and it is armed only after the opening scroll, so the scroll this
 * screen performs itself is not reported back as somewhere the wearer chose to stop.
 */
@Composable
private fun DocumentReadingAnchor(
    listState: ScalingLazyListState,
    initialPosition: WearListPosition?,
    onScrollStopped: (Int, Int) -> Unit
) {
    var opened by remember { mutableStateOf(false) }

    LaunchedEffect(initialPosition) {
        val itemCount = snapshotFlow { listState.layoutInfo.totalItemsCount }.first { it > 0 }
        val target = initialPosition?.index ?: WEAR_LIST_ANCHOR
        listState.scrollToItem(
            target.coerceIn(0, itemCount - 1),
            initialPosition?.offset ?: 0
        )
        opened = true
    }

    LaunchedEffect(opened) {
        if (opened) {
            snapshotFlow { listState.isScrollInProgress }
                // The first emission is the current value, not a settle: without it every open would
                // write back the position it had just been placed on.
                .drop(1)
                .filter { inProgress -> !inProgress }
                .collect {
                    onScrollStopped(listState.centerItemIndex, listState.centerItemScrollOffset)
                }
        }
    }
}

@Composable
private fun DocumentTitle(fileName: String) {
    Text(
        text = fileName,
        style = MaterialTheme.typography.title3,
        textAlign = TextAlign.Center,
        maxLines = TITLE_MAX_LINES,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth()
    )
}

/**
 * Sits after the last paragraph rather than at the top: it describes where the text stops, and a
 * wearer who never scrolls that far was not affected by the cap.
 */
@Composable
private fun TruncationNotice() {
    Text(
        text = stringResource(R.string.wear_document_truncated),
        style = MaterialTheme.typography.caption2,
        color = MaterialTheme.colors.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = VERTICAL_GAP)
    )
}

@Composable
private fun FontSizeRow(selected: DocumentFontSize, onSelected: (DocumentFontSize) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        DocumentFontSize.entries.forEach { size ->
            FontSizeTarget(
                size = size,
                selected = size == selected,
                onClick = { onSelected(size) }
            )
        }
    }
}

/**
 * The chosen size is ringed, not tinted: strategic §3.2 requires every state distinction to survive
 * without colour, and the glyph inside already differs only in size.
 */
@Composable
private fun FontSizeTarget(size: DocumentFontSize, selected: Boolean, onClick: () -> Unit) {
    val label = stringResource(size.labelRes)
    val description = if (selected) {
        stringResource(R.string.wear_document_font_selected, label)
    } else {
        label
    }
    val outline = if (selected) {
        Modifier.border(FONT_TARGET_BORDER, MaterialTheme.colors.onSurface, CircleShape)
    } else {
        Modifier
    }
    val glyphStyle = MaterialTheme.typography.title3

    Box(
        modifier = Modifier
            .size(FONT_TARGET_SIZE)
            .clip(CircleShape)
            .then(outline)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = FONT_SAMPLE_GLYPH,
            style = glyphStyle.copy(fontSize = glyphStyle.fontSize * size.scale)
        )
    }
}

@Composable
private fun DocumentLoading() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(modifier = Modifier.size(PROGRESS_SIZE))
        Text(
            text = stringResource(R.string.wear_document_loading),
            style = MaterialTheme.typography.caption1,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = VERTICAL_GAP)
        )
    }
}

@StringRes
private fun messageFor(failure: WearDocumentFailure): Int = when (failure) {
    WearDocumentFailure.NOT_FOUND -> R.string.wear_document_error_not_found
    WearDocumentFailure.NO_ACCESS -> R.string.wear_document_error_no_access
    WearDocumentFailure.UNREADABLE_ENCODING -> R.string.wear_document_error_encoding
    WearDocumentFailure.IO_ERROR -> R.string.wear_document_error_io
}
