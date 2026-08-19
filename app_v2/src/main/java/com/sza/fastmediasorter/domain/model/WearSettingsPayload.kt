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
    @SerializedName("downloadAlbumArt") val downloadAlbumArt: Boolean,
    // S1781: nullable with a null default, unlike the seven fields above - a phone that predates
    // these keys omits them, and only a nullable field lets the watch tell "not sent" from "sent
    // as List/false" and leave its own stored value alone.
    @SerializedName("viewMode") val viewMode: String? = null,
    @SerializedName("keepScreenAwakeOutsidePlayers") val keepScreenAwakeOutsidePlayers: Boolean? = null,
    // S1730: the file list keeps its own view, separate from the navigation screens above.
    @SerializedName("fileListViewMode") val fileListViewMode: String? = null,
    // S1814: active interface language of the phone, nullable so older phones do not clear watch locale.
    @SerializedName("appLanguage") val appLanguage: String? = null
)
