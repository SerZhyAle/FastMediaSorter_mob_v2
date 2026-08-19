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
    val downloadAlbumArt: Boolean,
    // S1781: nullable with a null default - an older phone omits these keys, and only a nullable
    // field lets the applying side leave the watch's own stored value untouched.
    val viewMode: String? = null,
    val keepScreenAwakeOutsidePlayers: Boolean? = null,
    // S1730: the file list keeps its own view, separate from the navigation screens above.
    val fileListViewMode: String? = null,
    // S1814: active interface language of the phone, nullable so older phones do not clear watch locale.
    val appLanguage: String? = null
)
