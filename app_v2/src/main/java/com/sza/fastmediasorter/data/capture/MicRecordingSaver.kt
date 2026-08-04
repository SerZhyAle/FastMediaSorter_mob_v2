package com.sza.fastmediasorter.data.capture

import com.sza.fastmediasorter.core.network.NetworkStateMonitor
import com.sza.fastmediasorter.data.transfer.local.LocalDestinationClassifier
import com.sza.fastmediasorter.data.transfer.local.LocalDestinationWriter
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.model.SaveFallbackReason
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.stats.CaptureKind
import com.sza.fastmediasorter.domain.stats.StatsEvent
import com.sza.fastmediasorter.domain.stats.StatsSink
import com.sza.fastmediasorter.util.CaptureDestinationPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single Activity-free backend for "save a finished microphone recording to its destination",
 * shared by the Browse mic flow and the home-screen Quick Audio Recorder widget (S0526). Mirrors
 * [CameraCaptureSaver]: resolves the configured mic destination, writes locally via the
 * MediaStore-aware writer or uploads to a network resource, and applies the S0522 fallback to a
 * local default folder (never losing the recording). The caller owns the recorder lifecycle, the
 * temp file, and any user notification - this class returns the outcome.
 */
@Singleton
class MicRecordingSaver @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val resourceRepository: ResourceRepository,
    private val destinationClassifier: LocalDestinationClassifier,
    private val destinationWriter: LocalDestinationWriter,
    private val localCaptureDestinationWriter: LocalCaptureDestinationWriter,
    private val networkStateMonitor: NetworkStateMonitor,
    private val statsSink: StatsSink,
) {

    /**
     * Outcome of a save. [fallbackReason] is non-null only when a network destination could not be
     * written and the recording was redirected to [folderLabel]; the caller turns it into a notice.
     */
    data class Result(
        val success: Boolean,
        val savedPath: String?,
        val fallbackReason: SaveFallbackReason?,
        val resourceName: String?,
        val folderLabel: String?,
    )

    /**
     * @param browsedResource the resource currently browsed (Browse flow), or null (widget) so only
     *   the configured destination is considered.
     * @param upload invoked only for a network/cloud target; returns true on a successful transfer.
     */
    suspend fun save(
        tempFile: File,
        name: String,
        browsedResource: MediaResource?,
        upload: suspend (tempFile: File, name: String, resource: MediaResource) -> Boolean,
    ): Result {
        Timber.d("S1354: microphone capture local destination save")
        val targetResource = resolveMicSaveResource(browsedResource)
        var success = false
        var savedPath: String? = null
        var fellBackUnavailable = false
        val downloadsName = CaptureDestinationPolicy.resolveMicDestination(null).name
        try {
            when {
                targetResource == null -> {
                    val path = File(CaptureDestinationPolicy.resolveMicDestination(null), name).absolutePath
                    success = writeToDevice(tempFile, path)
                    if (success) savedPath = path
                }
                targetResource.type == ResourceType.LOCAL -> {
                    val saved = localCaptureDestinationWriter.write(tempFile, targetResource.path, name)
                    success = saved.isSuccess
                    if (success) savedPath = saved.getOrThrow()
                }
                else -> {
                    if (networkStateMonitor.canReach(targetResource.type)) {
                        success = upload(tempFile, name, targetResource)
                        if (success) savedPath = targetResource.path.trimEnd('/') + '/' + name
                    }
                    if (!success) {
                        // Unreachable transport or failed upload - redirect locally so the clip survives.
                        fellBackUnavailable = true
                        val path = File(CaptureDestinationPolicy.resolveMicDestination(null), name).absolutePath
                        success = writeToDevice(tempFile, path)
                        if (success) savedPath = path
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "MicRecordingSaver: save failed name=$name")
        }
        if (success) statsSink.record(StatsEvent.Capture(CaptureKind.VOICE))
        return Result(
            success = success,
            savedPath = if (success) savedPath else null,
            fallbackReason = if (success && fellBackUnavailable) SaveFallbackReason.ResourceUnavailable else null,
            resourceName = targetResource?.name,
            folderLabel = if (fellBackUnavailable) downloadsName else null,
        )
    }

    /**
     * Pick the destination: a usable configured `micRecordingDestinationResourceId` wins; otherwise
     * the browsed resource when usable; otherwise null, routing to the public default folder.
     */
    private suspend fun resolveMicSaveResource(browsedResource: MediaResource?): MediaResource? {
        val configuredId = settingsRepository.getSettings().first()
            .micRecordingDestinationResourceId
            ?.toLongOrNull()
        if (configuredId != null) {
            val configured = resourceRepository.getResourceById(configuredId)
            if (CaptureDestinationPolicy.isUsableTarget(configured)) return configured
        }
        return if (browsedResource != null && CaptureDestinationPolicy.isUsableTarget(browsedResource)) {
            browsedResource
        } else {
            null
        }
    }

    /**
     * Write [tempFile] to an on-device path through the MediaStore-aware destination writer so a
     * public collection is published correctly on API 29+ scoped storage.
     */
    private suspend fun writeToDevice(tempFile: File, absolutePath: String): Boolean =
        withContext(Dispatchers.IO) {
            val category = destinationClassifier.classify(absolutePath)
            val sink = destinationWriter.open(category, overwrite = true).getOrElse { e ->
                Timber.e(e, "MicRecordingSaver: writer.open failed for %s", absolutePath)
                return@withContext false
            }
            try {
                tempFile.inputStream().use { input -> input.copyTo(sink.outputStream) }
                sink.commit().isSuccess
            } catch (e: Exception) {
                Timber.e(e, "MicRecordingSaver: streaming failed for %s", absolutePath)
                sink.abort()
                false
            }
        }
}
