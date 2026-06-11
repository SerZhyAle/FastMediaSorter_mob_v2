package com.sza.fastmediasorter.util

import android.os.Environment
import com.sza.fastmediasorter.data.transfer.local.LocalDestinationCategory
import com.sza.fastmediasorter.domain.model.MediaResource

class ScreenshotDestinationPolicy {

    sealed interface Target {
        data class SelectedResource(val resource: MediaResource) : Target

        data class PublicCollection(
            val collection: LocalDestinationCategory.PublicCollection.Kind,
            val relativePath: String
        ) : Target
    }

    fun resolve(
        selectedResourceId: String?,
        resources: List<MediaResource>,
        availableScreenshotRelativePaths: Set<String> = DEFAULT_SCREENSHOT_RELATIVE_PATHS
    ): Target {
        val selectedResource = resources.firstOrNull { it.id.toString() == selectedResourceId }
        if (selectedResource != null) {
            return Target.SelectedResource(selectedResource)
        }

        val screenshotRelativePath = SCREENSHOT_PATH_PRIORITY.firstOrNull {
            availableScreenshotRelativePaths.contains(it)
        }
        if (screenshotRelativePath != null) {
            return Target.PublicCollection(
                collection = LocalDestinationCategory.PublicCollection.Kind.IMAGES,
                relativePath = screenshotRelativePath
            )
        }

        return Target.PublicCollection(
            collection = LocalDestinationCategory.PublicCollection.Kind.DOWNLOADS,
            relativePath = DOWNLOADS_RELATIVE_PATH
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
