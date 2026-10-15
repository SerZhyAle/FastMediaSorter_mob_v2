package com.sza.fastmediasorter.data.remote.sftp.server

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.documentfile.provider.DocumentFile

/**
 * The SAF trees the embedded server serves, as nodes. Shared by the live server and the Drive channel
 * responder (S4231) so both paths resolve exactly the same roots.
 */
internal object SftpServedRoots {

    /** A picked folder whose access grant is gone no longer resolves to a directory and is skipped. */
    @RequiresApi(Build.VERSION_CODES.O)
    fun resolve(context: Context, uris: List<String>): List<Pair<String, SftpServerNode>> = uris
        .mapNotNull { uri -> DocumentFile.fromTreeUri(context, Uri.parse(uri))?.let { uri to it } }
        .filter { (_, tree) -> tree.isDirectory }
        .map { (uri, tree) -> uri to DocumentFileServerNode(context, tree) }
}
