package com.sza.fastmediasorter.domain.model.sos

/**
 * S3216: which halves of the distress signal are engaged.
 *
 * Three named modes rather than two booleans (ADR-4): the value travels over the Wearable Data Layer as
 * a single token, and a pair of flags would admit the fourth combination - neither sound nor light -
 * which is an SOS that does nothing.
 *
 * The watch module declares its own copy of this enum. The two share no source, so the MEMBER NAMES are
 * the entire wire contract: the payload carries [name] and the receiving side resolves it through
 * [fromNameOrDefault], which falls back to [ALL] rather than dropping the command.
 */
enum class SosMode {

    /** Siren and strobe together - the owner's default. */
    ALL,

    /** The siren alone, screen and torch left dark to save the battery. */
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
