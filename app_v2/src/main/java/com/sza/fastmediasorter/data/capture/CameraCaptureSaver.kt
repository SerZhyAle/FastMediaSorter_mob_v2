package com.sza.fastmediasorter.data.capture

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import com.sza.fastmediasorter.core.clipboard.ImageClipboardWriter
import com.sza.fastmediasorter.data.common.MediaTypeUtils
import com.sza.fastmediasorter.data.local.LocalMediaScanner
import com.sza.fastmediasorter.data.transfer.local.LocalDestinationClassifier
import com.sza.fastmediasorter.data.transfer.local.LocalDestinationWriter
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.model.SaveFallbackReason
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.stats.CaptureKind
import com.sza.fastmediasorter.domain.stats.StatsEvent
import com.sza.fastmediasorter.domain.stats.StatsSink
import com.sza.fastmediasorter.util.VirtualPathUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single, Activity-free implementation of the "save a freshly captured photo into its target"
 * routing extracted from [com.sza.fastmediasorter.ui.browse.managers.BrowseCameraCaptureManager]
 * (S0369). Browse and the quick-capture home widget both delegate here so there is exactly one
 * camera-to-resource save backend.
 *
 * Routing mirrors the original Browse logic verbatim:
 * - a [CameraCaptureTarget.CameraFolder] (or any virtual/camera-like resource path) -> public
 *   `DCIM/Camera` directory + a legacy media-scanner broadcast so the gallery picks it up;
 * - a LOCAL [CameraCaptureTarget.Resource] -> direct `copyTo` under the resource root;
 * - an SMB/SFTP/FTP/CLOUD resource -> the caller-supplied [upload] strategy (the network/cloud
 *   copy backends are context-specific and stay outside this Singleton).
 *
 * The temp file is always deleted once the save attempt finishes, success or failure - identical
 * to the pre-refactor behaviour.
 */
@Singleton
class CameraCaptureSaver @Inject constructor(
    @ApplicationContext private val context: Context,
    // S0465: route on-device writes through the MediaStore-aware writer so public-collection
    // targets (DCIM/Camera, Movies, Downloads fallback) do not fail with EACCES on API 29+ scoped
    // storage / restrictive OEM policy - same fix as S0464 applied to the mic path.
    private val destinationClassifier: LocalDestinationClassifier,
    private val destinationWriter: LocalDestinationWriter,
    private val localCaptureDestinationWriter: LocalCaptureDestinationWriter,
    // S0473: usage-statistics sink. Fire-and-forget; no-ops when collection is disabled.
    private val statsSink: StatsSink,
    // S0469: optional "copy captured photo to system clipboard" modifier, gated on a settings flag.
    private val settingsRepository: SettingsRepository,
    private val imageClipboardWriter: ImageClipboardWriter,
) {

    /**
     * Persist [tempFile] under [target] as [name].
     *
     * @param upload invoked only for network/cloud resource targets; returns true on a successful
     *   transfer. Local and camera-folder targets never call it.
     * @return a [SaveResult] carrying the on-disk/remote path on success (for an optional
     *   open-for-editing follow-up) or a failure category mapped by the caller to a user message.
     */
    suspend fun save(
        tempFile: File,
        name: String,
        target: CameraCaptureTarget,
        upload: suspend (tempFile: File, name: String, resource: CameraCaptureTarget.Resource) -> Boolean,
    ): SaveResult = withContext(Dispatchers.IO) {
        // S1573: the saver owns its dispatcher rather than inheriting the caller's. Every production
        // entry point reaches it from lifecycleScope.launch, i.e. Dispatchers.Main.immediate, which
        // left the settings read, the media-scanner broadcast, the whole network upload and the
        // temp-file delete on the main thread - StrictMode reported the last of those. withContext
        // returns to the caller's context, so the post-save UI work keeps running on Main unchanged.
        var savedPath = resolveSavedPath(target, name)
        Timber.i(
            "CameraCaptureSaver: save ENTRY tempFile=%s name=%s target=%s savedPath=%s",
            tempFile.absolutePath,
            name,
            target,
            savedPath,
        )
        // S0469: a captured photo can additionally be placed on the system clipboard. Done before the
        // temp file is deleted, gated to images only (the same saver also handles video capture), and
        // additive - it never alters the save routing/result below (strategic goal 4).
        val copiedToClipboard = maybeCopyToClipboard(tempFile, name)
        var failure: SaveResult.Failure? = null
        // S0522: set when a network upload could not be written and the capture was redirected to a
        // local public collection so it is never lost.
        var fallbackReason: SaveFallbackReason? = null
        val success = try {
            when (target) {
                is CameraCaptureTarget.CameraFolder -> saveToDcim(tempFile, name)
                is CameraCaptureTarget.Resource -> {
                    if (isVirtualCameraTarget(target.path)) {
                        saveToDcim(tempFile, name)
                    } else {
                        when (target.type) {
                            ResourceType.LOCAL -> {
                                val localResult = saveLocal(tempFile, name, target.path)
                                localResult.onSuccess { savedPath = it }.isSuccess
                            }
                            ResourceType.SMB, ResourceType.SFTP, ResourceType.FTP,
                            ResourceType.CLOUD -> {
                                if (upload(tempFile, name, target)) {
                                    true
                                } else {
                                    // S0522: the network/cloud upload failed (unreachable or write error).
                                    // Save to the local default folder for this media type instead of
                                    // dropping the capture; the caller surfaces the redirect to the user.
                                    fallbackReason = SaveFallbackReason.ResourceWriteFailed
                                    val localOk = saveToLocalFallback(tempFile, name)
                                    if (localOk) savedPath = localFallbackPath(name)
                                    localOk
                                }
                            }
                            ResourceType.WEAR_WATCH -> {
                                // S1861: a capture is written here and handed to the transfer queue as
                                // a separate step - the camera path must not block on a bridge that may
                                // be down. Unlike the stream branch this cannot throw: a watch target
                                // is writable, so the branch is reachable and a throw would be a crash.
                                fallbackReason = SaveFallbackReason.ResourceUnavailable
                                val localOk = saveToLocalFallback(tempFile, name)
                                if (localOk) savedPath = localFallbackPath(name)
                                localOk
                            }
                            ResourceType.HTTP_STREAM, ResourceType.RTSP_STREAM ->
                                throw IllegalArgumentException("Cannot save a capture to an internet stream target: ${target.type}")
                        }
                    }
                }
            }
        } catch (e: IOException) {
            Timber.e(e, "CameraCaptureSaver: save IO error target=%s", target)
            failure = SaveResult.Failure.Io
            false
        } catch (e: Exception) {
            Timber.e(e, "CameraCaptureSaver: save FAILED target=%s", target)
            failure = SaveResult.Failure.Generic
            false
        } finally {
            tempFile.delete()
        }
        Timber.i("CameraCaptureSaver: save EXIT success=%b name=%s", success, name)
        when {
            success -> {
                // S0473: a media capture completed. This saver is media-agnostic, so classify by the
                // output file name - a video extension counts as a recorded video, otherwise a photo.
                val kind = if (MediaTypeUtils.getMediaType(name) == MediaType.VIDEO) {
                    CaptureKind.VIDEO
                } else {
                    CaptureKind.PHOTO
                }
                statsSink.record(StatsEvent.Capture(kind))
                SaveResult.Success(savedPath, copiedToClipboard = copiedToClipboard, fallbackReason = fallbackReason)
            }
            else -> failure ?: SaveResult.Failure.Generic
        }
    }

    /**
     * S0469: when the capture is a photo and the clipboard option is on, place the encoded image file
     * on the system clipboard verbatim. Returns true only when the copy succeeded so callers can show
     * a confirmation. Video/audio captures are excluded by the [MediaType.IMAGE] gate.
     */
    private suspend fun maybeCopyToClipboard(tempFile: File, name: String): Boolean {
        if (MediaTypeUtils.getMediaType(name) != MediaType.IMAGE) return false
        val enabled = settingsRepository.getSettings().first().cameraCaptureCopyToClipboard
        if (!enabled) return false
        return imageClipboardWriter.copyImageFile(tempFile)
    }

    /** Pre-compute where the file ends up, matching the original Browse path resolution exactly. */
    private fun resolveSavedPath(target: CameraCaptureTarget, name: String): String = when (target) {
        is CameraCaptureTarget.CameraFolder -> File(cameraDir(), name).absolutePath
        is CameraCaptureTarget.Resource -> when {
            isVirtualCameraTarget(target.path) -> File(cameraDir(), name).absolutePath
            target.type == ResourceType.LOCAL -> target.path.trimEnd('/') + '/' + name
            else -> target.path.trimEnd('/') + '/' + name
        }
    }

    private fun isVirtualCameraTarget(path: String): Boolean =
        VirtualPathUtils.isVirtualPath(path) ||
            path == LocalMediaScanner.VIRTUAL_PATH_ALL_VIDEO ||
            path == LocalMediaScanner.VIRTUAL_PATH_ALL_IMAGES ||
            path == LocalMediaScanner.VIRTUAL_PATH_CAMERA_PHOTOS

    private suspend fun saveToDcim(tempFile: File, name: String): Boolean {
        val dest = File(cameraDir(), name)
        val saved = writeToDevice(tempFile, dest.absolutePath)
        if (saved) {
            // Pre-Q the writer routes DCIM through FileOutputStream, so the gallery still needs the
            // legacy scan broadcast. On Q+ MediaStore already indexes the image; the broadcast is a
            // harmless no-op against the same path.
            @Suppress("DEPRECATION")
            context.sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(dest)))
        }
        return saved
    }

    private suspend fun saveLocal(tempFile: File, name: String, rootPath: String): Result<String> =
        localCaptureDestinationWriter.write(tempFile, rootPath, name)

    /**
     * S0465: write [tempFile] to an on-device path through the MediaStore-aware destination writer.
     * A public collection (DCIM/Camera, Movies, Downloads fallback) is published via MediaStore on
     * API 29+ - the previous direct `File.copyTo` failed with EACCES under scoped storage /
     * restrictive OEM policy. Non-public paths still go through a plain file stream. Mirror of
     * [com.sza.fastmediasorter.ui.browse.managers.BrowseMicRecordingManager.writeToDevice] (S0464).
     */
    private suspend fun writeToDevice(tempFile: File, absolutePath: String): Boolean =
        withContext(Dispatchers.IO) {
            val category = destinationClassifier.classify(absolutePath)
            val sink = destinationWriter.open(category, overwrite = true).getOrElse { e ->
                Timber.e(e, "capture save: writer.open failed for %s", absolutePath)
                return@withContext false
            }
            try {
                tempFile.inputStream().use { input -> input.copyTo(sink.outputStream) }
                sink.commit().isSuccess
            } catch (e: Exception) {
                Timber.e(e, "capture save: streaming failed for %s", absolutePath)
                sink.abort()
                false
            }
        }

    private fun cameraDir(): File =
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Camera")

    private fun moviesDir(): File =
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)

    /**
     * S0522: local default folder for a capture whose network destination could not be written.
     * Video recordings go to the public Movies folder; photos to DCIM/Camera. Routed through the
     * MediaStore-aware writer so it is scoped-storage safe on API 29+.
     */
    private suspend fun saveToLocalFallback(tempFile: File, name: String): Boolean =
        if (MediaTypeUtils.getMediaType(name) == MediaType.VIDEO) {
            writeToDevice(tempFile, File(moviesDir(), name).absolutePath)
        } else {
            saveToDcim(tempFile, name)
        }

    private fun localFallbackPath(name: String): String =
        if (MediaTypeUtils.getMediaType(name) == MediaType.VIDEO) {
            File(moviesDir(), name).absolutePath
        } else {
            File(cameraDir(), name).absolutePath
        }
}

/**
 * Where a captured photo should be saved. Decouples [CameraCaptureSaver] from the full
 * `MediaResource`/`ResourceEntity` models so both Browse and the widget can map their own state in.
 */
sealed interface CameraCaptureTarget {

    /** Explicit "device camera folder" pseudo-target (DCIM/Camera), not a virtual aggregate. */
    data object CameraFolder : CameraCaptureTarget

    /** A real, writable resource. A virtual/camera-like [path] is still routed to DCIM/Camera. */
    data class Resource(
        val id: Long,
        val name: String,
        val path: String,
        val type: ResourceType,
    ) : CameraCaptureTarget
}

/** Outcome of a [CameraCaptureSaver.save] call. */
sealed interface SaveResult {

    /**
     * Saved successfully; [savedPath] is the final local/remote location (for open-for-editing).
     * [copiedToClipboard] is true when the S0469 option additionally placed the photo on the clipboard.
     */
    data class Success(
        val savedPath: String,
        val copiedToClipboard: Boolean = false,
        // S0522: non-null when a network destination could not be written and the capture was
        // redirected to a local default folder; the caller turns this into a user notification.
        val fallbackReason: SaveFallbackReason? = null,
    ) : SaveResult

    sealed interface Failure : SaveResult {
        /** I/O error during copy/upload - caller shows the I/O-specific message. */
        data object Io : Failure

        /** Any other failure - caller shows the generic save-error message. */
        data object Generic : Failure
    }
}
