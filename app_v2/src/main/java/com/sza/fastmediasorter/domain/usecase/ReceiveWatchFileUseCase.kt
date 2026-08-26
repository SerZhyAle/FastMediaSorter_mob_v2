package com.sza.fastmediasorter.domain.usecase

import android.content.Context
import android.os.Environment
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.sza.fastmediasorter.data.local.staging.StagedKind
import com.sza.fastmediasorter.data.local.staging.StagingDirectoryProvider
import com.sza.fastmediasorter.data.transfer.local.LocalDestinationClassifier
import com.sza.fastmediasorter.data.transfer.local.LocalDestinationWriter
import com.sza.fastmediasorter.data.transfer.local.LocalSink
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.model.WEAR_FILE_TRANSFER_MAX_BYTES
import com.sza.fastmediasorter.domain.model.WatchFileDestination
import com.sza.fastmediasorter.domain.model.WearFileReceiveOutcome
import com.sza.fastmediasorter.domain.model.WearFileReceiveOutcome.QUEUED_FOR_UPLOAD
import com.sza.fastmediasorter.domain.model.WearFileReceiveResult
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.util.VirtualPathUtils
import com.sza.fastmediasorter.worker.WearReceivedFileUploadWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
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
    private val destinationWriter: LocalDestinationWriter,
    private val stagingDirectoryProvider: StagingDirectoryProvider,
    private val workManager: WorkManager
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
        return when (val destination = resolveDestination()) {
            is WatchFileDestination.Local -> {
                val sink = openSink(destination, fileName)
                if (sink == null) {
                    Timber.w("No writable destination for the incoming watch file %s", fileName)
                    WearFileReceiveResult(WearFileReceiveOutcome.NO_DESTINATION)
                } else {
                    copyInto(sink, fileName, limitBytes, source)
                }
            }
            is WatchFileDestination.Remote -> {
                stageForUpload(destination, fileName, limitBytes, source)
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun stageForUpload(
        destination: WatchFileDestination.Remote,
        fileName: String,
        limitBytes: Long,
        source: InputStream
    ): WearFileReceiveResult {
        val stagingDir = stagingDirectoryProvider.directoryFor(StagedKind.WATCH_RECEIVED)
        val initialFile = File(stagingDir, fileName)
        val targetFile = if (initialFile.exists()) {
            File(stagingDir, uniquify(fileName))
        } else {
            initialFile
        }

        return try {
            val written = FileOutputStream(targetFile).use { output ->
                pump(source, output, limitBytes)
            }
            if (written == null) {
                Timber.w("Watch file %s outran declared size while staging, discarded", fileName)
                targetFile.delete()
                WearFileReceiveResult(WearFileReceiveOutcome.REFUSED_TOO_LARGE)
            } else {
                enqueueUploadWorker(
                    stagedPath = targetFile.absolutePath,
                    resourceId = destination.resourceId,
                    parentPath = destination.parentPath,
                    fileName = fileName
                )
                WearFileReceiveResult(QUEUED_FOR_UPLOAD, targetFile.absolutePath)
            }
        } catch (e: CancellationException) {
            targetFile.delete()
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Failed to stage watch file %s for upload", fileName)
            targetFile.delete()
            WearFileReceiveResult(WearFileReceiveOutcome.FAILED)
        }
    }

    private fun enqueueUploadWorker(
        stagedPath: String,
        resourceId: Long,
        parentPath: String,
        fileName: String
    ) {
        val inputData = workDataOf(
            WearReceivedFileUploadWorker.KEY_STAGED_PATH to stagedPath,
            WearReceivedFileUploadWorker.KEY_RESOURCE_ID to resourceId,
            WearReceivedFileUploadWorker.KEY_PARENT_PATH to parentPath,
            WearReceivedFileUploadWorker.KEY_FILE_NAME to fileName
        )
        val workRequest = OneTimeWorkRequestBuilder<WearReceivedFileUploadWorker>()
            .setInputData(inputData)
            .build()
        workManager.enqueue(workRequest)
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
    private suspend fun openSink(destination: WatchFileDestination.Local, fileName: String): LocalSink? {
        val targetDir = destination.directoryPath
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
     * The user's first writable destination, or the app's own downloads directory as [WatchFileDestination.Local]
     * when none is configured.
     */
    private suspend fun resolveDestination(): WatchFileDestination {
        val resource = resourceRepository.getAllResourcesSync()
            .filter { it.isDestination && it.type != ResourceType.WEAR_WATCH && it.isWritable }
            .filterNot { VirtualPathUtils.isVirtualPath(it.path) || it.path.startsWith("content://") }
            .minByOrNull { it.destinationOrder ?: Int.MAX_VALUE }

        if (resource == null) {
            val fallbackPath = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.absolutePath
                ?: context.filesDir.absolutePath
            return WatchFileDestination.Local(fallbackPath)
        }

        val path = resource.path
        val isRemote = path.startsWith("smb://") ||
            path.startsWith("sftp://") ||
            path.startsWith("ftp://") ||
            path.startsWith("cloud://")

        return if (isRemote) {
            WatchFileDestination.Remote(resource.id, path)
        } else {
            WatchFileDestination.Local(path)
        }
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
