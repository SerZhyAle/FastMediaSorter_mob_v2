package com.sza.fastmediasorter.wear.ui.home

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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

private data class PhoneCategory(
    val labelRes: Int,
    val mediaType: String,
    val icon: ImageVector
)

/**
 * The phone's virtual resources, reached from the watch.
 *
 * Registered sources that live on the phone are deliberately absent: they belong to the Resources
 * section, which is the whole point of splitting these two by content origin rather than media type.
 * The last row keeps the paired-phone folder browser reachable - it is a separate capability that
 * only ever had its entrance on the home screen.
 */
@Composable
fun PhoneHomeScreen(navController: NavController) {
    val listState = rememberScalingLazyListState()

    val categories = listOf(
        PhoneCategory(R.string.wear_phone_recents, "recents", Icons.Filled.History),
        PhoneCategory(R.string.wear_phone_video, "videos", Icons.Filled.VideoLibrary),
        PhoneCategory(R.string.wear_phone_audio, "music", Icons.Filled.MusicNote),
        PhoneCategory(R.string.wear_phone_images, "photos", Icons.Filled.Image),
        PhoneCategory(R.string.wear_phone_documents, "documents", Icons.Filled.Description),
        PhoneCategory(R.string.wear_phone_all_files, "all", Icons.Filled.SelectAll)
    )

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
                    text = stringResource(R.string.wear_section_phone),
                    style = MaterialTheme.typography.title3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    textAlign = TextAlign.Center
                )
            }

            items(categories) { category ->
                Chip(
                    onClick = { navController.navigate(WearRoutes.browsePhone(category.mediaType)) },
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

            item {
                Chip(
                    onClick = { navController.navigate(WearRoutes.PHONE_RESOURCE) },
                    label = { Text(text = stringResource(R.string.phone_resource_title)) },
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.Folder,
                            contentDescription = stringResource(R.string.phone_resource_title),
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ChipDefaults.secondaryChipColors()
                )
            }
        }
    }
}
