package com.sza.fastmediasorter.domain.usecase

import android.content.Context
import android.os.Environment
import com.sza.fastmediasorter.data.transfer.local.LocalDestinationClassifier
import com.sza.fastmediasorter.data.transfer.local.LocalDestinationWriter
import com.sza.fastmediasorter.data.transfer.local.LocalSink
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.model.WEAR_FILE_TRANSFER_MAX_BYTES
import com.sza.fastmediasorter.domain.model.WearFileReceiveOutcome
import com.sza.fastmediasorter.domain.model.WearFileReceiveResult
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.util.VirtualPathUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

/** Chunk size for the incoming copy; also how often the size budget is re-checked. */
private const val RECEIVE_BUFFER_BYTES = 64 * 1024

/**
 * S1861: places one file sent by the watch into the phone's chosen destination.
 *
 * "Chosen" is the destination the user already picked for everything else that lands on the phone -
 * the first writable local destination resource. A second, watch-only setting would ask the user the
 * same question twice; when no destination is configured the file goes to the app's own downloads
 * directory rather than being dropped.
 *
 * It takes a stream rather than a channel so the GMS types stay in `src/wearGms`: this file compiles
 * into every flavor, including the ones with no Play Services Wearable on the classpath.
 */
@Singleton
class ReceiveWatchFileUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val resourceRepository: ResourceRepository,
    private val destinationClassifier: LocalDestinationClassifier,
    private val destinationWriter: LocalDestinationWriter
) {

    suspend operator fun invoke(
        fileName: String,
        declaredBytes: Long,
        source: InputStream
    ): WearFileReceiveResult = withContext(Dispatchers.IO) {
        val limit = allowedBytes(declaredBytes)
        if (limit == null) {
            Timber.i("Refusing watch file %s: declared %d bytes over the ceiling", fileName, declaredBytes)
            WearFileReceiveResult(WearFileReceiveOutcome.REFUSED_TOO_LARGE)
        } else {
            writeToDestination(fileName, limit, source)
        }
    }

    /**
     * The byte budget this transfer may spend, or null when the declaration alone disqualifies it.
     * A declaration below the ceiling narrows the budget instead of raising it, which is what turns
     * an understated size into an abort rather than a free pass.
     */
    private fun allowedBytes(declaredBytes: Long): Long? = when {
        declaredBytes > WEAR_FILE_TRANSFER_MAX_BYTES -> null
        declaredBytes > 0L -> declaredBytes
        else -> WEAR_FILE_TRANSFER_MAX_BYTES
    }

    private suspend fun writeToDestination(
        fileName: String,
        limitBytes: Long,
        source: InputStream
    ): WearFileReceiveResult {
        val sink = openSink(fileName)
        return if (sink == null) {
            Timber.w("No writable destination for the incoming watch file %s", fileName)
            WearFileReceiveResult(WearFileReceiveOutcome.NO_DESTINATION)
        } else {
            copyInto(sink, fileName, limitBytes, source)
        }
    }

    // A local sink fails through IOException, SecurityException and MediaStore's own unchecked
    // rejections alike, and every one of them ends this transfer the same way.
    @Suppress("TooGenericExceptionCaught")
    private suspend fun copyInto(
        sink: LocalSink,
        fileName: String,
        limitBytes: Long,
        source: InputStream
    ): WearFileReceiveResult = try {
        val written = pump(source, sink.outputStream, limitBytes)
        if (written == null) {
            Timber.w("Incoming watch file %s outran its declared size, discarded", fileName)
            sink.abort()
            WearFileReceiveResult(WearFileReceiveOutcome.REFUSED_TOO_LARGE)
        } else {
            commit(sink, fileName, written)
        }
    } catch (e: CancellationException) {
        sink.abort()
        throw e
    } catch (e: Exception) {
        // The watch walking out of range surfaces as an IOException on the copy; the half-written
        // destination is removed so a truncated file is never published to the gallery.
        Timber.w(e, "Failed to receive %s from the watch", fileName)
        sink.abort()
        WearFileReceiveResult(WearFileReceiveOutcome.FAILED)
    }

    private suspend fun commit(sink: LocalSink, fileName: String, written: Long): WearFileReceiveResult =
        sink.commit().fold(
            onSuccess = { path ->
                Timber.i("Received %s (%d bytes) from the watch into %s", fileName, written, path)
                WearFileReceiveResult(WearFileReceiveOutcome.SAVED, path)
            },
            onFailure = { error ->
                Timber.w(error, "Failed to publish the received watch file %s", fileName)
                WearFileReceiveResult(WearFileReceiveOutcome.FAILED)
            }
        )

    /**
     * Opens the destination, uniquifying the name once if it is taken. Overwriting is never right
     * here: the watch chooses the name and the phone's copy may be an unrelated file of the same one.
     */
    private suspend fun openSink(fileName: String): LocalSink? {
        val targetDir = resolveTargetDirectory()
        val first = destinationWriter.open(
            destinationClassifier.classify("$targetDir/$fileName"),
            overwrite = false
        )
        return first.getOrNull() ?: destinationWriter.open(
            destinationClassifier.classify("$targetDir/${uniquify(fileName)}"),
            overwrite = false
        ).getOrNull()
    }

    private fun uniquify(fileName: String): String {
        val base = fileName.substringBeforeLast('.', fileName)
        val extension = fileName.substringAfterLast('.', "")
        val stamp = System.currentTimeMillis()
        return if (extension.isEmpty()) "${base}_$stamp" else "${base}_$stamp.$extension"
    }

    /**
     * The user's first writable local destination, or the app's own downloads directory when none is
     * configured. Network and cloud destinations are skipped: this path writes bytes with a local
     * sink, and routing a watch file onward to SMB is a transfer of its own, not a save.
     */
    private suspend fun resolveTargetDirectory(): String {
        val destination = resourceRepository.getAllResourcesSync()
            .filter { it.isDestination && it.type == ResourceType.LOCAL && it.isWritable }
            .filterNot { VirtualPathUtils.isVirtualPath(it.path) || it.path.startsWith("content://") }
            .minByOrNull { it.destinationOrder ?: Int.MAX_VALUE }
        return destination?.path
            ?: context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.absolutePath
            ?: context.filesDir.absolutePath
    }

    /** Returns the byte count written, or null once the budget is exceeded and the copy is abandoned. */
    private fun pump(input: InputStream, output: OutputStream, limitBytes: Long): Long? {
        val buffer = ByteArray(RECEIVE_BUFFER_BYTES)
        var written = 0L
        var read = input.read(buffer)
        while (read >= 0) {
            written += read
            if (written > limitBytes) return null
            output.write(buffer, 0, read)
            read = input.read(buffer)
        }
        output.flush()
        return written
    }
}
