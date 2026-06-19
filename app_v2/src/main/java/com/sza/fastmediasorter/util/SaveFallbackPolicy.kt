package com.sza.fastmediasorter.util

import android.os.Environment
import com.sza.fastmediasorter.data.transfer.local.LocalDestinationCategory
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.SaveFallbackReason

/**
 * Single decision point for "save to the chosen resource or to a local default folder", shared by
 * every save flow. Pure helper - no Android Context, no DI (mirrors [CaptureDestinationPolicy]).
 *
 * The reachability of a network resource is supplied by the caller (from
 * [com.sza.fastmediasorter.core.network.NetworkStateMonitor.canReach]) so this object stays pure
 * and unit-testable.
 */
object SaveFallbackPolicy {

    sealed interface Decision {
        data class UseResource(val resource: MediaResource) : Decision

        data class UseLocalFallback(
            val collection: LocalDestinationCategory.PublicCollection.Kind,
            val relativePath: String,
            val reason: SaveFallbackReason,
        ) : Decision
    }

    /**
     * @param resource the user-selected destination, or null when none is configured.
     * @param mediaType drives which local public collection the fallback targets.
     * @param isResourceReachable result of a reachability probe for [resource]; ignored for a
     *   local resource (always usable) and for the null case.
     */
    fun decide(
        resource: MediaResource?,
        mediaType: MediaType,
        isResourceReachable: Boolean,
    ): Decision {
        if (resource == null) {
            return localFallback(mediaType, SaveFallbackReason.NoResourceConfigured)
        }
        if (resource.type.isNetworkResource && !isResourceReachable) {
            return localFallback(mediaType, SaveFallbackReason.ResourceUnavailable)
        }
        return Decision.UseResource(resource)
    }

    /** Maps a media type to its default local public collection and relative path. */
    fun fallbackCollection(
        mediaType: MediaType,
    ): Pair<LocalDestinationCategory.PublicCollection.Kind, String> = when (mediaType) {
        MediaType.IMAGE, MediaType.GIF ->
            LocalDestinationCategory.PublicCollection.Kind.IMAGES to "${Environment.DIRECTORY_PICTURES}/"
        MediaType.VIDEO ->
            LocalDestinationCategory.PublicCollection.Kind.VIDEO to "${Environment.DIRECTORY_MOVIES}/"
        MediaType.AUDIO ->
            LocalDestinationCategory.PublicCollection.Kind.AUDIO to "${Environment.DIRECTORY_MUSIC}/"
        else ->
            LocalDestinationCategory.PublicCollection.Kind.DOWNLOADS to "${Environment.DIRECTORY_DOWNLOADS}/"
    }

    private fun localFallback(mediaType: MediaType, reason: SaveFallbackReason): Decision.UseLocalFallback {
        val (collection, relativePath) = fallbackCollection(mediaType)
        return Decision.UseLocalFallback(collection, relativePath, reason)
    }
}
