package com.sza.fastmediasorter.data.remote.sftp

class SftpDownloadExhaustedException(
    remotePath: String,
    cause: Throwable? = null
) : Exception("SFTP download exhausted all retries: $remotePath", cause)
