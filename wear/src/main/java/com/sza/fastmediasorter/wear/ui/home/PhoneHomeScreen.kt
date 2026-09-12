package com.sza.fastmediasorter.wear.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.browse.BrowseCategoryCatalog
import com.sza.fastmediasorter.wear.domain.model.WearCategoryOrigin
import com.sza.fastmediasorter.wear.ui.navigation.WearRoutes
import com.sza.fastmediasorter.wear.ui.settings.SettingsViewModel
import com.sza.fastmediasorter.wear.ui.settings.allowedContentTypes

/**
 * The phone's virtual resources, reached from the watch.
 *
 * Uses the unified [OriginHomeScreen] container for rendering category items and phone folder browsing.
 */
@Composable
fun PhoneHomeScreen(
    navController: NavController,
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val settings by settingsViewModel.uiState.collectAsStateWithLifecycle()

    val categories = BrowseCategoryCatalog.categoriesFor(
        WearCategoryOrigin.PHONE,
        settings.allowedContentTypes()
    )

    OriginHomeScreen(
        title = stringResource(R.string.wear_section_phone),
        categories = categories,
        viewMode = settings.viewMode,
        onCategoryClick = { category -> navController.navigate(WearRoutes.browsePhone(category.token)) },
        onBack = { navController.popBackStack() },
        onFolderClick = { navController.navigate(WearRoutes.PHONE_RESOURCE) },
        positionKey = WearRoutes.PHONE_HOME
    )
}
