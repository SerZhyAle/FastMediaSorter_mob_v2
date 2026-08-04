package com.sza.fastmediasorter.ui.browse.managers

import com.sza.fastmediasorter.domain.usecase.FavoritesUseCase
import com.sza.fastmediasorter.domain.usecase.GetResourcesUseCase
import com.sza.fastmediasorter.domain.usecase.MaterializeFavoritesUseCase

data class BrowseStateSyncUseCases(
    val favoritesUseCase: FavoritesUseCase,
    val materializeFavoritesUseCase: MaterializeFavoritesUseCase,
    val getResourcesUseCase: GetResourcesUseCase,
)
