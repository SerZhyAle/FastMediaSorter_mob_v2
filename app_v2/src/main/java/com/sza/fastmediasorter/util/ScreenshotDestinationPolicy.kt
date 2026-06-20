package com.sza.fastmediasorter.util

import android.os.Environment
import com.sza.fastmediasorter.data.transfer.local.LocalDestinationCategory
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.SaveFallbackReason

class ScreenshotDestinationPolicy {

    sealed interface Target {
        data class SelectedResource(val resource: MediaResource) : Target

        data class PublicCollection(
            val collection: LocalDestinationCategory.PublicCollection.Kind,
            val relativePath: String,
            // Non-null only when the selected network resource was unreachable and we redirected
            // here; drives the user-facing "saved locally because X is unavailable" notice.
            val fallbackReason: SaveFallbackReason? = null
        ) : Target
    }

    fun resolve(
        selectedResourceId: String?,
        resources: List<MediaResource>,
        availableScreenshotRelativePaths: Set<String> = DEFAULT_SCREENSHOT_RELATIVE_PATHS,
        isResourceReachable: (MediaResource) -> Boolean = { true }
    ): Target {
        val selectedResource = resources.firstOrNull { it.id.toString() == selectedResourceId }
        if (selectedResource != null) {
            val unreachableNetwork =
                selectedResource.type.isNetworkResource && !isResourceReachable(selectedResource)
            if (!unreachableNetwork) {
                return Target.SelectedResource(selectedResource)
            }
            return publicScreenshotTarget(availableScreenshotRelativePaths, SaveFallbackReason.ResourceUnavailable)
        }

        return publicScreenshotTarget(availableScreenshotRelativePaths, fallbackReason = null)
    }

    private fun publicScreenshotTarget(
        availableScreenshotRelativePaths: Set<String>,
        fallbackReason: SaveFallbackReason?
    ): Target.PublicCollection {
        val screenshotRelativePath = SCREENSHOT_PATH_PRIORITY.firstOrNull {
            availableScreenshotRelativePaths.contains(it)
        }
        if (screenshotRelativePath != null) {
            return Target.PublicCollection(
                collection = LocalDestinationCategory.PublicCollection.Kind.IMAGES,
                relativePath = screenshotRelativePath,
                fallbackReason = fallbackReason
            )
        }

        return Target.PublicCollection(
            collection = LocalDestinationCategory.PublicCollection.Kind.DOWNLOADS,
            relativePath = DOWNLOADS_RELATIVE_PATH,
            fallbackReason = fallbackReason
        )
    }

    companion object {
        val PICTURES_SCREENSHOTS_RELATIVE_PATH =
            "${Environment.DIRECTORY_PICTURES}/Screenshots/"
        val DCIM_SCREENSHOTS_RELATIVE_PATH =
            "${Environment.DIRECTORY_DCIM}/Screenshots/"
        val DOWNLOADS_RELATIVE_PATH =
            "${Environment.DIRECTORY_DOWNLOADS}/"

        val DEFAULT_SCREENSHOT_RELATIVE_PATHS: Set<String> = linkedSetOf(
            PICTURES_SCREENSHOTS_RELATIVE_PATH,
            DCIM_SCREENSHOTS_RELATIVE_PATH
        )

        private val SCREENSHOT_PATH_PRIORITY = listOf(
            PICTURES_SCREENSHOTS_RELATIVE_PATH,
            DCIM_SCREENSHOTS_RELATIVE_PATH
        )
    }
}
