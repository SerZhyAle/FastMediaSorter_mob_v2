package com.sza.fastmediasorter.wear.domain.model

/**
 * Which storage a browsed watch file actually lives in.
 *
 * The browse list mixes all three, and the operations a file may be offered are a function of this
 * class rather than a flat list: two of the four operations are impossible on one of them without a
 * system consent dialog per item (S1863 strategic ADR-2).
 */
enum class WearFileStorageClass {

    /** A plain file under the app's own external files directory - no permission, no consent. */
    APP_OWNED,

    /** A MediaStore content URI row this app did not necessarily create, with no filesystem path. */
    MEDIA_STORE,

    /** An entry listed over SMB, FTP or SFTP, identified by its list position rather than an identity. */
    NETWORK
}
