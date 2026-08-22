package com.sza.fastmediasorter.wear.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.wear.domain.model.HomeSectionVisibility
import com.sza.fastmediasorter.wear.domain.model.LastUsedResource
import com.sza.fastmediasorter.wear.domain.model.WearViewMode
import com.sza.fastmediasorter.wear.domain.repository.WearPreferencesRepository
import com.sza.fastmediasorter.wear.domain.usecase.ResolveLastUsedResourceUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import timber.log.Timber
import javax.inject.Inject

private const val SUBSCRIPTION_TIMEOUT_MS = 5_000L

/**
 * Turns the three visibility sources into the section list the home screen renders.
 *
 * The screen used to filter its own hardcoded chips; moving that here is what lets a new section be
 * added to the catalog without touching the screen at all.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val preferencesRepository: WearPreferencesRepository,
    private val resolveLastUsedResource: ResolveLastUsedResourceUseCase
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = combine(
        resolveLastUsedResource(),
        preferencesRepository.streamsSectionEnabled,
        preferencesRepository.viewMode
    ) { lastUsedResource, streamsEnabled, viewMode ->
        HomeSources(lastUsedResource, streamsEnabled, viewMode)
    }.map { sources ->
        Timber.d("S1940: home sections rebuilt, favourites always last")
        HomeUiState(
            sections = HomeSectionCatalog.sectionsFor(
                HomeSectionVisibility(
                    lastUsedResource = sources.lastUsedResource,
                    streamsEnabled = sources.streamsEnabled
                )
            ),
            viewMode = sources.viewMode
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
        initialValue = HomeUiState()
    )
}

/** Carries the three observed preferences into one emission so the mapping reads by name. */
private data class HomeSources(
    val lastUsedResource: LastUsedResource?,
    val streamsEnabled: Boolean,
    val viewMode: WearViewMode
)
