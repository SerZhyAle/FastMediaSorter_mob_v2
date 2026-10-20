package com.sza.fastmediasorter.core.clipboard

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The three directories of the clipboard on the exchange server. `received/` holds the one value placed in the
 * system clipboard and is the only one the FileProvider exposes; the whole of `filesDir` is not, because it also
 * holds the DataStore files. `incoming/` takes a value while it arrives, so a partial value never sits where the
 * clipboard points, and `outgoing/` in the cache stages text or a copy being sent.
 */
@Singleton
class ExchangeClipboardFiles @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    private val root: File get() = File(context.filesDir, ROOT)
    private val incomingDir: File get() = File(root, INCOMING)
    private val outgoingDir: File get() = File(File(context.cacheDir, ROOT), OUTGOING)

    /** The directory the placed value lives in; the FileProvider path `exchange_clipboard` names it. */
    val receivedDir: File get() = File(root, RECEIVED)

    /** A path for a value about to arrive; nothing is created until the first byte is written. */
    fun newIncomingFile(): File = File(incomingDir.apply { mkdirs() }, "incoming-${UUID.randomUUID()}.part")

    fun newOutgoingFile(extension: String): File =
        File(outgoingDir.apply { mkdirs() }, "outgoing-${UUID.randomUUID()}.$extension")

    /** Whether app storage can take [bytes] and still keep a margin for the app itself (strategic §7). */
    fun hasRoomFor(bytes: Long): Boolean = context.filesDir.usableSpace >= bytes + MARGIN_BYTES

    /**
     * Makes [incoming] the one received value under [name]: the previous value is deleted first, because only the
     * last value is kept (strategic pillar C).
     */
    fun promote(incoming: File, name: String): File {
        val dir = receivedDir.apply { mkdirs() }
        dir.listFiles()?.forEach(File::delete)
        val target = File(dir, ExchangeClipboardFileNames.uniqueIn(dir.list().orEmpty().toSet(), name))
        if (!incoming.renameTo(target)) {
            incoming.copyTo(target)
            incoming.delete()
        }
        return target
    }

    /** Drops what a broken or killed receive left behind; a stale part can be gigabytes. */
    fun clearIncoming() {
        incomingDir.listFiles()?.forEach(File::delete)
    }

    private companion object {
        const val ROOT = "exchange-clipboard"
        const val RECEIVED = "received"
        const val INCOMING = "incoming"
        const val OUTGOING = "outgoing"
        const val MARGIN_BYTES = 64L * 1024 * 1024
    }
}
