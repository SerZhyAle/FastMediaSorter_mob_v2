package com.sza.fastmediasorter.ui.settings.search

import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.ui.settings.SettingsSearchDestination

/**
 * Static mapping from a settings-fragment layout resource id to the search tab
 * and section it belongs to. Drives how entries discovered in a given layout file
 * are routed when the user picks a search result.
 */
data class TabAssignment(
    val destination: SettingsSearchDestination,
    val sectionId: String
)

object SettingsSearchTabMapping {

    private val byLayout: Map<Int, TabAssignment> = mapOf(
        R.layout.fragment_settings_general to TabAssignment(
            SettingsSearchDestination.GENERAL, "general"
        ),
        R.layout.fragment_settings_playback to TabAssignment(
            SettingsSearchDestination.PLAYBACK, "playback"
        ),
        R.layout.fragment_settings_media_container to TabAssignment(
            SettingsSearchDestination.MEDIA, "media"
        ),
        R.layout.fragment_settings_images to TabAssignment(
            SettingsSearchDestination.MEDIA, "images"
        ),
        R.layout.fragment_settings_video to TabAssignment(
            SettingsSearchDestination.MEDIA, "video"
        ),
        R.layout.fragment_settings_audio to TabAssignment(
            SettingsSearchDestination.MEDIA, "audio"
        ),
        R.layout.fragment_settings_documents to TabAssignment(
            SettingsSearchDestination.MEDIA, "documents"
        ),
        R.layout.fragment_settings_other to TabAssignment(
            SettingsSearchDestination.MEDIA, "other"
        ),
        R.layout.fragment_settings_destinations to TabAssignment(
            SettingsSearchDestination.OPERATIONS, "destinations"
        )
        // No fragment_settings_backup_restore entry: that layout is excluded from the search
        // scan (see SettingsSearchLayoutCatalog). The real backup/restore rows live in
        // fragment_settings_general and are routed to the General tab via that mapping.
    )

    fun assignmentFor(layoutResId: Int): TabAssignment? = byLayout[layoutResId]
}
