package com.sza.fastmediasorter.wear.data.network.sftp

import android.net.Uri
import android.webkit.MimeTypeMap
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.sza.fastmediasorter.wear.domain.model.NetworkSource
import com.sza.fastmediasorter.wear.domain.model.WearMediaFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Vector
import javax.inject.Inject

class SftpDataSource @Inject constructor() {

    suspend fun listDirectory(source: NetworkSource, path: String): List<WearMediaFile> =
        withContext(Dispatchers.IO) {
            val jsch = JSch()
            var session: Session? = null
            var channel: ChannelSftp? = null
            try {
                if (!source.sshPrivateKey.isNullOrBlank()) {
                    jsch.addIdentity(
                        "wear_identity",
                        source.sshPrivateKey.toByteArray(),
                        null,
                        null
                    )
                }
                session = jsch.getSession(source.username, source.server, source.port)
                session.setPassword(source.password)
                session.setConfig("StrictHostKeyChecking", "no")
                session.connect(30_000)

                channel = session.openChannel("sftp") as ChannelSftp
                channel.connect()

                @Suppress("UNCHECKED_CAST")
                val entries = channel.ls(path) as? Vector<*>
                    ?: error("SFTP ls returned null for path=$path")

                entries.filterIsInstance<ChannelSftp.LsEntry>()
                    .filterNot { it.filename == "." || it.filename == ".." }
                    .mapIndexed { index, entry ->
                        val filePath = "${path.trimEnd('/')}/${entry.filename}"
                        WearMediaFile(
                            id = index.toLong(),
                            name = entry.filename,
                            uri = Uri.parse("sftp://${source.server}:${source.port}$filePath"),
                            mimeType = inferMimeType(entry.filename),
                            size = entry.attrs.size,
                            dateModified = entry.attrs.mTime.toLong() * 1000L
                        )
                    }
            } finally {
                runCatching { channel?.disconnect() }
                runCatching { session?.disconnect() }
            }
        }

    private fun inferMimeType(fileName: String): String? {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        if (ext.isEmpty()) return null
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
    }
}
