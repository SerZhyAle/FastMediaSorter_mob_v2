package com.sza.fastmediasorter.wear.domain.model

/**
 * S4228: one `sftp-share` resource of the watch's own account as the directory lists it (contract DEVICE-EXCHANGE
 * 6.2, 23 item CF), reduced to what attaching it needs. [code] carries the share's password, so nothing prints it.
 */
data class WearAccountFolder(
    val resourceId: String,
    val name: String,
    val code: WearSftpPairingCode,
    /** `access.root`: the virtual root the folder opens at, without its slashes; empty for the whole share. */
    val root: String,
    /** The root asks for its original PIN (17 item AG), which only the phone can hand over. */
    val pinProtected: Boolean,
) {

    override fun toString(): String = "WearAccountFolder(protected=$pinProtected)"
}

/** S4228: one row of the account's folder list - the folder, and whether this watch already holds it. */
data class WearAccountFolderRow(val folder: WearAccountFolder, val isOnWatch: Boolean)
