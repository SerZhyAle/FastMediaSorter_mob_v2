package com.sza.fastmediasorter.wear.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.model.HomeSection
import com.sza.fastmediasorter.wear.domain.model.HomeSectionId
import com.sza.fastmediasorter.wear.domain.model.HomeSectionVisibility
import com.sza.fastmediasorter.wear.domain.model.LastUsedKind
import com.sza.fastmediasorter.wear.domain.model.LastUsedResource
import com.sza.fastmediasorter.wear.domain.model.WearLaunchTarget
import com.sza.fastmediasorter.wear.domain.model.WearTileTargetRef
import com.sza.fastmediasorter.wear.domain.model.WearViewMode
import com.sza.fastmediasorter.wear.domain.repository.WearNowPlayingRepository
import com.sza.fastmediasorter.wear.domain.repository.WearPreferencesRepository
import com.sza.fastmediasorter.wear.domain.usecase.ResolveLastUsedResourceUseCase
import com.sza.fastmediasorter.wear.domain.usecase.ResolveWearLaunchRouteUseCase
import com.sza.fastmediasorter.wear.ui.navigation.WearRoutes
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
    private val resolveLastUsedResource: ResolveLastUsedResourceUseCase,
    private val resolveLaunchRoute: ResolveWearLaunchRouteUseCase,
    nowPlayingRepository: WearNowPlayingRepository
) : ViewModel() {

    /**
     * S2472: whether sound is alive right now, for the home affordance's cross/chevron choice.
     *
     * False until the store answers on purpose: a chevron drawn on a guess would promise the user
     * their playback survived when it may not have. The flag this reads is the one the playback
     * service itself keeps truthful across the hand-off (it clears the flag in its own teardown),
     * so composition never has to poll anything.
     */
    val isBackgroundPlaybackActive: StateFlow<Boolean> = nowPlayingRepository.nowPlaying
        .map { it.isPlaying }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
            initialValue = false
        )

    val uiState: StateFlow<HomeUiState> = combine(
        resolveLastUsedResource(),
        preferencesRepository.streamsSectionEnabled,
        preferencesRepository.viewMode
    ) { lastUsedResources, streamsEnabled, viewMode ->
        HomeSources(lastUsedResources, streamsEnabled, viewMode)
    }.map { sources ->
        HomeUiState(
            lastUsedResources = sources.lastUsedResources
                // S2499: the shortcut obeys the setting that hides the Streams section itself -
                // otherwise switching the section off leaves the feature on the first screen.
                .filter { sources.streamsEnabled || it.kind == LastUsedKind.RESOURCE }
                .map(::shortcutSection),
            sections = HomeSectionCatalog.sectionsFor(
                HomeSectionVisibility(streamsEnabled = sources.streamsEnabled)
            ),
            viewMode = sources.viewMode
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
        initialValue = HomeUiState()
    )

    /**
     * S2499: the address a shortcut opens, resolved at the moment it is tapped.
     *
     * A resource answers instantly with the route it already carries. A channel has none to carry -
     * its player address holds a number that playback preparation hands out, and that preparation has
     * not run on a cold start - so it goes through the same resolver a stream tile uses, which finds
     * the channel, prepares the playback and only then answers. One path for both entrances is what
     * keeps them from becoming two answers to "open this channel".
     */
    suspend fun resolveShortcutRoute(section: HomeSection): String? {
        val route = section.route ?: section.targetRef?.let { resolveLaunchRoute(WearLaunchTarget.Open(it)) }
        if (route == null) {
            Timber.w("Home shortcut no longer resolves, staying put: %s", section.dynamicLabel)
        }
        return route
    }

    /**
     * S1836: the same route builder a tap in the sources list uses, so the shortcut and the list
     * cannot drift into two entrances to one resource.
     *
     * S2499: a channel takes the second form instead - a target reference resolved on tap.
     */
    private fun shortcutSection(resource: LastUsedResource) = when (resource.kind) {
        LastUsedKind.RESOURCE -> HomeSection(
            id = HomeSectionId.LAST_USED_RESOURCE,
            labelRes = R.string.wear_section_last_used,
            route = WearRoutes.sourceMediaType(resource.id, resource.name),
            dynamicLabel = resource.name,
            iconId = resource.iconId
        )

        LastUsedKind.STREAM -> HomeSection(
            id = HomeSectionId.LAST_USED_STREAM,
            labelRes = R.string.wear_section_last_used,
            route = null,
            dynamicLabel = resource.name,
            targetRef = WearTileTargetRef.Stream(resource.id)
        )
    }
}

/** Carries the three observed preferences into one emission so the mapping reads by name. */
private data class HomeSources(
    val lastUsedResources: List<LastUsedResource>,
    val streamsEnabled: Boolean,
    val viewMode: WearViewMode
)
