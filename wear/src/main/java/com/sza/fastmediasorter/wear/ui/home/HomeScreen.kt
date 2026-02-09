package com.sza.fastmediasorter.wear.ui.home

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.ui.settings.SettingsViewModel
import timber.log.Timber

@Composable
fun HomeScreen(
    navController: NavController,
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    Timber.d("HomeScreen composing")
    
    val settings by settingsViewModel.uiState.collectAsStateWithLifecycle()
    
    val allCategories = listOf(
        MediaCategory("🎵 " + stringResource(R.string.music), "music"),
        MediaCategory("🎬 " + stringResource(R.string.videos), "videos"),
        MediaCategory("🖼️ " + stringResource(R.string.photos), "photos")
    )
    
    // Filter categories based on settings
    val filteredCategories = allCategories.filter { category ->
        when (category.route) {
            "music" -> settings.isAudioEnabled
            "videos" -> settings.isVideoEnabled
            "photos" -> settings.isImagesEnabled
            else -> true
        }
    }
    
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.title2,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                textAlign = TextAlign.Center
            )
        }
        
        items(filteredCategories) { category ->
            Chip(
                onClick = {
                    Timber.d("Category clicked: ${category.route}")
                    navController.navigate("browse/${category.route}")
                },
                label = {
                    Text(text = category.name)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ChipDefaults.primaryChipColors()
            )
        }
        
        // Network Storage button
        item {
            Chip(
                onClick = {
                    Timber.d("Network Storage clicked")
                    navController.navigate("network_sources")
                },
                label = {
                    Text(text = "📡 " + stringResource(R.string.network_storage))
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ChipDefaults.primaryChipColors()
            )
        }
        
        // Settings button
        item {
            Chip(
                onClick = {
                    Timber.d("Settings clicked")
                    navController.navigate("settings")
                },
                label = {
                    Text(text = "⚙️ " + stringResource(R.string.settings))
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ChipDefaults.secondaryChipColors()
            )
        }
    }
}

data class MediaCategory(
    val name: String,
    val route: String
)
