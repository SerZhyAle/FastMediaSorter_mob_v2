package com.sza.fastmediasorter.wear.ui.settings

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
import androidx.compose.material.icons.filled.Memory
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Text
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.ui.common.WearScreenScaffold
import com.sza.fastmediasorter.wear.ui.common.wearScreenInsets
import com.sza.fastmediasorter.wear.util.GridColumnFit
import timber.log.Timber

private const val SINGLE_COLUMN = 1
private val GRID_GAP = GridColumnFit.DEFAULT_GAP_DP.dp
private val CELL_BUTTON_SIZE = GridColumnFit.DEFAULT_MIN_TARGET_DP.dp
private val CELL_ICON_SIZE = 24.dp

@Composable
fun SettingsScreen(
    navController: NavController,
    listState: ScalingLazyListState = rememberScalingLazyListState(),
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    Timber.d("S1724: grouped settings root displayed")
    Timber.d("S1868: settings root applies the shared screen view")
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
                SettingsRoutes.ABOUT to stringResource(R.string.about),
                SettingsRoutes.SYSTEM_INFO to stringResource(R.string.system_info_title)
            )
            ScalingLazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = wearScreenInsets(),
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(GRID_GAP)
        ) {
            rowDestinations.forEach { (route, label) ->
                Column(
                    modifier = Modifier.weight(1f).semantics { contentDescription = label },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Button(
                        onClick = { onClick(route) },
                        modifier = Modifier.size(CELL_BUTTON_SIZE),
                        colors = ButtonDefaults.primaryButtonColors()
                    ) {
                        Icon(
                            imageVector = iconFor(route),
                            contentDescription = label,
                            modifier = Modifier.size(CELL_ICON_SIZE)
                        )
                    }
                    Text(
                        text = label,
                        style = MaterialTheme.typography.caption3,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            repeat(columns - rowDestinations.size) { Spacer(modifier = Modifier.weight(1f)) }
        }
    }
}

private fun iconFor(route: String) = when (route) {
    SettingsRoutes.MEDIA_TYPES -> Icons.Filled.PermMedia
    SettingsRoutes.SLIDESHOW -> Icons.Filled.Slideshow
    SettingsRoutes.SCREEN -> Icons.Filled.Settings
    SettingsRoutes.OTHER -> Icons.Filled.MoreHoriz
    SettingsRoutes.ABOUT -> Icons.Filled.Info
    SettingsRoutes.SYSTEM_INFO -> Icons.Filled.Memory
    else -> Icons.Filled.Settings
}
