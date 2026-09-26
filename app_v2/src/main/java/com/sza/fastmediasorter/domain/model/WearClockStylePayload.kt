package com.sza.fastmediasorter.domain.model

import com.google.gson.annotations.SerializedName

/**
 * S3557: the phone clock's look, sent to the watch face so it mirrors the launcher dial and wallpaper.
 *
 * Keys are pinned because the phone ships minified while the watch keeps its own unobfuscated copy of
 * this contract (S1631); the watch declaration must stay a mirror of this one. Every field is nullable
 * with a null default so an older or newer peer that omits a key decodes to "keep your own value"
 * instead of a Gson-zeroed primitive.
 */
data class WearClockStylePayload(
    @SerializedName("secondsVisible") val secondsVisible: Boolean? = null,
    // ARGB; null means the dial follows the theme colour rather than a picked one.
    @SerializedName("dialColor") val dialColor: Int? = null,
    @SerializedName("dialTypeface") val dialTypeface: String? = null,
    @SerializedName("animationPalette") val animationPalette: String? = null,
    @SerializedName("wallpaperIntensity") val wallpaperIntensity: Float? = null,
    @SerializedName("wallpaperAnimationSpeed") val wallpaperAnimationSpeed: Float? = null,
    @SerializedName("wallpaperParticleDensity") val wallpaperParticleDensity: Float? = null,
    // S3707: the watch background mode matching the launcher wallpaper, one of the
    // WearSettingsPayload.BACKGROUND_MODE_* names; the watch applies it only under FOLLOW_PHONE.
    @SerializedName("launcherBackdrop") val launcherBackdrop: String? = null,
    @SerializedName("sentAt") val sentAt: Long? = null,
)
