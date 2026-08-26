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
    @SerializedName("downloadAlbumArt") val downloadAlbumArt: Boolean,
    // S1781: nullable with a null default, unlike the six fields above - a phone that predates
    // these keys omits them, and only a nullable field lets the watch tell "not sent" from "sent
    // as List/false" and leave its own stored value alone.
    @SerializedName("viewMode") val viewMode: String? = null,
    @SerializedName("keepScreenAwakeOutsidePlayers") val keepScreenAwakeOutsidePlayers: Boolean? = null,
    // S1730: the file list keeps its own view, separate from the navigation screens above.
    @SerializedName("fileListViewMode") val fileListViewMode: String? = null,
    // S1814: active interface language of the phone, nullable so older phones do not clear watch locale.
    @SerializedName("appLanguage") val appLanguage: String? = null,
    // S2000: name of the watch's WearBackgroundMode. Only the choice rides here - the picture itself
    // goes over the file-transfer channel, because this payload is Gson-encoded and a ByteArray would
    // serialize as an array of numbers, pushing the data item past the size where it is dropped in
    // silence and reads on the watch as "phone out of reach".
    @SerializedName("backgroundMode") val backgroundMode: String? = null
) {
    /**
     * S2000: the watch's `WearBackgroundMode` entries, mirrored as strings.
     *
     * This module cannot see the wear module's enum - the two compile separately with no shared
     * artifact - and the contract carries the name rather than an ordinal, so the names are pinned
     * once on each side and an unknown one resolves back to the animation on the watch.
     */
    companion object {
        const val BACKGROUND_MODE_BRANDED_ANIMATION = "BRANDED_ANIMATION"
        const val BACKGROUND_MODE_IMAGE = "IMAGE"
    }
}
