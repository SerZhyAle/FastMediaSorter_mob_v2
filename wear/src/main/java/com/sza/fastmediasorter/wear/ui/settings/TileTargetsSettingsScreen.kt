package com.sza.fastmediasorter.wear.ui.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Text
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.ui.common.WearListColumn
import com.sza.fastmediasorter.wear.ui.common.WearScreenScaffold
import com.sza.fastmediasorter.wear.ui.common.rememberWearListState
import com.sza.fastmediasorter.wear.ui.navigation.WearRoutes

private val TITLE_BOTTOM_PADDING = 8.dp

/**
 * S2587: the settings entry that lets an already-assigned tile be pointed somewhere else.
 *
 * Before this screen the target picker had exactly one caller - the "select target" chip a tile draws only
 * while it is unassigned or its target has vanished - so a tile that worked could never be re-pointed except
 * by removing it from the watch carousel and adding it back. This screen is the second caller; the picker,
 * its ViewModel and the assignment repository are untouched.
 *
 * A row names a KIND, not a pinned tile, because that is what the assignment is stored under (S1955 ADR-3):
 * an affordance on one tile would promise a per-instance change the storage cannot make.
 */
@Composable
fun TileTargetsSettingsScreen(
    navController: NavController,
    listState: ScalingLazyListState = rememberWearListState(positionKey = SettingsRoutes.TILE_TARGETS),
    viewModel: TileTargetsSettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    // Re-read on every resume: the picker writes the new target and pops straight back here, so a one-shot
    // load would leave the row naming the target the user has just replaced.
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.refresh()
        }
    }

    WearScreenScaffold(
        contentPadding = PaddingValues(0.dp),
        scrollState = listState,
        positionIndicator = { PositionIndicator(listState) }
    ) {
        WearListColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState
        ) {
            item {
                Text(
                    text = stringResource(R.string.wear_tile_targets_title),
                    style = MaterialTheme.typography.title2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = TITLE_BOTTOM_PADDING),
                    textAlign = TextAlign.Center
                )
            }
            items(uiState.rows) { row ->
                Chip(
                    onClick = {
                        navController.navigate(WearRoutes.tileTargetPicker(row.kind.name))
                    },
                    label = { Text(stringResource(row.labelRes)) },
                    secondaryLabel = { Text(captionText(row.caption)) }
                )
            }
        }
    }
}

@Composable
private fun captionText(caption: TileTargetCaption): String = when (caption) {
    is TileTargetCaption.Assigned -> caption.title
    TileTargetCaption.Missing -> stringResource(R.string.wear_tile_target_missing)
    TileTargetCaption.None -> stringResource(R.string.wear_tile_targets_none)
}
