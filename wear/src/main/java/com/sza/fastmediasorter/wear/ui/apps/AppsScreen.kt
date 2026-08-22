package com.sza.fastmediasorter.wear.ui.apps

import androidx.annotation.DrawableRes
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
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
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Text
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.model.WearApp
import com.sza.fastmediasorter.wear.domain.model.WearAppId
import com.sza.fastmediasorter.wear.ui.common.WearScreenScaffold
import com.sza.fastmediasorter.wear.ui.common.wearScreenInsets
import com.sza.fastmediasorter.wear.util.GridColumnFit

private const val SINGLE_COLUMN = 1
private val GRID_GAP = GridColumnFit.DEFAULT_GAP_DP.dp
private val CELL_BUTTON_SIZE = GridColumnFit.DEFAULT_MIN_TARGET_DP.dp
private val CELL_ICON_SIZE = 24.dp
private val TITLE_VERTICAL_PADDING = 16.dp

@Composable
fun AppsScreen(
    navController: NavController,
    viewModel: AppsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberScalingLazyListState()

    WearScreenScaffold(
        contentPadding = PaddingValues(0.dp),
        scrollState = listState,
        positionIndicator = { PositionIndicator(listState) }
    ) {
        // The column count comes from the width this composable actually gets, so the Apps list and
        // the home screen never disagree about how many columns fit the same display.
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val columns = GridColumnFit.columnsFor(uiState.viewMode, maxWidth.value.toInt())
            ScalingLazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = wearScreenInsets()
            ) {
                item {
                    Text(
                        text = stringResource(R.string.wear_section_apps),
                        style = MaterialTheme.typography.title2,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = TITLE_VERTICAL_PADDING),
                        textAlign = TextAlign.Center
                    )
                }

                appItems(
                    apps = uiState.apps,
                    columns = columns,
                    onAppClick = { app -> navController.navigate(app.route) }
                )
            }
        }
    }
}

private fun ScalingLazyListScope.appItems(
    apps: List<WearApp>,
    columns: Int,
    onAppClick: (WearApp) -> Unit
) {
    if (columns == SINGLE_COLUMN) {
        items(apps) { app ->
            AppChip(app = app, onClick = { onAppClick(app) })
        }
    } else {
        items(apps.chunked(columns)) { rowApps ->
            AppRow(apps = rowApps, columns = columns, onAppClick = onAppClick)
        }
    }
}

@Composable
private fun AppChip(
    app: WearApp,
    onClick: () -> Unit
) {
    val label = stringResource(app.labelRes)
    Chip(
        onClick = onClick,
        label = { Text(text = label) },
        icon = {
            Icon(
                painter = painterResource(iconFor(app.id)),
                contentDescription = label,
                modifier = Modifier.size(CELL_ICON_SIZE)
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = label },
        colors = ChipDefaults.primaryChipColors()
    )
}

/** A short row is padded with empty weights so its cells keep the width of a full row's cells. */
@Composable
private fun AppRow(
    apps: List<WearApp>,
    columns: Int,
    onAppClick: (WearApp) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(GRID_GAP)
    ) {
        apps.forEach { app ->
            AppCell(
                app = app,
                modifier = Modifier.weight(1f),
                onClick = { onAppClick(app) }
            )
        }
        repeat(columns - apps.size) {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun AppCell(
    app: WearApp,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val label = stringResource(app.labelRes)
    Column(
        modifier = modifier.semantics { contentDescription = label },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(
            onClick = onClick,
            // CELL_BUTTON_SIZE is the interactive minimum itself, so a grid cell keeps a reachable
            // target no matter which view mode produced it.
            modifier = Modifier.size(CELL_BUTTON_SIZE),
            colors = ButtonDefaults.primaryButtonColors()
        ) {
            Icon(
                painter = painterResource(iconFor(app.id)),
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

/**
 * Icons stay here rather than on the program record, matching the home screen's own reason: the
 * domain layer carries no drawing concern.
 *
 * The network monitor reuses the Wi-Fi glyph the home screen already gives to Resources, because on a
 * watch both mean the same thing to look at - the radio - and inventing a second one would tell the
 * user they are different.
 */
@DrawableRes
private fun iconFor(id: WearAppId): Int = when (id) {
    WearAppId.CALCULATOR -> R.drawable.ic_app_calculator
    WearAppId.NETWORK_MONITOR -> R.drawable.ic_wifi
    WearAppId.GAME -> R.drawable.ic_app_game
}
