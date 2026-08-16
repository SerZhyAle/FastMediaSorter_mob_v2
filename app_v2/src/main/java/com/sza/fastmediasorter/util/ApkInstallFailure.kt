package com.sza.fastmediasorter.util

/**
 * S1686: what the package installer actually refused on.
 *
 * The installer answers every refusal with `RESULT_FIRST_USER`, which names no cause; the reason travels
 * beside it in the result's `INSTALL_RESULT` extra as a legacy status. Classifying that number here - away
 * from the screen that shows it - is what makes the decision checkable without a device, which is the whole
 * point of the ticket: the original cause was found only by inspecting the APK and the device by hand.
 */
enum class ApkInstallFailure {
    /** A newer version of the same package is already installed. */
    VERSION_DOWNGRADE,

    /** The installed app was signed with a different key, or the update is otherwise incompatible. */
    UPDATE_INCOMPATIBLE,

    /** Not enough free space to install. */
    INSUFFICIENT_STORAGE,

    /** The file is damaged or is not a readable package. */
    CORRUPT_PACKAGE,

    /** The installer reported something else, or reported nothing at all. */
    UNKNOWN;

    companion object {
        /**
         * Name of the installer's result extra. `Intent.EXTRA_INSTALL_RESULT` is hidden from the public
         * SDK, so the constant cannot be referenced; this is the name the platform has always used for the
         * result of `ACTION_INSTALL_PACKAGE`, and an absent extra simply classifies as [UNKNOWN].
         */
        const val EXTRA_INSTALL_RESULT: String = "android.intent.extra.INSTALL_RESULT"

        /** Value to read the extra with when it may be absent. */
        const val NO_STATUS: Int = Int.MIN_VALUE

        private const val INSUFFICIENT_STORAGE_STATUS = -4
        private const val UPDATE_INCOMPATIBLE_STATUS = -7
        private const val VERSION_DOWNGRADE_STATUS = -25
        private const val PARSE_FAILED_RANGE_END = -100
        private const val PARSE_FAILED_RANGE_START = -108

        /** Classify a legacy installer status. Anything unrecognised or absent is [UNKNOWN]. */
        fun fromLegacyStatus(legacyStatus: Int): ApkInstallFailure = when {
            legacyStatus == VERSION_DOWNGRADE_STATUS -> VERSION_DOWNGRADE
            legacyStatus == UPDATE_INCOMPATIBLE_STATUS -> UPDATE_INCOMPATIBLE
            legacyStatus == INSUFFICIENT_STORAGE_STATUS -> INSUFFICIENT_STORAGE
            legacyStatus in PARSE_FAILED_RANGE_START..PARSE_FAILED_RANGE_END -> CORRUPT_PACKAGE
            else -> UNKNOWN
        }
    }
}
