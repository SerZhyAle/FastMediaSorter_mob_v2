package com.sza.fastmediasorter.wear.ui.network

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Text
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.ui.common.WearScreenScaffold
import com.sza.fastmediasorter.wear.ui.common.wearScreenInsets
import com.sza.fastmediasorter.wear.ui.navigation.WearRoutes
import com.sza.fastmediasorter.wear.ui.settings.SettingsViewModel
import timber.log.Timber

private data class SourceMediaCategory(
    val labelRes: Int,
    val mediaType: String,
    val icon: ImageVector
)

/**
 * S1829: the media type a network source is opened under.
 *
 * Until this screen existed, [NetworkSourcesScreen] was the only way into a network source and it
 * passed a hard-coded "music", so images and video on SMB/FTP/SFTP were unreachable from the watch
 * even though the code that lists and plays them was already written. The type could not come from
 * anywhere else: unlike the watch's own media and the phone's, a network origin holds many containers,
 * so its type belongs to the source the user picked rather than to the origin.
 *
 * The same three settings that hide a category on [com.sza.fastmediasorter.wear.ui.home.LocalHomeScreen]
 * hide it here, so a watch with video turned off never offers a route into a list it would refuse to
 * fill.
 */
@Composable
fun NetworkSourceMediaTypeScreen(
    navController: NavController,
    sourceId: String,
    sourceName: String,
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val settings by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberScalingLazyListState()

    val categories = listOf(
        SourceMediaCategory(R.string.music, MEDIA_TYPE_MUSIC, Icons.Filled.MusicNote),
        SourceMediaCategory(R.string.videos, MEDIA_TYPE_VIDEOS, Icons.Filled.VideoLibrary),
        SourceMediaCategory(R.string.photos, MEDIA_TYPE_PHOTOS, Icons.Filled.Image)
    ).filter { category ->
        when (category.mediaType) {
            MEDIA_TYPE_MUSIC -> settings.isAudioEnabled
            MEDIA_TYPE_VIDEOS -> settings.isVideoEnabled
            MEDIA_TYPE_PHOTOS -> settings.isImagesEnabled
            else -> true
        }
    }

    // A choice between one option is not a choice. Pass straight through and drop this screen from the
    // back stack, so Back returns to the source list rather than to a step that decided nothing.
    LaunchedEffect(categories, sourceId) {
        val only = categories.singleOrNull() ?: return@LaunchedEffect
        Timber.d("S1829: only '%s' enabled, skipping the step for '%s'", only.mediaType, sourceName)
        navController.navigate(WearRoutes.browseSource(only.mediaType, sourceId, sourceName)) {
            popUpTo(WearRoutes.SOURCE_MEDIA_TYPE_PATTERN) { inclusive = true }
        }
    }

    WearScreenScaffold(
        contentPadding = PaddingValues(0.dp),
        scrollState = listState,
        positionIndicator = { PositionIndicator(listState) }
    ) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = wearScreenInsets()
        ) {
            item {
                Text(
                    text = sourceName,
                    style = MaterialTheme.typography.title3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    textAlign = TextAlign.Center
                )
            }

            items(categories) { category ->
                Chip(
                    onClick = {
                        Timber.d("S1829: opening '%s' as '%s'", sourceName, category.mediaType)
                        navController.navigate(
                            WearRoutes.browseSource(category.mediaType, sourceId, sourceName)
                        )
                    },
                    label = { Text(text = stringResource(category.labelRes)) },
                    icon = {
                        Icon(
                            imageVector = category.icon,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ChipDefaults.primaryChipColors()
                )
            }
        }
    }
}

// The route argument values BrowseScreen parses back into MediaType; they are a navigation contract,
// not display text, so they stay literal on both ends.
private const val MEDIA_TYPE_MUSIC = "music"
private const val MEDIA_TYPE_VIDEOS = "videos"
private const val MEDIA_TYPE_PHOTOS = "photos"
