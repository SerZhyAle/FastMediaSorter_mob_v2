package com.sza.fastmediasorter.wear.domain.model

/**
 * S3383: what the credential screen is being asked to do.
 *
 * One screen for three errands because all three ask the same question of the wearer, and asking it
 * in three places would mean three chances to mask the field differently.
 */
enum class WearFdSecMode {

    /** Open a container for viewing: the recovered copy stays in the private cache. */
    OPEN,

    /** Pack the selected file into a container beside it, keeping the original. */
    ENCRYPT,

    /** Restore a container's original beside it, keeping the container. */
    DECRYPT;

    companion object {

        /** An unknown name reads as [OPEN], which is the only mode that writes nothing. */
        fun fromNameOrOpen(name: String?): WearFdSecMode =
            entries.firstOrNull { it.name == name } ?: OPEN
    }
}
