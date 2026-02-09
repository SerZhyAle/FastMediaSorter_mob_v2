package com.sza.fastmediasorter.wear.ui.network

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.ui.network.viewmodel.NetworkSourcesUiState
import com.sza.fastmediasorter.wear.ui.network.viewmodel.NetworkSourcesViewModel
import timber.log.Timber

/**
 * Screen for displaying available SMB network sources.
 * Shows list of saved connections and allows browsing files on selected source.
 */
@Composable
fun NetworkSourcesScreen(
    navController: NavController,
    viewModel: NetworkSourcesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    Timber.d("NetworkSourcesScreen composing with state: $uiState")
    
    when (val state = uiState) {
        is NetworkSourcesUiState.Loading -> {
            LoadingContent()
        }
        is NetworkSourcesUiState.Success -> {
            SourcesListContent(
                sources = state.sources,
                onSourceClick = { sourceId, sourceName ->
                    Timber.d("Source selected: $sourceName (ID: $sourceId)")
                    // Navigate to browse screen with source ID
                    navController.navigate("browse/music?sourceId=$sourceId&sourceName=$sourceName")
                },
                onAddClick = {
                    navController.navigate("add_smb")
                }
            )
        }
        is NetworkSourcesUiState.Empty -> {
            EmptyContent(onAddClick = {
                navController.navigate("add_smb")
            })
        }
        is NetworkSourcesUiState.Error -> {
            ErrorContent(
                message = state.message,
                onRetry = {
                    Timber.d("Retrying network sources load")
                    // Would need a way to retry - for now just navigate back
                    navController.popBackStack()
                }
            )
        }
    }
}

@Composable
private fun LoadingContent() {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        Text(
            text = "⏳ Loading...",
            style = MaterialTheme.typography.body2,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SourcesListContent(
    sources: List<SourceItem>,
    onSourceClick: (String, String) -> Unit,
    onAddClick: () -> Unit
) {
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Text(
                text = stringResource(R.string.network_storage),
                style = MaterialTheme.typography.title2,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                textAlign = TextAlign.Center
            )
        }
        
        items(sources) { source ->
            Chip(
                onClick = { onSourceClick(source.id, source.name) },
                label = {
                    Text(text = "📡 ${source.name}\n${source.server}")
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ChipDefaults.primaryChipColors()
            )
        }
        
        item {
            Chip(
                onClick = onAddClick,
                label = {
                    Text(text = stringResource(R.string.add_smb_connection))
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ChipDefaults.secondaryChipColors()
            )
        }
    }
}

@Composable
private fun EmptyContent(onAddClick: () -> Unit) {
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Text(
                text = stringResource(R.string.network_storage),
                style = MaterialTheme.typography.title2,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                textAlign = TextAlign.Center
            )
        }
        
        item {
            Text(
                text = "No network sources configured",
                style = MaterialTheme.typography.body2,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                textAlign = TextAlign.Center
            )
        }
        
        item {
            Chip(
                onClick = onAddClick,
                label = {
                    Text(text = stringResource(R.string.add_smb_connection))
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ChipDefaults.primaryChipColors()
            )
        }
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit
) {
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Text(
                text = "Error",
                style = MaterialTheme.typography.title2,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                textAlign = TextAlign.Center
            )
        }
        
        item {
            Text(
                text = message,
                style = MaterialTheme.typography.body2,
                color = MaterialTheme.colors.error,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                textAlign = TextAlign.Center
            )
        }
        
        item {
            Chip(
                onClick = onRetry,
                label = {
                    Text(text = "Retry")
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ChipDefaults.secondaryChipColors()
            )
        }
    }
}

data class SourceItem(
    val id: String,
    val name: String,
    val server: String
)
