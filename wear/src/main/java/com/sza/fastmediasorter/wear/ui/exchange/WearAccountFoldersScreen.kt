package com.sza.fastmediasorter.wear.ui.exchange

import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Text
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.model.WearAccountFolderRow
import com.sza.fastmediasorter.wear.ui.common.WearListColumn
import com.sza.fastmediasorter.wear.ui.common.WearScreenScaffold
import com.sza.fastmediasorter.wear.ui.common.rememberWearListState
import com.sza.fastmediasorter.wear.ui.settings.SettingsRoutes

/**
 * S4228: the folders the account of one exchange server shares, read by the watch itself (contract DEVICE-EXCHANGE
 * 23 item CF). A tap adds one to the network resources; a protected one is added locked and opens once the phone
 * hands its PIN over. The list scrolls by the bezel through [WearListColumn], whose rotary scroll is on by default.
 */
@Composable
fun WearAccountFoldersScreen(
    viewModel: WearAccountFoldersViewModel = hiltViewModel(),
    listState: ScalingLazyListState = rememberWearListState(positionKey = SettingsRoutes.EXCHANGE_ACCOUNT_FOLDERS),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val added = stringResource(R.string.exchange_account_folder_added)
    val addFailed = stringResource(R.string.exchange_account_folder_add_failed)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            val text = if (event == WearAccountFolderEvent.ADDED) added else addFailed
            Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
        }
    }

    WearScreenScaffold(
        contentPadding = PaddingValues(0.dp),
        scrollState = listState,
        positionIndicator = { PositionIndicator(listState) },
    ) {
        WearListColumn(modifier = Modifier.fillMaxSize(), state = listState, centered = true, rotary = true) {
            item {
                Text(
                    text = stringResource(R.string.exchange_account_folders),
                    style = MaterialTheme.typography.title2,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    textAlign = TextAlign.Center,
                )
            }
            folderItems(state, onAttach = viewModel::attach, onRetry = viewModel::retry)
        }
    }
}

private fun ScalingLazyListScope.folderItems(
    state: WearAccountFoldersUiState,
    onAttach: (WearAccountFolderRow) -> Unit,
    onRetry: () -> Unit,
) {
    when (state) {
        WearAccountFoldersUiState.Loading -> item { StatusLine(R.string.exchange_account_folders_loading) }
        WearAccountFoldersUiState.Failed -> {
            item { StatusLine(R.string.exchange_account_folders_failed) }
            item {
                Chip(
                    onClick = onRetry,
                    label = { Text(text = stringResource(R.string.retry), maxLines = 1) },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        is WearAccountFoldersUiState.Listed -> if (state.rows.isEmpty()) {
            item { StatusLine(R.string.exchange_account_folders_empty) }
        } else {
            items(state.rows, key = { it.folder.resourceId }) { row -> FolderChip(row, onAttach) }
        }
    }
}

@Composable
private fun FolderChip(row: WearAccountFolderRow, onAttach: (WearAccountFolderRow) -> Unit) {
    val caption = when {
        row.isOnWatch -> stringResource(R.string.exchange_account_folder_on_watch)
        row.folder.pinProtected -> stringResource(R.string.exchange_account_folder_locked)
        else -> null
    }
    Chip(
        onClick = { onAttach(row) },
        label = { Text(text = row.folder.name, maxLines = 1) },
        secondaryLabel = caption?.let { { Text(text = it, maxLines = 1) } },
        icon = if (row.folder.pinProtected) {
            {
                Icon(
                    painter = painterResource(R.drawable.ic_lock),
                    contentDescription = stringResource(R.string.exchange_account_folder_locked),
                )
            }
        } else {
            null
        },
        colors = if (row.isOnWatch) ChipDefaults.secondaryChipColors() else ChipDefaults.primaryChipColors(),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun StatusLine(@StringRes text: Int) {
    Text(
        text = stringResource(text),
        style = MaterialTheme.typography.body2,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        textAlign = TextAlign.Center,
    )
}
