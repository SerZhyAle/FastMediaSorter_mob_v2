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
    @SerializedName("backgroundMode") val backgroundMode: String? = null,
    // S2093: the watch row that had no phone control until this ticket.
    @SerializedName("streamsSectionEnabled") val streamsSectionEnabled: Boolean? = null,
    // S2130: the fourth allowed-type switch. Nullable unlike its three siblings above for the S1781
    // reason - a phone that predates it omits it, and only a nullable field lets the watch keep its
    // own stored value instead of reading the absence as "documents switched off".
    @SerializedName("documentsEnabled") val documentsEnabled: Boolean? = null,
    // S2209: disable animations toggle synced between phone and watch.
    @SerializedName("disableAnimations") val disableAnimations: Boolean? = null,
    // S2093: contract field name to epoch-millis of that field's last edit on the sending side. One map
    // rather than a companion field per setting, so a later registry entry needs no new contract field
    // and no new storage key. Absent entirely on a side that predates the two-way exchange, which the
    // merge reads as "apply the incoming value", preserving today's one-way behaviour.
    @SerializedName("fieldTimestamps") val fieldTimestamps: Map<String, Long>? = null,
    // S2093: device traits the other side cannot infer, keyed by
    // WearSettingsRegistry.CAPABILITY_AUTO_ROTATION_SENSOR and its future peers.
    @SerializedName("capabilities") val capabilities: Map<String, Boolean>? = null
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
