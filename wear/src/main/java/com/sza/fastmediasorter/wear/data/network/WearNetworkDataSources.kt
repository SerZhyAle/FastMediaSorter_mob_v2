package com.sza.fastmediasorter.wear.data.network

import com.sza.fastmediasorter.wear.data.network.ftp.FtpDataSource
import com.sza.fastmediasorter.wear.data.network.sftp.SftpDataSource
import com.sza.fastmediasorter.wear.data.network.smb.SmbDataSource
import javax.inject.Inject

/**
 * The watch's three read-only network sources, injected as one collaborator.
 *
 * A caller reaches exactly one of them after branching on the source type, so they are never useful
 * apart; taken separately they spend three constructor slots at every call site that browses.
 */
class WearNetworkDataSources @Inject constructor(
    val smb: SmbDataSource,
    val ftp: FtpDataSource,
    val sftp: SftpDataSource
)
