package com.sza.fastmediasorter.data.capture

import android.content.Context
import android.net.Uri
import android.os.Environment
import com.sza.fastmediasorter.core.playback.ListenRecordingSinkHolder
import com.sza.fastmediasorter.core.save.SaveFallbackNotifier
import com.sza.fastmediasorter.data.transfer.strategies.LocalToFtpStrategy
import com.sza.fastmediasorter.data.transfer.strategies.LocalToSftpStrategy
import com.sza.fastmediasorter.data.transfer.strategies.LocalToSmbStrategy
import com.sza.fastmediasorter.data.transfer.strategy.CloudOperationStrategy
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.util.CaptureFileNamer
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S2881: everything a listening session must know about turning bytes into a saved recording.
 *
 * It sits beside [MicRecordingSaver] rather than inside the watch session owner because the four
 * transfer strategies a network destination needs are a `data/capture` concern, and the session
 * owner's subject is the watch link. Strategic ADR-3: the destination, the network upload and the
 * fallback to a local folder are the shipped dictaphone rules, reused rather than reinvented.
 */
@Singleton
class ListenRecordingStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val sinkHolder: ListenRecordingSinkHolder,
    private val micRecordingSaver: MicRecordingSaver,
    private val saveFallbackNotifier: SaveFallbackNotifier,
    private val localToFtpStrategy: LocalToFtpStrategy,
    private val localToSmbStrategy: LocalToSmbStrategy,
    private val localToSftpStrategy: LocalToSftpStrategy,
    private val cloudOperationStrategy: CloudOperationStrategy,
) {

    /** What became of a recording, in the three shapes a surface has words for. */
    enum class Outcome { SAVED, SAVED_TO_FALLBACK, FAILED }

    /**
     * Arm the recording for the session about to play [streamUrl].
     *
     * Called before playback starts: the data source is opened inside that start and asks the holder
     * while it opens, so arming afterwards would record a session from its second half onwards.
     */
    fun begin(streamUrl: String) {
        // Research 02 item 2: the watch serves self-delimiting ADTS frames, which this extension
        // names truthfully - the bytes are written through untouched, with no container to build.
        val name = CaptureFileNamer.shared.allocate(CaptureFileNamer.CaptureKind.AUDIO, ".aac")
        val directory = (context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: context.filesDir)
        directory.mkdirs()
        sinkHolder.arm(streamUrl, File(directory, name))
    }

    /**
     * Close the file and hand it back, or null when this session was not recording.
     *
     * Separate from [finish] because strategic §5.2 fixes the order: the file is whole before
     * playback is torn down, and only then does the save - which may reach the network - begin.
     */
    fun endCapture(): File? = sinkHolder.closeAndTake()

    /** Save what [endCapture] returned, reporting a fallback the same way a dictaphone clip does. */
    suspend fun finish(recording: File): Outcome {
        val outcome = if (!recording.exists() || recording.length() == 0L) {
            // The session ended before a single frame arrived; an empty file is not a recording.
            Timber.i("The watch listening session left no audio to save")
            Outcome.FAILED
        } else {
            save(recording)
        }
        recording.delete()
        return outcome
    }

    private suspend fun save(recording: File): Outcome {
        val result = micRecordingSaver.save(
            tempFile = recording,
            name = recording.name,
            browsedResource = null,
            upload = { tempFile, name, resource -> uploadToResource(tempFile, name, resource) },
        )
        result.fallbackReason?.let { reason ->
            saveFallbackNotifier.notify(
                reason = reason,
                folderLabel = result.folderLabel.orEmpty(),
                resourceName = result.resourceName.orEmpty(),
                background = true,
            )
        }
        return when {
            !result.success -> Outcome.FAILED
            result.fallbackReason != null -> Outcome.SAVED_TO_FALLBACK
            else -> Outcome.SAVED
        }
    }

    /** The dictaphone's own upload routing, so both recordings reach a destination the same way. */
    private suspend fun uploadToResource(tempFile: File, name: String, resource: MediaResource): Boolean {
        val sourceUri = Uri.fromFile(tempFile)
        val destUri = Uri.parse(resource.path.trimEnd('/') + '/' + Uri.encode(name))
        return when (resource.type) {
            ResourceType.FTP -> localToFtpStrategy.copy(sourceUri, destUri, true, null, null)
            ResourceType.SMB -> localToSmbStrategy.copy(sourceUri, destUri, true, null, null)
            ResourceType.SFTP -> localToSftpStrategy.copy(sourceUri, destUri, true, null, null)
            ResourceType.CLOUD -> cloudOperationStrategy.copyFile(
                tempFile.absolutePath,
                resource.path.trimEnd('/') + '/' + name,
                true,
                null,
            ).isSuccess
            else -> false
        }
    }
}
