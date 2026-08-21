package com.sza.fastmediasorter.wear.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Text
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.ui.common.WearScreenScaffold
import com.sza.fastmediasorter.wear.ui.common.wearScreenInsets
import timber.log.Timber

@Composable
fun SettingsScreen(
    navController: NavController,
    listState: ScalingLazyListState = rememberScalingLazyListState()
) {
    Timber.d("S1724: grouped settings root displayed")
    WearScreenScaffold(
        contentPadding = PaddingValues(0.dp),
        scrollState = listState,
        positionIndicator = { PositionIndicator(listState) }
    ) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = wearScreenInsets(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
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
            item {
                Chip(
                    onClick = { navController.navigate(SettingsRoutes.MEDIA_TYPES) },
                    label = { Text(stringResource(R.string.media_types)) }
                )
            }
            item {
                Chip(
                    onClick = { navController.navigate(SettingsRoutes.SLIDESHOW) },
                    label = { Text(stringResource(R.string.slideshow_settings)) }
                )
            }
            item {
                Chip(
                    onClick = { navController.navigate(SettingsRoutes.SCREEN) },
                    label = { Text(stringResource(R.string.screen_settings_title)) }
                )
            }
            item {
                Chip(
                    onClick = { navController.navigate(SettingsRoutes.OTHER) },
                    label = { Text(stringResource(R.string.settings_group_other)) }
                )
            }
            item {
                Chip(
                    onClick = { navController.navigate(SettingsRoutes.ABOUT) },
                    label = { Text(stringResource(R.string.about)) }
                )
            }
            item {
                Chip(
                    onClick = { navController.navigate(SettingsRoutes.SYSTEM_INFO) },
                    label = { Text(stringResource(R.string.system_info_title)) }
                )
            }
        }
    }
}
