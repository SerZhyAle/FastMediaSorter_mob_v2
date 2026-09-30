package com.sza.fastmediasorter.ui.mirror.helpers

import android.content.Context
import android.os.Environment
import com.sza.fastmediasorter.core.di.ApplicationScope
import com.sza.fastmediasorter.domain.usecase.SaveMirrorCaptureUseCase
import com.sza.fastmediasorter.util.CaptureFileNamer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject

/**
 * Allocates the temp files the mirror captures into, and hands a finished one to
 * [SaveMirrorCaptureUseCase] for persisting (strategic S1924 6.1).
 *
 * Destination resolution and the save itself live in that use case rather than here: this class sits in
 * the UI layer, which reaches the data layer through a use case and never imports it directly (S2103).
 */
class MirrorCaptureManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val saveMirrorCapture: SaveMirrorCaptureUseCase,
    @ApplicationScope private val appScope: CoroutineScope,
) {

    /** Allocates the temp file a photo is captured into, or null when the app-private dir is unusable. */
    suspend fun newPhotoFile(): File? = createTemp(PHOTO_EXT)

    /** Allocates the temp file a recording is written into, or null when the dir is unusable. */
    suspend fun newVideoFile(): File? = createTemp(VIDEO_EXT)

    /**
     * Moves [tempFile] into the configured photo destination, or into DCIM/Camera when none resolves.
     *
     * Runs in the application scope, not the caller's: the save callback routinely lands after the
     * user closed the mirror, and a save tied to the activity was cancelled with it, leaving the shot
     * in app-private storage. The caller awaits the result only to report it.
     */
    fun savePhoto(tempFile: File): Deferred<Boolean> =
        appScope.async { saveMirrorCapture(tempFile, isVideo = false) }

    /** Moves [tempFile] into the configured video destination, detached from the screen as [savePhoto] is. */
    fun saveVideo(tempFile: File): Deferred<Boolean> =
        appScope.async { saveMirrorCapture(tempFile, isVideo = true) }

    /**
     * Suspend on purpose: `getExternalFilesDir` creates the directory on first use and `createNewFile`
     * writes an inode - both StrictMode disk writes, the way S1609 records for the capture screen.
     */
    private suspend fun createTemp(ext: String): File? = withContext(Dispatchers.IO) {
        val isVideo = ext == VIDEO_EXT
        val kind = if (isVideo) CaptureFileNamer.CaptureKind.VIDEO else CaptureFileNamer.CaptureKind.PHOTO
        val dirType = if (isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES
        runCatching {
            val dir = context.getExternalFilesDir(dirType) ?: context.filesDir
            File(dir, CaptureFileNamer.shared.allocate(kind, ext)).also { it.createNewFile() }
        }.onFailure { Timber.e(it, "MirrorCaptureManager: temp file creation failed") }.getOrNull()
    }

    private companion object {
        const val PHOTO_EXT = ".jpg"
        const val VIDEO_EXT = ".mp4"
    }
}
