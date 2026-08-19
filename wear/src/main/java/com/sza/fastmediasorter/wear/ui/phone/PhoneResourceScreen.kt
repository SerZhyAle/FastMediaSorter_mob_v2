package com.sza.fastmediasorter.wear.ui.phone

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.sza.fastmediasorter.wear.ui.common.ThumbnailCell
import com.sza.fastmediasorter.wear.ui.common.WearScreenScaffold
import com.sza.fastmediasorter.wear.ui.common.wearScreenInsets
import com.sza.fastmediasorter.wear.util.GridColumnFit
import timber.log.Timber

private const val SINGLE_COLUMN = 1
private val GRID_GAP = GridColumnFit.DEFAULT_GAP_DP.dp
private val ENTRY_ICON_SIZE = 24.dp

@Composable
fun PhoneResourceScreen(
    navController: NavController,
    viewModel: PhoneResourceViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val fileListViewMode by viewModel.fileListViewMode.collectAsStateWithLifecycle()
    val thumbnails by viewModel.thumbnails.collectAsStateWithLifecycle()

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

            is PhoneResourceUiState.Content -> PhoneResourceList(
                items = current.items,
                listState = listState,
                viewMode = fileListViewMode,
                thumbnails = thumbnails,
                onFolderClick = viewModel::openFolder
            )

            is PhoneResourceUiState.Empty -> RetryMessage(
                text = stringResource(R.string.phone_resource_empty),
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
    onFolderClick: (String) -> Unit
) {
    // The column count comes from the width this composable actually gets, so this browser answers
    // the geometry question exactly as the general file list does.
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val columns = GridColumnFit.columnsFor(viewMode, maxWidth.value.toInt())
        Timber.d("S1730: phone folder grid mode=$viewMode columns=$columns")
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = wearScreenInsets()
        ) {
            item {
                Text(
                    text = stringResource(R.string.phone_resource_title),
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
                onFolderClick = onFolderClick
            )
        }
    }
}

/** One column keeps today's chip; more than one draws the cell both watch file lists share. */
private fun ScalingLazyListScope.entryItems(
    items: List<WearPhoneResourceItem>,
    columns: Int,
    thumbnails: Map<String, WearThumbnail>,
    onFolderClick: (String) -> Unit
) {
    if (columns == SINGLE_COLUMN) {
        items(items) { entry ->
            EntryChip(entry = entry, onFolderClick = onFolderClick)
        }
    } else {
        items(items.chunked(columns)) { rowEntries ->
            EntryRow(
                entries = rowEntries,
                columns = columns,
                thumbnails = thumbnails,
                onFolderClick = onFolderClick
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
    onFolderClick: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(GRID_GAP)
    ) {
        entries.forEach { entry ->
            ThumbnailCell(
                thumbnail = thumbnails[entry.token] ?: WearThumbnail.Unavailable,
                caption = entry.name,
                onClick = { if (entry.isDirectory) onFolderClick(entry.token) },
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
    onFolderClick: (String) -> Unit
) {
    Chip(
        onClick = { if (entry.isDirectory) onFolderClick(entry.token) },
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
private fun WearPhoneResourceResponseStatus?.toMessageRes(): Int = when (this) {
    WearPhoneResourceResponseStatus.ACCESS_DENIED -> R.string.phone_resource_access_denied
    WearPhoneResourceResponseStatus.UNSUPPORTED_MEDIA -> R.string.phone_resource_unsupported
    WearPhoneResourceResponseStatus.TRANSFER_REJECTED -> R.string.phone_resource_transfer_rejected
    else -> R.string.phone_resource_unavailable
}
