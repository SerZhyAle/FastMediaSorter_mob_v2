package com.sza.fastmediasorter.domain.model

/**
 * Payload for pushing Wear companion settings from phone to watch.
 * Fields mirror the setters in WearPreferencesRepository on the watch side.
 */
data class WearSettingsPayload(
    val audioEnabled: Boolean,
    val videoEnabled: Boolean,
    val imagesEnabled: Boolean,
    val slideshowEnabled: Boolean,
    val slideshowIntervalSeconds: Int,
    val slideshowWaitForFinish: Boolean,
    val downloadAlbumArt: Boolean
)
