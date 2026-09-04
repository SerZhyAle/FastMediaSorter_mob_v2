package com.sza.fastmediasorter.wear.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PermMedia
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.runtime.Composable
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
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Text
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.ui.common.WearListColumn
import com.sza.fastmediasorter.wear.ui.common.WearScreenScaffold
import com.sza.fastmediasorter.wear.ui.common.rememberWearListState
import com.sza.fastmediasorter.wear.util.GridColumnFit
import timber.log.Timber
import java.text.DateFormat
import java.util.Date

private const val SINGLE_COLUMN = 1
private const val MENU_LABEL_MAX_LINES = 2
private val GRID_GAP = GridColumnFit.DEFAULT_GAP_DP.dp
private val CELL_BUTTON_SIZE = GridColumnFit.DEFAULT_MIN_TARGET_DP.dp
private val CELL_ICON_SIZE = 24.dp
private val SYNC_CELL_TOP_PADDING = 8.dp

@Composable
fun SettingsScreen(
    navController: NavController,
    listState: ScalingLazyListState = rememberWearListState(initialItemIndex = 1),
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    WearScreenScaffold(
        contentPadding = PaddingValues(0.dp),
        scrollState = listState,
        positionIndicator = { PositionIndicator(listState) }
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val columns = GridColumnFit.columnsFor(uiState.viewMode, maxWidth.value.toInt())
            val destinations = listOf(
                SettingsRoutes.MEDIA_TYPES to stringResource(R.string.media_types),
                SettingsRoutes.SLIDESHOW to stringResource(R.string.slideshow_settings),
                SettingsRoutes.SCREEN to stringResource(R.string.screen_settings_title),
                SettingsRoutes.OTHER to stringResource(R.string.settings_group_other),
                SettingsRoutes.ABOUT to stringResource(R.string.about)
            )
            WearListColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(GRID_GAP)
            ) {
                item {
                    Text(
                        text = stringResource(R.string.settings),
                        style = MaterialTheme.typography.title2,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        textAlign = TextAlign.Center
                    )
                }
                settingsItems(
                    destinations = destinations,
                    columns = columns,
                    onClick = navController::navigate
                )
                item {
                    SyncSettingsCell(
                        lastSyncedAtEpochMillis = uiState.lastSyncedAtEpochMillis,
                        syncing = uiState.isSyncing,
                        onSync = viewModel::syncSettings
                    )
                }
            }
        }
    }
}

private fun ScalingLazyListScope.settingsItems(
    destinations: List<Pair<String, String>>,
    columns: Int,
    onClick: (String) -> Unit
) {
    if (columns == SINGLE_COLUMN) {
        items(destinations) { (route, label) ->
            Chip(onClick = { onClick(route) }, label = { Text(label) })
        }
        return
    }

    items(destinations.chunked(columns)) { rowDestinations ->
        com.sza.fastmediasorter.wear.ui.common.CenteredGridRow(
            columns = columns,
            itemCount = rowDestinations.size,
            gap = GRID_GAP
        ) {
            rowDestinations.forEach { (route, label) ->
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .semantics { contentDescription = label },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Column(
                        modifier = Modifier
                            .size(CELL_BUTTON_SIZE)
                            .clickable {
                                Timber.d("S2478: opened settings destination $route from the icon grid")
                                onClick(route)
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = iconFor(route),
                            contentDescription = null,
                            modifier = Modifier.size(CELL_ICON_SIZE)
                        )
                    }
                    Text(
                        // Wrap, never ellipsize: strategic S2042, same rule S1949 already applied
                        // to the settings screens themselves via WearSettingsToggleCell.
                        text = label,
                        style = MaterialTheme.typography.caption3,
                        maxLines = MENU_LABEL_MAX_LINES,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

/**
 * S2093: the watch half of the symmetric sync control - one button with the same name the phone's
 * companion window uses, and beneath it when the two sides last agreed.
 *
 * The caption reads from the stored sync time rather than from the press, so a press that reached
 * nothing leaves the old time standing instead of claiming a sync that did not happen.
 */
@Composable
private fun SyncSettingsCell(
    lastSyncedAtEpochMillis: Long,
    syncing: Boolean,
    onSync: () -> Unit
) {
    val caption = if (lastSyncedAtEpochMillis <= 0L) {
        stringResource(R.string.wear_settings_sync_never)
    } else {
        stringResource(R.string.wear_settings_last_synced, formatSyncTime(lastSyncedAtEpochMillis))
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = SYNC_CELL_TOP_PADDING),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Chip(
            onClick = onSync,
            enabled = !syncing,
            label = { Text(stringResource(R.string.wear_settings_sync_button)) }
        )
        Text(
            text = caption,
            style = MaterialTheme.typography.caption3,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// Short local date and time rather than a full timestamp: the caption sits under a chip on a round
// screen, where a long form wraps to three lines and pushes the chip off the readable band.
private fun formatSyncTime(epochMillis: Long): String = DateFormat
    .getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
    .format(Date(epochMillis))

private fun iconFor(route: String) = when (route) {
    SettingsRoutes.MEDIA_TYPES -> Icons.Filled.PermMedia
    SettingsRoutes.SLIDESHOW -> Icons.Filled.Slideshow
    SettingsRoutes.SCREEN -> Icons.Filled.Settings
    SettingsRoutes.OTHER -> Icons.Filled.MoreHoriz
    SettingsRoutes.ABOUT -> Icons.Filled.Info
    else -> Icons.Filled.Settings
}
