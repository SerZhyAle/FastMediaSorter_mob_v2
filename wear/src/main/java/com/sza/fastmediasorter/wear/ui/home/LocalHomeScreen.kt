package com.sza.fastmediasorter.wear.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.browse.BrowseCategoryCatalog
import com.sza.fastmediasorter.wear.domain.model.WearBrowseCategory
import com.sza.fastmediasorter.wear.domain.model.WearCategoryOrigin
import com.sza.fastmediasorter.wear.ui.navigation.WearRoutes
import com.sza.fastmediasorter.wear.ui.settings.SettingsViewModel
import com.sza.fastmediasorter.wear.ui.settings.allowedContentTypes
import timber.log.Timber

/**
 * Media stored on the watch itself, split by type.
 *
 * Uses the unified [OriginHomeScreen] container for rendering category items and folder browsing.
 */
@Composable
fun LocalHomeScreen(
    navController: NavController,
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val settings by settingsViewModel.uiState.collectAsStateWithLifecycle()

    val categories = BrowseCategoryCatalog.categoriesFor(
        WearCategoryOrigin.LOCAL,
        settings.allowedContentTypes()
    )

    // A choice between one option is not a choice. Pass straight through and drop this screen from the
    // back stack, so Back returns to the home screen rather than to a step that decided nothing.
    LaunchedEffect(categories) {
        val only = categories.singleOrNull() ?: return@LaunchedEffect
        navController.navigate(routeForCategory(only)) {
            popUpTo(WearRoutes.LOCAL_HOME) { inclusive = true }
        }
    }

    OriginHomeScreen(
        title = stringResource(R.string.wear_section_local),
        categories = categories,
        viewMode = settings.viewMode,
        onCategoryClick = { category -> navController.navigate(routeForCategory(category)) },
        onBack = { navController.popBackStack() },
        onFolderClick = { navController.navigate(WearRoutes.localFolderRoot()) },
        positionKey = WearRoutes.LOCAL_HOME
    )
}

/**
 * S2495: voice notes leave the media route entirely. The note list reads the app's own index rather
 * than a MediaStore query, so it takes no media-type argument - the same reason browse has its own
 * route and not a token in one.
 */
private fun routeForCategory(category: WearBrowseCategory): String {
    Timber.d("S2495: local category tapped: %s", category.token)
    return routeOf(category)
}

private fun routeOf(category: WearBrowseCategory): String = when (category.token) {
    BrowseCategoryCatalog.TOKEN_BROWSE -> WearRoutes.localFolderRoot()
    BrowseCategoryCatalog.TOKEN_VOICE_NOTES -> WearRoutes.VOICE_NOTES
    else -> WearRoutes.browse(category.token)
}
