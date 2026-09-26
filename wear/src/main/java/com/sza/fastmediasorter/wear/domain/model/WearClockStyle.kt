package com.sza.fastmediasorter.wear.domain.model

/**
 * S3557: the phone launcher clock gadget's look plus its wallpaper animation, mirrored on this watch.
 *
 * The phone publishes it on every change as its own Data Item; the watch app's dim clock and backdrop
 * read it, and the clock-style complication serves it to the watch face. [DEFAULT] is exactly what
 * the watch drew before the ticket, so a watch that never heard from a phone keeps today's picture.
 *
 * @param dialColor ARGB of the time text; null means "the theme colour", which is white on the watch.
 * @param sentAt when the phone built the style. The face encoder seeds its hue roll with it, so one
 *   style always yields the same colours however often the face asks.
 */
data class WearClockStyle(
    val secondsVisible: Boolean,
    val dialColor: Int?,
    val typeface: WearClockTypeface,
    val palette: WearAnimationPalette,
    val wallpaperIntensity: Float,
    val wallpaperAnimationSpeed: Float,
    val wallpaperParticleDensity: Float,
    val sentAt: Long
) {
    companion object {
        // WAVE-PARTICLES section 3.5 (rule 15): the tuning bounds every renderer clamps to.
        const val INTENSITY_MIN = 0f
        const val INTENSITY_MAX = 1f
        const val SPEED_MIN = 0.25f
        const val SPEED_MAX = 2f
        const val DENSITY_MIN = 0f
        const val DENSITY_MAX = 1f
        const val TUNING_DEFAULT = 1f

        val DEFAULT = WearClockStyle(
            secondsVisible = false,
            dialColor = null,
            typeface = WearClockTypeface.DEFAULT,
            palette = WearAnimationPalette.DYNAMIC,
            wallpaperIntensity = TUNING_DEFAULT,
            wallpaperAnimationSpeed = TUNING_DEFAULT,
            wallpaperParticleDensity = TUNING_DEFAULT,
            sentAt = 0L
        )
    }
}

/**
 * The phone gadget's five typefaces, all drawn bold there. [wireName] is the phone's stored name and
 * the whole wire contract; [ordinal] is the index the face encoder packs, so the order is frozen.
 */
enum class WearClockTypeface(val wireName: String) {
    DEFAULT("default"),
    CONDENSED("condensed"),
    SERIF("serif"),
    MONOSPACE("monospace"),
    CASUAL("casual");

    companion object {
        fun fromWireName(name: String?): WearClockTypeface =
            entries.firstOrNull { it.wireName.equals(name, ignoreCase = true) } ?: DEFAULT
    }
}

/**
 * WAVE-PARTICLES section 3.3 palette keys. The member name is the wire value; [ordinal] is the index
 * the face encoder packs, so the order is frozen.
 */
enum class WearAnimationPalette {
    DYNAMIC,
    GREEN,
    PINK,
    BLUE;

    companion object {
        fun fromWireName(name: String?): WearAnimationPalette =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: DYNAMIC
    }
}
