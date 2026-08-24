package com.sza.fastmediasorter.wear.ui.phone

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Text
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.model.WearPhoneResourceItem
import com.sza.fastmediasorter.wear.domain.model.WearPhoneResourceResponseStatus
import com.sza.fastmediasorter.wear.domain.model.WearThumbnail
import com.sza.fastmediasorter.wear.domain.model.WearViewMode
import com.sza.fastmediasorter.wear.ui.common.ScreenTitle
import com.sza.fastmediasorter.wear.ui.common.ThumbnailCell
import com.sza.fastmediasorter.wear.ui.common.WearGridScalingParams
import com.sza.fastmediasorter.wear.ui.common.WearScreenScaffold
import com.sza.fastmediasorter.wear.ui.common.wearScreenInsets
import com.sza.fastmediasorter.wear.ui.navigation.WearRoutes
import com.sza.fastmediasorter.wear.util.GridColumnFit

private const val SINGLE_COLUMN = 1
private val GRID_GAP = GridColumnFit.DEFAULT_GAP_DP.dp
private val ENTRY_ICON_SIZE = 24.dp
private val STATUS_CORNER_RADIUS = 12.dp
private val STATUS_GAP = 8.dp
private val STATUS_PADDING_HORIZONTAL = 12.dp
private val STATUS_PADDING_VERTICAL = 6.dp
private val STATUS_PROGRESS_SIZE = 16.dp

@Composable
fun PhoneResourceScreen(
    navController: NavController,
    viewModel: PhoneResourceViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val fileListViewMode by viewModel.fileListViewMode.collectAsStateWithLifecycle()
    val thumbnails by viewModel.thumbnails.collectAsStateWithLifecycle()

    // S1846: the tap on a file ends here, once. Navigation is a side effect of an outcome, so it runs
    // in an effect keyed to that outcome and the outcome is consumed straight after - recomposing the
    // list must not push the player a second time.
    val openOutcome by viewModel.openOutcome.collectAsStateWithLifecycle()
    LaunchedEffect(openOutcome) {
        val outcome = openOutcome
        if (outcome is PhoneFileOpenOutcome.Ready) {
            navController.navigate(playerRouteFor(outcome.fileId, outcome.mimeType))
            viewModel.consumeOpenOutcome()
        }
    }

    // Back walks the folder trail first; only the root hands Back back to navigation.
    BackHandler(enabled = true) {
        if (!viewModel.navigateUp()) {
            navController.popBackStack()
        }
    }

    val listState = rememberScalingLazyListState()

    WearScreenScaffold(
        contentPadding = PaddingValues(0.dp),
        scrollState = listState,
        positionIndicator = { PositionIndicator(listState) }
    ) {
        when (val current = state) {
            is PhoneResourceUiState.Loading -> CenteredMessage(
                text = stringResource(R.string.phone_resource_loading),
                showProgress = true
            )

            is PhoneResourceUiState.Content -> Box(modifier = Modifier.fillMaxSize()) {
                PhoneResourceList(
                    items = current.items,
                    listState = listState,
                    viewMode = fileListViewMode,
                    thumbnails = thumbnails,
                    title = current.title,
                    onEntryClick = { entry ->
                        if (entry.isDirectory) {
                            viewModel.openFolder(entry.token, entry.name)
                        } else {
                            viewModel.openFile(entry)
                        }
                    }
                )

                // S1898: the outcome is anchored to the screen, not to the start of the list. Reaching a
                // file means scrolling to it, so a line drawn as the list's second item reports the result
                // outside the viewport it was meant for - which is why 30 s of waiting and the refusal that
                // followed it both read as nothing happening at all.
                val statusRes = openOutcome.toStatusRes()
                if (statusRes != null) {
                    PinnedOpenStatus(
                        statusRes = statusRes,
                        showProgress = openOutcome == PhoneFileOpenOutcome.Opening,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(wearScreenInsets())
                    )
                }
            }

            is PhoneResourceUiState.Empty -> RetryMessage(
                // A filtered list that says "your phone has nothing to show" would blame the phone for
                // a filter the user chose one screen earlier (S1846).
                text = if (viewModel.mediaType == null) {
                    stringResource(R.string.phone_resource_empty)
                } else {
                    stringResource(R.string.phone_resource_empty_filtered)
                },
                onRetry = viewModel::retry
            )

            is PhoneResourceUiState.Unavailable -> RetryMessage(
                text = stringResource(current.reason.toMessageRes()),
                onRetry = viewModel::retry
            )
        }
    }
}

@Composable
private fun PhoneResourceList(
    items: List<WearPhoneResourceItem>,
    listState: ScalingLazyListState,
    viewMode: WearViewMode,
    thumbnails: Map<String, WearThumbnail>,
    title: ScreenTitle,
    onEntryClick: (WearPhoneResourceItem) -> Unit
) {
    // The column count comes from the width this composable actually gets, so this browser answers
    // the geometry question exactly as the general file list does.
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val columns = GridColumnFit.columnsFor(viewMode, maxWidth.value.toInt())
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = wearScreenInsets(),
            scalingParams = WearGridScalingParams
        ) {
            item {
                val titleText = when (title) {
                    is ScreenTitle.Text -> title.value
                    is ScreenTitle.Resource -> stringResource(title.id)
                }
                Text(
                    text = titleText,
                    style = MaterialTheme.typography.title3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    textAlign = TextAlign.Center
                )
            }

            entryItems(
                items = items,
                columns = columns,
                thumbnails = thumbnails,
                onEntryClick = onEntryClick
            )
        }
    }
}

/**
 * S1898: the single place a tap's outcome is reported - the wait first, then whatever it came to, at
 * one coordinate rather than two.
 *
 * Opaque behind the text because the list keeps scrolling underneath it, and deliberately neither
 * clickable nor focusable so the rotating crown keeps addressing the list rather than this row.
 */
@Composable
private fun PinnedOpenStatus(
    @StringRes statusRes: Int,
    showProgress: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colors.surface, RoundedCornerShape(STATUS_CORNER_RADIUS))
            .padding(horizontal = STATUS_PADDING_HORIZONTAL, vertical = STATUS_PADDING_VERTICAL),
        horizontalArrangement = Arrangement.spacedBy(STATUS_GAP, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showProgress) {
            CircularProgressIndicator(modifier = Modifier.size(STATUS_PROGRESS_SIZE))
        }
        Text(
            text = stringResource(statusRes),
            style = MaterialTheme.typography.caption2,
            // Waiting is not a failure: the error colour belongs to the outcomes that are one.
            color = if (showProgress) MaterialTheme.colors.onSurface else MaterialTheme.colors.error,
            textAlign = TextAlign.Center
        )
    }
}

/** One column keeps today's chip; more than one draws the cell both watch file lists share. */
private fun ScalingLazyListScope.entryItems(
    items: List<WearPhoneResourceItem>,
    columns: Int,
    thumbnails: Map<String, WearThumbnail>,
    onEntryClick: (WearPhoneResourceItem) -> Unit
) {
    if (columns == SINGLE_COLUMN) {
        items(items) { entry ->
            EntryChip(entry = entry, onEntryClick = onEntryClick)
        }
    } else {
        items(items.chunked(columns)) { rowEntries ->
            EntryRow(
                entries = rowEntries,
                columns = columns,
                thumbnails = thumbnails,
                onEntryClick = onEntryClick
            )
        }
    }
}

/** A short row is padded with empty weights so its cells keep the width of a full row's cells. */
@Composable
private fun EntryRow(
    entries: List<WearPhoneResourceItem>,
    columns: Int,
    thumbnails: Map<String, WearThumbnail>,
    onEntryClick: (WearPhoneResourceItem) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(GRID_GAP)
    ) {
        entries.forEach { entry ->
            ThumbnailCell(
                thumbnail = thumbnails[entry.token] ?: WearThumbnail.Unavailable,
                caption = entry.name,
                onClick = { onEntryClick(entry) },
                modifier = Modifier.weight(1f)
            ) {
                EntryIcon(entry = entry)
            }
        }
        repeat(columns - entries.size) {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun EntryChip(
    entry: WearPhoneResourceItem,
    onEntryClick: (WearPhoneResourceItem) -> Unit
) {
    Chip(
        onClick = { onEntryClick(entry) },
        label = { Text(text = entry.name) },
        icon = { EntryIcon(entry = entry) },
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = entry.name },
        colors = ChipDefaults.primaryChipColors()
    )
}

@Composable
private fun EntryIcon(entry: WearPhoneResourceItem) {
    Icon(
        imageVector = if (entry.isDirectory) {
            Icons.Filled.Folder
        } else {
            Icons.AutoMirrored.Filled.InsertDriveFile
        },
        contentDescription = null,
        modifier = Modifier.size(ENTRY_ICON_SIZE)
    )
}

@Composable
private fun CenteredMessage(text: String, showProgress: Boolean) {
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = wearScreenInsets()
    ) {
        if (showProgress) {
            item { CircularProgressIndicator(modifier = Modifier.padding(vertical = 8.dp)) }
        }
        item {
            Text(
                text = text,
                style = MaterialTheme.typography.body2,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun RetryMessage(text: String, onRetry: () -> Unit) {
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = wearScreenInsets()
    ) {
        item {
            Text(
                text = text,
                style = MaterialTheme.typography.body2,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                textAlign = TextAlign.Center
            )
        }
        item {
            Chip(
                onClick = onRetry,
                label = { Text(text = stringResource(R.string.phone_resource_retry)) },
                icon = {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = stringResource(R.string.phone_resource_retry),
                        modifier = Modifier.size(24.dp)
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ChipDefaults.secondaryChipColors()
            )
        }
    }
}

/**
 * A protocol status never reaches the user: each one is answered with the next step the person can
 * actually take, per `docs/COMMUNICATION_POLICY.md`.
 */
/**
 * S1846: the line shown after a tap that did not reach a player, or null when there is nothing to say.
 * `Ready` shows nothing because the screen is already leaving for the player. S1898 moved where it is
 * drawn - see [PinnedOpenStatus] - without changing which outcomes speak.
 */
@StringRes
private fun PhoneFileOpenOutcome?.toStatusRes(): Int? = when (this) {
    PhoneFileOpenOutcome.Opening -> R.string.phone_resource_opening
    PhoneFileOpenOutcome.Unsupported -> R.string.phone_resource_open_unsupported
    is PhoneFileOpenOutcome.Failed -> reason.toMessageRes()
    else -> null
}

/**
 * S1846: which player renders a delivered phone file.
 *
 * Decided from the file's own mime type rather than from the chip that listed it: a folder reached under
 * the `all` entrance mixes kinds, and the general browser already picks its player the same way. The three
 * routes are the watch's existing player entrances - a fourth would duplicate them.
 */
private fun playerRouteFor(fileId: Long, mimeType: String): String = when {
    mimeType.startsWith("image/") -> WearRoutes.imageViewer(fileId)
    mimeType.startsWith("video/") -> WearRoutes.videoPlayer(fileId)
    else -> WearRoutes.audioPlayer(fileId)
}

private fun WearPhoneResourceResponseStatus?.toMessageRes(): Int = when (this) {
    WearPhoneResourceResponseStatus.SOURCE_UNAVAILABLE -> R.string.phone_resource_source_unavailable
    WearPhoneResourceResponseStatus.ACCESS_DENIED -> R.string.phone_resource_access_denied
    WearPhoneResourceResponseStatus.UNSUPPORTED_MEDIA -> R.string.phone_resource_unsupported
    WearPhoneResourceResponseStatus.TRANSFER_REJECTED -> R.string.phone_resource_transfer_rejected
    // S1897: the phone answered and refused - it just could not find the file. Without its own message
    // this fell to the fallback below and told the user the phone was out of reach, which a device run
    // showed being read as "nothing happened" while the phone had in fact replied in 41 ms.
    WearPhoneResourceResponseStatus.NOT_FOUND -> R.string.phone_resource_not_found
    else -> R.string.phone_resource_unavailable
}
