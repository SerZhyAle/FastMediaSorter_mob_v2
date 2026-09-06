package com.sza.fastmediasorter.wear.domain.usecase

import android.content.Context
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.capability.WearRestrictedCapabilities
import com.sza.fastmediasorter.wear.domain.model.HomeSectionVisibility
import com.sza.fastmediasorter.wear.domain.model.WearLaunchTarget
import com.sza.fastmediasorter.wear.domain.model.WearTileContent
import com.sza.fastmediasorter.wear.domain.model.WearTileKind
import com.sza.fastmediasorter.wear.domain.model.WearTileShortcut
import com.sza.fastmediasorter.wear.domain.model.WearTileTargetRef
import com.sza.fastmediasorter.wear.domain.model.destinationFor
import com.sza.fastmediasorter.wear.domain.model.findByTargetRef
import com.sza.fastmediasorter.wear.domain.model.normalizeWearStreamUrl
import com.sza.fastmediasorter.wear.domain.repository.NetworkSourceRepository
import com.sza.fastmediasorter.wear.domain.repository.WearFavoritesRepository
import com.sza.fastmediasorter.wear.domain.repository.WearPreferencesRepository
import com.sza.fastmediasorter.wear.domain.repository.WearStreamChannelRepository
import com.sza.fastmediasorter.wear.domain.repository.WearTileAssignmentRepository
import com.sza.fastmediasorter.wear.ui.apps.WearAppCatalog
import com.sza.fastmediasorter.wear.ui.apps.WearAppIconCatalog
import com.sza.fastmediasorter.wear.ui.home.HomeSectionCatalog
import com.sza.fastmediasorter.wear.ui.home.HomeSectionIconCatalog
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject

/**
 * S1955: reads the content for a Wear OS tile from local repositories only.
 *
 * ADR-2: no network call, no Data Layer call to phone, no Wearable clients.
 */
class LoadWearTileContentUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tileAssignmentRepository: WearTileAssignmentRepository,
    private val networkSourceRepository: NetworkSourceRepository,
    private val wearStreamChannelRepository: WearStreamChannelRepository,
    private val wearFavoritesRepository: WearFavoritesRepository,
    private val preferencesRepository: WearPreferencesRepository,
    private val capabilities: WearRestrictedCapabilities
) {
    suspend operator fun invoke(kind: WearTileKind): WearTileContent = when (kind) {
        WearTileKind.RESOURCE -> loadResourceContent()
        WearTileKind.STREAM -> loadStreamContent()
        WearTileKind.FAVOURITES -> loadFavouritesContent()
        WearTileKind.PROGRAMS -> loadProgramsContent()
        WearTileKind.SECTIONS -> loadSectionsContent()
    }

    /**
     * S2511: the mini-programs, composed from the catalog the Apps screen itself draws.
     *
     * Reading that catalog rather than listing the programs again is what keeps the tile from carrying a
     * second, divergent answer to "which programs exist and in what order".
     */
    private fun loadProgramsContent(): WearTileContent {
        val apps = WearAppCatalog.apps(capabilities.offersBodySensorDiagnostics)
        Timber.d("S2511: programs tile content, %d shortcut(s)", apps.size)
        return WearTileContent.Shortcuts(
            apps.map { app ->
                WearTileShortcut(
                    iconResId = WearAppIconCatalog.iconFor(app.id),
                    contentDescription = context.getString(app.labelRes),
                    launchTarget = WearLaunchTarget.Destination(destinationFor(app.id))
                )
            }
        )
    }

    /**
     * S2511: the home sections, composed from the catalog the home screen itself draws.
     *
     * The Streams row is a runtime user preference rather than a build flag, so the real visibility is read
     * here: a tile built from a hardcoded `true` would offer a section the owner switched off.
     */
    private suspend fun loadSectionsContent(): WearTileContent {
        val streamsEnabled = preferencesRepository.streamsSectionEnabled.first()
        Timber.d("S2511: sections tile content, streamsEnabled=%s", streamsEnabled)
        val visibility = HomeSectionVisibility(streamsEnabled = streamsEnabled)
        return WearTileContent.Shortcuts(
            HomeSectionCatalog.sectionsFor(visibility).mapNotNull { section ->
                destinationFor(section.id)?.let { destination ->
                    WearTileShortcut(
                        iconResId = HomeSectionIconCatalog.iconFor(section.id),
                        contentDescription = context.getString(section.labelRes),
                        launchTarget = WearLaunchTarget.Destination(destination)
                    )
                }
            }
        )
    }

    private suspend fun loadResourceContent(): WearTileContent {
        val assignment = tileAssignmentRepository.assignmentFor(WearTileKind.RESOURCE)
            as? WearTileTargetRef.Resource

        return when {
            assignment == null -> WearTileContent.Unassigned(WearTileKind.RESOURCE)
            else -> {
                val sources = networkSourceRepository.getAllSources()
                val source = sources.findByTargetRef(assignment)
                if (source == null) {
                    WearTileContent.TargetMissing(WearTileKind.RESOURCE)
                } else {
                    WearTileContent.Assigned(
                        title = source.name,
                        subtitle = source.server,
                        iconResId = null,
                        launchTarget = WearLaunchTarget.Open(assignment)
                    )
                }
            }
        }
    }

    private suspend fun loadStreamContent(): WearTileContent {
        val assignment = tileAssignmentRepository.assignmentFor(WearTileKind.STREAM)
            as? WearTileTargetRef.Stream

        return when {
            assignment == null -> WearTileContent.Unassigned(WearTileKind.STREAM)
            else -> {
                val channels = wearStreamChannelRepository.getAllChannels()
                val channel = channels.firstOrNull { channel ->
                    normalizeWearStreamUrl(channel.url) == assignment.normalizedUrl
                }
                if (channel == null) {
                    WearTileContent.TargetMissing(WearTileKind.STREAM)
                } else {
                    WearTileContent.Assigned(
                        title = channel.name,
                        subtitle = channel.url,
                        iconResId = null,
                        launchTarget = WearLaunchTarget.Open(assignment)
                    )
                }
            }
        }
    }

    private suspend fun loadFavouritesContent(): WearTileContent {
        val favorites = wearFavoritesRepository.getFavorites()
        return if (favorites.isEmpty()) {
            WearTileContent.FavouritesEmpty
        } else {
            val entries = favorites.map { record ->
                record.displayName
            }
            WearTileContent.Assigned(
                // S2511: was an English literal, so the tile read the same on every locale.
                title = context.getString(R.string.wear_tile_favourites_label),
                subtitle = null,
                iconResId = null,
                launchTarget = WearLaunchTarget.Open(WearTileTargetRef.Favourites),
                entries = entries
            )
        }
    }
}
