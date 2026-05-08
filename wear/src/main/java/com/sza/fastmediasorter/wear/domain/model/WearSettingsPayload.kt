package com.sza.fastmediasorter.wear.domain.model

/**
 * Payload for receiving Wear companion settings pushed from the phone.
 * Fields mirror the setters in WearPreferencesRepository.
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
