package com.sza.fastmediasorter.domain.model

import com.google.gson.annotations.SerializedName

/**
 * Payload for pushing Wear companion settings from phone to watch.
 * Fields mirror the setters in WearPreferencesRepository on the watch side.
 *
 * S1631: keys pinned - the watch reads this contract by its real field names.
 */
data class WearSettingsPayload(
    @SerializedName("audioEnabled") val audioEnabled: Boolean,
    @SerializedName("videoEnabled") val videoEnabled: Boolean,
    @SerializedName("imagesEnabled") val imagesEnabled: Boolean,
    @SerializedName("slideshowEnabled") val slideshowEnabled: Boolean,
    @SerializedName("slideshowIntervalSeconds") val slideshowIntervalSeconds: Int,
    @SerializedName("slideshowWaitForFinish") val slideshowWaitForFinish: Boolean,
    @SerializedName("downloadAlbumArt") val downloadAlbumArt: Boolean
)
