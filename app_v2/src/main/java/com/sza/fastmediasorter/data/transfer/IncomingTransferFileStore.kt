package com.sza.fastmediasorter.data.transfer

import android.content.Context
import com.sza.fastmediasorter.core.util.rethrowIfCancellation
import com.sza.fastmediasorter.domain.model.transfer.DrivePacketNames
import com.sza.fastmediasorter.domain.port.IncomingFileResult
import com.sza.fastmediasorter.domain.port.IncomingTransferFileSink
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S3040: accepted media files land in the app's own `incoming` folder on shared external storage.
 *
 * App-owned storage rather than a user-picked directory, because a packet is claimed from a list
 * and not from a file picker - asking for a destination per packet would turn a one-tap accept into
 * a two-screen flow. The folder is browsable, so the user can move the file on afterwards.
 *
 * S4231: bytes go to `incoming/.partial/` first and are renamed into place only after the size and the
 * SHA-256 matched, so a damaged transfer never appears as a file and an existing file is never replaced.
 */
@Singleton
class IncomingTransferFileStore @Inject constructor(
    @param:ApplicationContext private val context: Context
) : IncomingTransferFileSink {

    private val directory: File
        get() = File(context.getExternalFilesDir(null) ?: context.filesDir, INCOMING_DIRECTORY)

    override suspend fun receive(
        fileName: String,
        expectedSize: Long?,
        expectedSha256: String?,
        source: InputStream
    ): IncomingFileResult = withContext(Dispatchers.IO) {
        // The name comes from another device's manifest: anything that is not a plain file name is
        // refused rather than normalised, so no packet can address a path of its choosing.
        if (!DrivePacketNames.isSafeBaseName(fileName)) {
            IncomingFileResult.Rejected("unsafe name")
        } else {
            receiveSafe(fileName, expectedSize, expectedSha256, source)
        }
    }

    override suspend fun discard(path: String) {
        withContext(Dispatchers.IO) {
            val file = File(path)
            if (file.parentFile?.canonicalPath == directory.canonicalPath && !file.delete()) {
                Timber.w("IncomingTransferFileStore: could not discard %s", file.name)
            }
        }
    }

    private suspend fun receiveSafe(
        fileName: String,
        expectedSize: Long?,
        expectedSha256: String?,
        source: InputStream
    ): IncomingFileResult {
        val partialDir = File(directory, PARTIAL_DIRECTORY).apply { mkdirs() }
        sweepStalePartials(partialDir)
        val partial = File.createTempFile("in-", ".part", partialDir)
        return try {
            val (size, digest) = copyHashing(source, partial)
            val mismatch = (expectedSize != null && size != expectedSize) ||
                (expectedSha256 != null && !digest.equals(expectedSha256, ignoreCase = true))
            if (mismatch) {
                IncomingFileResult.Rejected("size or digest mismatch")
            } else {
                place(partial, fileName)
            }
        } catch (error: IOException) {
            Timber.e(error, "Failed to receive incoming transfer file")
            IncomingFileResult.Rejected(error.javaClass.simpleName)
        } finally {
            if (partial.exists() && !partial.delete()) Timber.w("IncomingTransferFileStore: partial file left behind")
        }
    }

    private suspend fun copyHashing(source: InputStream, target: File): Pair<Long, String> {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(COPY_BUFFER_BYTES)
        var total = 0L
        target.outputStream().use { out ->
            while (true) {
                currentCoroutineContext().ensureActive()
                val read = source.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
                out.write(buffer, 0, read)
                total += read
            }
        }
        return total to digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun place(partial: File, fileName: String): IncomingFileResult {
        val target = freeName(directory, fileSystemSafe(fileName))
        return if (partial.renameTo(target)) {
            IncomingFileResult.Placed(target.absolutePath)
        } else {
            IncomingFileResult.Rejected("could not place the file")
        }
    }

    private fun sweepStalePartials(partialDir: File) {
        val cutoff = System.currentTimeMillis() - STALE_PARTIAL_MS
        partialDir.listFiles()?.filter { it.lastModified() < cutoff }?.forEach { stale ->
            runCatching { stale.delete() }.onFailure { it.rethrowIfCancellation() }
        }
    }

    internal companion object {
        const val INCOMING_DIRECTORY = "incoming"
        private const val PARTIAL_DIRECTORY = ".partial"
        private const val COPY_BUFFER_BYTES = 64 * 1024
        private val STALE_PARTIAL_MS = TimeUnit.DAYS.toMillis(1)
        private val refusedCharacters = Regex("[\\u0000-\\u001f/\\\\:*?\"<>|]")

        fun fileSystemSafe(name: String): String = name.replace(refusedCharacters, "_")

        /** `name`, else `name (2)`, `name (3)`.. - the first that does not exist yet, so nothing is overwritten. */
        fun freeName(directory: File, name: String): File {
            val candidate = File(directory, name)
            if (!candidate.exists()) return candidate
            val stem = name.substringBeforeLast('.', name)
            val extension = name.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }
            return generateSequence(2) { it + 1 }
                .map { index -> File(directory, "$stem ($index)$extension") }
                .first { !it.exists() }
        }
    }
}
