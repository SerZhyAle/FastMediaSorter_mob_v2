package com.sza.fastmediasorter.wear.domain.model

/**
 * S1862: who decides that a finished note leaves the watch.
 *
 * Both models exist behind one setting (section 6 item 1) because the automatic one is what a watch
 * is for - speak and forget - while the manual one is the only way to keep a note off the phone at
 * all. A setting that cannot stop the automatic path would not be a setting.
 */
enum class VoiceNoteSendPolicy {

    /** The note is queued for the phone the moment the recording closes. */
    AUTOMATIC,

    /** The note stays on the watch until the user asks for it by hand. */
    MANUAL;

    companion object {

        /**
         * Stored by name rather than by ordinal: an ordinal silently re-points every stored value
         * the day a third policy is inserted, and an unknown name must read as the shipped default
         * rather than throw on a downgraded install.
         */
        fun fromNameOrDefault(name: String?): VoiceNoteSendPolicy =
            entries.firstOrNull { it.name == name } ?: AUTOMATIC
    }
}
