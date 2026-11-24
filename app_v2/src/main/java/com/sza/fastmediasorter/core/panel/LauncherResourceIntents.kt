package com.sza.fastmediasorter.core.panel

import android.content.Context
import android.content.Intent
import com.sza.fastmediasorter.ui.browse.BrowseActivity
import com.sza.fastmediasorter.ui.browse.helpers.BrowseExitManager
import com.sza.fastmediasorter.ui.player.PlayerActivity

/**
 * The screens a launcher cell opens for a resource or a favourite file, built here so the launcher use case
 * answers with an Intent and never names a screen class. The caller applies its own task flags.
 */
object LauncherResourceIntents {

    /** Browse marked as opened from the launcher, so leaving it returns there and not to the main menu. */
    fun browse(context: Context, resourceId: Long): Intent =
        BrowseExitManager.fromLauncher(BrowseActivity.createIntent(context, resourceId))

    fun player(
        context: Context,
        resourceId: Long,
        isSlideshowEnabled: Boolean = false,
        skipAvailabilityCheck: Boolean = false,
        initialFilePath: String? = null,
    ): Intent = PlayerActivity.createPanelIntent(
        context = context,
        resourceId = resourceId,
        skipAvailabilityCheck = skipAvailabilityCheck,
        initialFilePath = initialFilePath,
        isSlideshowEnabled = isSlideshowEnabled,
    )
}
