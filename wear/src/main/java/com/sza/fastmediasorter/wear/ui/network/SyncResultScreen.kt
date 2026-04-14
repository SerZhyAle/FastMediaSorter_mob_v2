package com.sza.fastmediasorter.wear.ui.network

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.sza.fastmediasorter.wear.R
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
        navController.navigate("network_sources") {
            popUpTo("sync_result/$added/$updated") { inclusive = true }
        }
    }

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
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
                onClick = {
                    navController.navigate("network_sources") {
                        popUpTo("sync_result/$added/$updated") { inclusive = true }
                    }
                },
                label = { Text(stringResource(R.string.wear_sync_browse_now)) },
                modifier = Modifier.fillMaxWidth(),
                colors = ChipDefaults.primaryChipColors()
            )
        }
        item {
            Chip(
                onClick = {
                    navController.navigate("network_sources") {
                        popUpTo("sync_result/$added/$updated") { inclusive = true }
                    }
                },
                label = { Text(stringResource(R.string.done)) },
                modifier = Modifier.fillMaxWidth(),
                colors = ChipDefaults.secondaryChipColors()
            )
        }
    }
}
