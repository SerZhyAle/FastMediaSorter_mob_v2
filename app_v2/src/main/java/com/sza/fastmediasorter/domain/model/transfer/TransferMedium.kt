package com.sza.fastmediasorter.domain.model.transfer

/**
 * Where a [TransferDataKind] is written to or read from (S1565).
 *
 * Deliberately fieldless: the user-visible names and content descriptions are string resources
 * resolved in the UI layer, so a further medium can be added without touching the data rules.
 */
enum class TransferMedium {
    DEVICE_FILE,
    GOOGLE_DRIVE
}
