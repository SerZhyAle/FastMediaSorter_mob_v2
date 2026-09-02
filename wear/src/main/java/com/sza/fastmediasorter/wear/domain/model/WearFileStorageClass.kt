package com.sza.fastmediasorter.wear.domain.model

/**
 * Which storage a browsed watch file actually lives in.
 *
 * The browse list mixes all three, and the operations a file may be offered are a function of this
 * class rather than a flat list: two of the four operations are impossible on one of them without a
 * system consent dialog per item (S1863 strategic ADR-2).
 */
enum class WearFileStorageClass {

    /** A plain file under one of the app's own directories - no permission, no consent. */
    APP_OWNED,

    /**
     * An app-owned file the paired phone sent for viewing, so the phone still holds the original.
     *
     * Apart from [APP_OWNED] only because of what the phone still has: everything the watch may do to
     * its own file it may do to this one, and one thing more - ask the phone to open the original
     * (S2004 strategic §5.1 pillar 4). A class the watch cannot tell apart cannot carry that offer,
     * and offering it over a file the phone never had is the refusal §11 criterion 7 forbids.
     */
    PHONE_COPY,

    /** A MediaStore content URI row this app did not necessarily create, with no filesystem path. */
    MEDIA_STORE,

    /** An entry listed over SMB, FTP or SFTP, identified by its list position rather than an identity. */
    NETWORK
}
