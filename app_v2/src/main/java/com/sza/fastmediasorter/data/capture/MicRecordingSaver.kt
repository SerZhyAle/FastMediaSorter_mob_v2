package com.sza.fastmediasorter.data.capture

import com.sza.fastmediasorter.core.network.NetworkStateMonitor
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.model.SaveFallbackReason
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.stats.CaptureKind
import com.sza.fastmediasorter.domain.stats.StatsEvent
import com.sza.fastmediasorter.domain.stats.StatsSink
import com.sza.fastmediasorter.util.CaptureDestinationPolicy
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single Activity-free backend for "save a finished microphone recording to its destination",
 * shared by the Browse mic flow and the home-screen Quick Audio Recorder widget (S0526). Mirrors
 * [CameraCaptureSaver]: resolves the configured mic destination, writes locally via the
 * collision-aware capture writer or uploads to a network resource, and applies the S0522 fallback to
 * the recordings folder (never losing the recording). The caller owns the recorder lifecycle, the
 * temp file, and any user notification - this class returns the outcome.
 */
@Singleton
class MicRecordingSaver @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val resourceRepository: ResourceRepository,
    private val localCaptureDestinationWriter: LocalCaptureDestinationWriter,
    private val networkStateMonitor: NetworkStateMonitor,
    private val statsSink: StatsSink,
) {

    /**
     * Outcome of a save. [fallbackReason] is non-null only when a network destination could not be
     * written and the recording was redirected to [folderLabel]; the caller turns it into a notice.
     * [savedName] is the name the file really got, which differs from the requested one when the
     * destination already held a file of that name.
     */
    data class Result(
        val success: Boolean,
        val savedPath: String?,
        val fallbackReason: SaveFallbackReason?,
        val resourceName: String?,
        val folderLabel: String?,
        val savedName: String? = null,
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
        val targetResource = resolveMicSaveResource(browsedResource)
        var saved: Saved? = null
        var fellBackUnavailable = false
        val defaultDir = CaptureDestinationPolicy.resolveMicDestination(null)
        Timber.d("S3746: mic save name=%s defaultDir=%s", name, defaultDir)
        try {
            when {
                targetResource == null -> saved = writeLocal(tempFile, defaultDir, name)
                targetResource.type == ResourceType.LOCAL ->
                    saved = localCaptureDestinationWriter.writeCapture(tempFile, targetResource.path, name)
                        .getOrNull()
                        ?.let { Saved(it.location, it.displayName) }
                else -> {
                    if (networkStateMonitor.canReach(targetResource.type) && upload(tempFile, name, targetResource)) {
                        saved = Saved(targetResource.path.trimEnd('/') + '/' + name, name)
                    }
                    if (saved == null) {
                        // Unreachable transport or failed upload - redirect locally so the clip survives.
                        fellBackUnavailable = true
                        saved = writeLocal(tempFile, defaultDir, name)
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "MicRecordingSaver: save failed name=$name")
        }
        val success = saved != null
        if (success) statsSink.record(StatsEvent.Capture(CaptureKind.VOICE))
        return Result(
            success = success,
            savedPath = saved?.path,
            fallbackReason = if (success && fellBackUnavailable) SaveFallbackReason.ResourceUnavailable else null,
            resourceName = targetResource?.name,
            folderLabel = if (fellBackUnavailable) defaultDir.name else null,
            savedName = saved?.name,
        )
    }

    private data class Saved(val path: String, val name: String)

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
     * Write [tempFile] into the public folder [dir] through the MediaStore-aware capture writer so the
     * collection is published correctly on API 29+ scoped storage. The returned path is the absolute
     * file path under the name the writer chose.
     */
    private suspend fun writeLocal(tempFile: File, dir: File, name: String): Saved? =
        localCaptureDestinationWriter.writeCapture(tempFile, dir.absolutePath, name)
            .onFailure { e -> Timber.e(e, "MicRecordingSaver: write failed for %s in %s", name, dir) }
            .getOrNull()
            ?.let { Saved(File(dir, it.displayName).absolutePath, it.displayName) }
}
