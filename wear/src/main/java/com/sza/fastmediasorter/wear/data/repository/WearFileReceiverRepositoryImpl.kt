package com.sza.fastmediasorter.wear.data.repository

import android.content.Context
import android.os.Environment
import android.util.LruCache
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.Wearable
import com.sza.fastmediasorter.wear.data.wear.WearDataLayerPaths
import com.sza.fastmediasorter.wear.domain.model.WEAR_FILE_TRANSFER_MAX_BYTES
import com.sza.fastmediasorter.wear.domain.model.WearBackgroundMode
import com.sza.fastmediasorter.wear.domain.model.WearFileReceiveOutcome
import com.sza.fastmediasorter.wear.domain.model.WearFileReceiveResult
import com.sza.fastmediasorter.wear.domain.model.WearFileTransferMetadata
import com.sza.fastmediasorter.wear.domain.repository.WearFileReceiverRepository
import com.sza.fastmediasorter.wear.domain.repository.WearPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S2000: the one definition of where an incoming file lands, so the writer here and the reader in
 * ResolveWearBackgroundUseCase cannot drift apart about it. A name that arrives twice overwrites:
 * the destination is built from the name alone and the copy truncates, so there is one slot per
 * name rather than a growing set of suffixed copies.
 */
internal fun incomingFilesDirectory(context: Context): File =
    context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir

/**
 * S1861: receives incoming phone -> watch files and saves them where the announced intent says.
 *
 * S1884: a file announced with `openNow` is a one-off view, so it lands in a pruned cache directory
 * rather than in downloads - see [destinationFor].
 *
 * Declarations are keyed by file name because that is the only correlator the channel carries - the
 * name rides as the trailing segment of the channel path, and the Data Layer offers no other handle
 * shared between the announcing message and the channel it announces.
 */
@Singleton
class WearFileReceiverRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesRepository: WearPreferencesRepository
) : WearFileReceiverRepository {

    private val declarations = LruCache<String, WearFileTransferMetadata>(MAX_DECLARATIONS)

    override fun declare(metadata: WearFileTransferMetadata) {
        if (metadata.name.isBlank()) {
            Timber.w("Ignoring an incoming file declaration with no name")
        } else {
            declarations.put(metadata.name, metadata)
        }
    }

    override suspend fun receiveFile(
        channel: ChannelClient.Channel,
        fileName: String
    ): WearFileReceiveResult = withContext(Dispatchers.IO) {
        val declared = declarations.remove(fileName)
        val limit = allowedBytes(declared)
        if (limit == null) {
            Timber.w(
                "Refusing incoming file %s: declared %d bytes over the %d byte ceiling",
                fileName,
                declared?.size ?: 0L,
                WEAR_FILE_TRANSFER_MAX_BYTES
            )
            closeChannel(channel)
            WearFileReceiveResult(WearFileReceiveOutcome.REFUSED_TOO_LARGE, declaration = declared)
        } else {
            copyFromChannel(channel, fileName, limit, destinationFor(declared, fileName), declared)
        }
    }

    /**
     * S1884: where this file is allowed to land.
     *
     * An announced `openNow` means the sender wants it looked at once, and strategic 2 Non-goals
     * forbids that file becoming a stored copy - so it goes to a cache directory that is emptied
     * before each arrival. Anything else is the shipped sorting transfer and keeps its downloads
     * destination byte for byte.
     */
    private fun destinationFor(declared: WearFileTransferMetadata?, fileName: String): File {
        val directory = if (declared?.openNow == true) {
            previewDirectory()
        } else {
            incomingFilesDirectory(context)
        }
        return File(directory, fileName)
    }

    /**
     * The preview directory, emptied first. A watch has little storage and a file sent to be seen
     * once has no reason to outlive the next one; nothing outside this directory is touched.
     */
    private fun previewDirectory(): File {
        val directory = File(context.cacheDir, PREVIEW_DIR_NAME)
        directory.mkdirs()
        directory.listFiles()?.forEach { stale ->
            if (!stale.delete()) {
                Timber.w("Could not prune the stale preview file %s", stale.name)
            }
        }
        return directory
    }

    /**
     * The byte budget this transfer is allowed to spend, or null when the declaration alone already
     * disqualifies it. A declaration smaller than the ceiling narrows the budget rather than raising
     * it, which is what turns an understated size into an abort instead of a free pass.
     */
    private fun allowedBytes(declared: WearFileTransferMetadata?): Long? = when {
        declared == null -> WEAR_FILE_TRANSFER_MAX_BYTES
        declared.size > WEAR_FILE_TRANSFER_MAX_BYTES -> null
        declared.size > 0L -> declared.size
        else -> WEAR_FILE_TRANSFER_MAX_BYTES
    }

    // A channel drain fails through GMS ApiException, IOException and RemoteException alike, and all
    // of them end this transfer identically; cancellation is rethrown before the broad arm.
    @Suppress("TooGenericExceptionCaught")
    private suspend fun copyFromChannel(
        channel: ChannelClient.Channel,
        fileName: String,
        limitBytes: Long,
        targetFile: File,
        declared: WearFileTransferMetadata?
    ): WearFileReceiveResult {
        return try {
            val written = Wearable.getChannelClient(context).getInputStream(channel).await().use { input ->
                FileOutputStream(targetFile).use { output -> pump(input, output, limitBytes) }
            }
            if (written == null) {
                Timber.w("Incoming file %s outran its declared size, partial write discarded", fileName)
                targetFile.delete()
                WearFileReceiveResult(WearFileReceiveOutcome.REFUSED_TOO_LARGE, declaration = declared)
            } else {
                Timber.i("Received %s (%d bytes) from the phone", fileName, written)
                if (fileName == WearDataLayerPaths.BACKGROUND_IMAGE_FILE_NAME) {
                    runCatching { preferencesRepository.setBackgroundMode(WearBackgroundMode.IMAGE) }
                        .onFailure { Timber.w(it, "Failed to update background mode preference on image arrival") }
                }
                WearFileReceiveResult(WearFileReceiveOutcome.SAVED, targetFile.absolutePath, declared)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Expected whenever the phone walks out of range mid-copy; the partial file is removed so
            // the watch never shows a truncated media item as if it had arrived whole.
            Timber.w(e, "Failed to receive %s from the phone", fileName)
            targetFile.delete()
            WearFileReceiveResult(WearFileReceiveOutcome.FAILED, declaration = declared)
        } finally {
            closeChannel(channel)
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

    private suspend fun closeChannel(channel: ChannelClient.Channel) {
        // NonCancellable so a cancelled receive still hands the channel back to GMS; a leaked channel
        // blocks the next transfer on the same path for the life of the process.
        withContext(NonCancellable) {
            runCatching { Wearable.getChannelClient(context).close(channel).await() }
                .onFailure { Timber.w(it, "Failed to close the incoming file channel") }
        }
    }

    private companion object {
        // Declarations have no timestamp, so a size cap bounds abandoned announcements without inventing age data.
        const val MAX_DECLARATIONS = 128

        const val RECEIVE_BUFFER_BYTES = 64 * 1024

        /** S1884: cache subdirectory holding the one file currently being previewed, and nothing else. */
        const val PREVIEW_DIR_NAME = "watch_preview"
    }
}
