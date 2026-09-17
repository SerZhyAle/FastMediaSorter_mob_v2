package com.sza.fastmediasorter.wear.domain.model

/**
 * S3216: which halves of the distress signal are engaged - watch-side mirror.
 *
 * Member names are identical to the phone module's `domain.model.sos.SosMode` and that name match is the
 * entire contract between them: the two modules share no source, the Data Layer payload carries [name],
 * and [fromNameOrDefault] resolves an unknown token to [ALL] rather than dropping the command.
 */
enum class SosMode {

    /** Siren and strobe together - the owner's default. */
    ALL,

    /** The siren alone, the display left dark to save the battery. */
    SOUND_ONLY,

    /** The strobe alone, for a situation where sound would draw the wrong attention. */
    LIGHT_ONLY;

    val engagesSound: Boolean get() = this != LIGHT_ONLY

    val engagesLight: Boolean get() = this != SOUND_ONLY

    companion object {

        /** The mode [name] stands for, or [ALL] when the token is absent or unknown to this build. */
        fun fromNameOrDefault(name: String?): SosMode =
            entries.firstOrNull { it.name == name } ?: ALL
    }
}
