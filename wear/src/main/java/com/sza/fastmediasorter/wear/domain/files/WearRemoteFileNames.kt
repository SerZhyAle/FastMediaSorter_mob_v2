package com.sza.fastmediasorter.wear.domain.files

import java.util.UUID

/**
 * S4230: the name a file wears on a resource while it is still being written.
 *
 * The transfer is stored under this name and renamed to the real one only after the last byte landed, so an
 * interrupted send never leaves a file under its real name that the person would find and fail to open. The name
 * carries no part of the person's file name: a long or unusual name cannot break the creation of the temporary file.
 */
object WearRemoteFileNames {

    private const val TOKEN_LENGTH = 8

    fun partialName(token: String): String = ".fms-$token.part"

    /** Eight lowercase hex characters, fresh per transfer, so two sends of one name never share a partial file. */
    fun newToken(): String = UUID.randomUUID().toString().replace("-", "").take(TOKEN_LENGTH)

    /** `directory/name` for an SFTP or FTP path; a trailing slash on the directory is never doubled. */
    fun joinRemote(directory: String, name: String): String = "${directory.trimEnd('/')}/$name"

    /**
     * The share-relative form smbj takes: backslashes, no leading separator, and the share root is the empty
     * string - the same form the folder listing already passes to `DiskShare.list`.
     */
    fun joinShare(directory: String, name: String): String {
        val folder = directory.trim('/', '\\').replace('/', '\\')
        return if (folder.isEmpty()) name else "$folder\\$name"
    }
}
