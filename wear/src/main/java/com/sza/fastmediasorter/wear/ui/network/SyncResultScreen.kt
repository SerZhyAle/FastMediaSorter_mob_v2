package com.sza.fastmediasorter.wear.ui.network

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Text
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.ui.common.WEAR_LIST_NO_ANCHOR
import com.sza.fastmediasorter.wear.ui.common.WearListColumn
import com.sza.fastmediasorter.wear.ui.common.WearScreenScaffold
import com.sza.fastmediasorter.wear.ui.common.rememberWearListState
import com.sza.fastmediasorter.wear.ui.navigation.WearRoutes
import kotlinx.coroutines.delay

/**
 * Full-screen result screen shown after a successful sync operation.
 * Auto-dismisses to NetworkSourcesScreen after 8 seconds if the user takes no action.
 */
@Composable
fun SyncResultScreen(
    navController: NavController,
    added: Int,
    updated: Int
) {
    LaunchedEffect(Unit) {
        delay(8_000)
        returnToNetworkSources(navController, added, updated)
    }

    val listState = rememberWearListState(initialCenterItemIndex = WEAR_LIST_NO_ANCHOR)

    WearScreenScaffold(
        contentPadding = PaddingValues(0.dp),
        scrollState = listState,
        positionIndicator = { PositionIndicator(listState) }
    ) {
        WearListColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            centered = true
        ) {
            syncResultItems(navController, added, updated)
        }
    }
}

private fun ScalingLazyListScope.syncResultItems(
    navController: NavController,
    added: Int,
    updated: Int
) {
    item {
        Text(
            text = "✓",
            style = MaterialTheme.typography.display3,
            color = MaterialTheme.colors.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
    item {
        Text(
            text = stringResource(R.string.wear_sync_complete),
            style = MaterialTheme.typography.title3,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
    item {
        Text(
            text = stringResource(R.string.wear_sync_stats, added, updated),
            style = MaterialTheme.typography.body2,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
    item {
        Chip(
            onClick = { returnToNetworkSources(navController, added, updated) },
            label = {
                Text(
                    text = stringResource(R.string.wear_sync_browse_now),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ChipDefaults.primaryChipColors()
        )
    }
    item {
        Chip(
            onClick = { returnToNetworkSources(navController, added, updated) },
            label = {
                Text(
                    text = stringResource(R.string.done),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ChipDefaults.secondaryChipColors()
        )
    }
}

/**
 * The sync flow left Network Sources on the back stack beneath this screen, so pop back to that entry
 * instead of pushing a second list. Acting only while this screen is current keeps a late auto-dismiss or a
 * double tap from popping the list itself.
 */
private fun returnToNetworkSources(navController: NavController, added: Int, updated: Int) {
    if (navController.currentDestination?.route != WearRoutes.SYNC_RESULT_PATTERN) return
    if (!navController.popBackStack(WearRoutes.NETWORK_SOURCES, inclusive = false)) {
        navController.navigate(WearRoutes.NETWORK_SOURCES) {
            popUpTo(WearRoutes.syncResult(added, updated)) { inclusive = true }
        }
    }
}
