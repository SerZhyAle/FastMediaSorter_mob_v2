package com.sza.fastmediasorter.data.remote.sftp.server

import android.content.Context
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import androidx.annotation.RequiresApi
import androidx.documentfile.provider.DocumentFile
import java.io.FileNotFoundException
import java.nio.channels.SeekableByteChannel

/**
 * One file or directory the embedded SFTP server can serve. The server's file system is written
 * against this seam rather than against [DocumentFile] directly, so the whole SFTP stack - path
 * mapping, the NIO provider, MINA's subsystem - runs in a plain JVM test over a real directory.
 */
interface SftpServerNode {
    val name: String
    val isDirectory: Boolean
    val length: Long
    val lastModified: Long
    val canWrite: Boolean

    fun listChildren(): List<SftpServerNode>
    fun findChild(name: String): SftpServerNode?
    fun createDirectory(name: String): SftpServerNode?
    fun createFile(name: String): SftpServerNode?
    fun delete(): Boolean

    /** Renames in place; returns the node under its new name, or null when the provider refused. */
    fun renameTo(newName: String): SftpServerNode?

    /** Moves under [newParent] keeping the name; returns the moved node, or null when refused. */
    fun moveTo(newParent: SftpServerNode): SftpServerNode?

    fun openChannel(mode: ChannelMode): SeekableByteChannel

    enum class ChannelMode(val safMode: String) {
        READ("r"),
        WRITE("rw"),
        TRUNCATE("rwt"),
        APPEND("wa"),
    }
}

/**
 * [SftpServerNode] over a SAF tree document.
 *
 * Channels come from `openFileDescriptor`, not from `openInputStream`: for the local storage provider
 * the descriptor is a real file, so the resulting [java.nio.channels.FileChannel] seeks both ways and
 * SFTP clients that read or resume at an offset work. A provider that hands back a pipe instead makes
 * a seek fail with an I/O error, which the client reports as a failed transfer.
 */
@RequiresApi(Build.VERSION_CODES.O)
class DocumentFileServerNode(
    private val context: Context,
    private val document: DocumentFile,
) : SftpServerNode {

    override val name: String get() = document.name.orEmpty()
    override val isDirectory: Boolean get() = document.isDirectory
    override val length: Long get() = document.length()
    override val lastModified: Long get() = document.lastModified()
    override val canWrite: Boolean get() = document.canWrite()

    override fun listChildren(): List<SftpServerNode> = document.listFiles().map(::wrap)

    override fun findChild(name: String): SftpServerNode? = document.findFile(name)?.let(::wrap)

    override fun createDirectory(name: String): SftpServerNode? = document.createDirectory(name)?.let(::wrap)

    // octet-stream carries no extension mapping, so the provider keeps the client's file name verbatim.
    override fun createFile(name: String): SftpServerNode? =
        document.createFile(BINARY_MIME_TYPE, name)?.let(::wrap)

    override fun delete(): Boolean = document.delete()

    override fun renameTo(newName: String): SftpServerNode? = if (document.renameTo(newName)) this else null

    override fun moveTo(newParent: SftpServerNode): SftpServerNode? {
        val sourceParent = document.parentFile
        val targetParent = (newParent as? DocumentFileServerNode)?.document
        if (sourceParent == null || targetParent == null) return null
        val movedUri = DocumentsContract.moveDocument(
            context.contentResolver,
            document.uri,
            sourceParent.uri,
            targetParent.uri,
        )
        return movedUri?.let { DocumentFile.fromTreeUri(context, it) }?.let(::wrap)
    }

    override fun openChannel(mode: SftpServerNode.ChannelMode): SeekableByteChannel {
        val descriptor = context.contentResolver.openFileDescriptor(document.uri, mode.safMode)
            ?: throw FileNotFoundException("No descriptor for ${document.uri}")
        // The Auto-close streams tie the descriptor's life to the channel: closing the channel closes it.
        return if (mode == SftpServerNode.ChannelMode.READ) {
            ParcelFileDescriptor.AutoCloseInputStream(descriptor).channel
        } else {
            ParcelFileDescriptor.AutoCloseOutputStream(descriptor).channel
        }
    }

    private fun wrap(child: DocumentFile): SftpServerNode = DocumentFileServerNode(context, child)

    private companion object {
        const val BINARY_MIME_TYPE = "application/octet-stream"
    }
}
