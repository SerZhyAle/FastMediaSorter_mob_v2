package com.sza.fastmediasorter.data.transfer

import android.content.Context
import com.sza.fastmediasorter.core.util.rethrowIfCancellation
import com.sza.fastmediasorter.domain.port.IncomingTransferFileSink
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S3040: accepted media files land in the app's own `incoming` folder on shared external storage.
 *
 * App-owned storage rather than a user-picked directory, because a packet is claimed from a list
 * and not from a file picker - asking for a destination per packet would turn a one-tap accept into
 * a two-screen flow. The folder is browsable, so the user can move the file on afterwards.
 */
@Singleton
class IncomingTransferFileStore @Inject constructor(
    @param:ApplicationContext private val context: Context
) : IncomingTransferFileSink {

    override suspend fun write(fileName: String, bytes: ByteArray): String? = withContext(Dispatchers.IO) {
        val safeName = fileName.substringAfterLast('/').substringAfterLast('\\')
        when {
            // The name comes from another device's manifest: anything that is not a plain file name
            // is refused rather than normalised, so no packet can address a path of its choosing.
            safeName.isEmpty() || safeName == "." || safeName == ".." -> null
            else -> writeInto(safeName, bytes)
        }
    }

    private fun writeInto(safeName: String, bytes: ByteArray): String? {
        val directory = File(context.getExternalFilesDir(null) ?: context.filesDir, INCOMING_DIRECTORY)
        return runCatching {
            directory.mkdirs()
            val target = File(directory, safeName)
            target.writeBytes(bytes)
            target.absolutePath
        }.getOrElse { error ->
            error.rethrowIfCancellation()
            Timber.e(error, "Failed to store incoming transfer file $safeName")
            null
        }
    }

    private companion object {
        const val INCOMING_DIRECTORY = "incoming"
    }
}
