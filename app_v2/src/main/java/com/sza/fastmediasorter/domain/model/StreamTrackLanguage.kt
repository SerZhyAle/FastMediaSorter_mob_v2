package com.sza.fastmediasorter.domain.model

/**
 * S1144: global default audio / subtitle language for stream playback. [DEFAULT] means "follow the app
 * language", mirroring `PlayerSettingsDialog.LanguageOption` so the two surfaces name the same thing the
 * same way. A per-channel preference (ADR-4) overrides this for its own channel only.
 */
enum class StreamTrackLanguage {
    DEFAULT,
    ENGLISH,
    RUSSIAN,
    UKRAINIAN;

    /** ISO code applied to the track selector, or null for [DEFAULT] (caller substitutes the app language). */
    fun isoCodeOrNull(): String? = when (this) {
        DEFAULT -> null
        ENGLISH -> "en"
        RUSSIAN -> "ru"
        UKRAINIAN -> "uk"
    }

    companion object {
        fun fromName(name: String?): StreamTrackLanguage =
            values().firstOrNull { it.name == name } ?: DEFAULT

        /** Maps a stored ISO code (per-channel preference) back onto the enum for picker display. */
        fun fromIsoCode(code: String?): StreamTrackLanguage = when (code?.lowercase()) {
            "en" -> ENGLISH
            "ru" -> RUSSIAN
            "uk" -> UKRAINIAN
            else -> DEFAULT
        }
    }
}
